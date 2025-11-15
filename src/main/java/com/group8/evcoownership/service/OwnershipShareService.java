package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.enums.*;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OwnershipShareService {

    private final OwnershipShareRepository shareRepo;
    private final OwnershipGroupRepository groupRepo;
    private final UserRepository userRepo;
    private final VehicleRepository vehicleRepository;
    private final ContractRepository contractRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final UserDocumentValidationService userDocumentValidationService;

    // ------- mapping -------
    private OwnershipShareResponseDTO toDto(OwnershipShare s) {
        return new OwnershipShareResponseDTO(
                s.getUser().getUserId(),
                s.getGroup().getGroupId(),
                s.getGroupRole(),
                s.getOwnershipPercentage(),
                s.getDepositStatus(),
                s.getJoinDate(),
                s.getUpdatedAt()
        );
    }

    // ================== BUSINESS/APIs ==================

    /**
     * Thêm thành viên + % sở hữu (FE kiểm quyền). Người đầu tiên = ADMIN, còn lại = MEMBER.
     */
    @Transactional
    public OwnershipShareResponseDTO addGroupShare(OwnershipShareCreateRequestDTO req) {
        var user = userRepo.findById(req.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var group = groupRepo.findById(req.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalStateException("Group is not ACTIVE");
        }

        if (shareRepo.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId())) {
            throw new IllegalStateException("Membership already exists");
        }

        // Kiểm tra user có đầy đủ giấy tờ đã được duyệt không
        userDocumentValidationService.validateUserDocuments(user.getUserId());

        long currentMembers = shareRepo.countByGroup_GroupId(group.getGroupId());
        long capacity = group.getMemberCapacity() == null ? Long.MAX_VALUE : group.getMemberCapacity();
        if (currentMembers + 1 > capacity) {
            throw new IllegalStateException("MemberCapacity exceeded");
        }

        var roleToSet = (currentMembers == 0) ? GroupRole.ADMIN : GroupRole.MEMBER;

        var id = new OwnershipShareId(user.getUserId(), group.getGroupId());
        var share = OwnershipShare.builder()
                .id(id)
                .user(user)
                .group(group)
                .groupRole(roleToSet)
                .ownershipPercentage(req.ownershipPercentage())
                .depositStatus(DepositStatus.PENDING)
                .joinDate(LocalDateTime.now())
                .build();

        var saved = shareRepo.save(share);
        // Send notification to group members about new member
        notificationOrchestrator.sendGroupNotification(
                group.getGroupId(),
                NotificationType.GROUP_MEMBER_JOINED,
                "New Member Joined",
                String.format("%s has joined the group with %.2f%% ownership",
                        user.getFullName(), req.ownershipPercentage())
        );

//        tryActivate(group.getGroupId());
        return toDto(saved);
    }

    /**
     * Xoá 1 thành viên (chỉ khi ACTIVE)
     */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        var id = new OwnershipShareId(userId, groupId);
        var share = shareRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));

        var group = share.getGroup();
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalStateException("Cannot remove member when group is not ACTIVE");
        }

        ensureContractAllowsRemoval(groupId);

        shareRepo.delete(share);
//        tryActivate(groupId);
    }

    private void ensureContractAllowsRemoval(Long groupId) {
        contractRepository.findByGroupGroupId(groupId).ifPresent(contract -> {
            var status = contract.getApprovalStatus();
            boolean contractLocked = status == ContractApprovalStatus.SIGNED
                    || status == ContractApprovalStatus.APPROVED
                    || Boolean.TRUE.equals(contract.getIsActive());
            if (contractLocked) {
                throw new IllegalStateException("Cannot remove member while the group's contract is signed or active. Please cancel the contract first.");
            }
        });
    }

    public List<OwnershipShareResponseDTO> listByGroup(Long groupId) {
        if (!groupRepo.existsById(groupId)) throw new EntityNotFoundException("Group not found");
        return shareRepo.findByGroup_GroupId(groupId).stream().map(this::toDto).toList();
    }

    public List<OwnershipShareResponseDTO> listByUser(Long userId) {
        if (!userRepo.existsById(userId)) throw new EntityNotFoundException("User not found");
        return shareRepo.findByUser_UserId(userId).stream().map(this::toDto).toList();
    }

    // ================== AUTO ACTIVATE ==================


//    @Transactional
//    protected void tryActivate(Long groupId) {
//        var group = groupRepo.findById(groupId)
//                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
//
//        if (group.getStatus() != GroupStatus.ACTIVE) return;
//
//        BigDecimal total = shareRepo.sumPercentageByGroupId(groupId);
//        long members = shareRepo.countByGroup_GroupId(groupId);
//
//        boolean pctOk = total != null && total.compareTo(new BigDecimal("100.00")) == 0;
//        boolean membersOk = members >= 1;
//        boolean capacityOk = group.getMemberCapacity() == null || members <= group.getMemberCapacity();
//
//
//        // boolean depositsOk = shareRepo.countByGroup_GroupIdAndDepositStatusNot(groupId, DepositStatus.PAID) == 0;
//
//        if (pctOk && membersOk && capacityOk /* && depositsOk */) {
//            group.setStatus(GroupStatus.VERIFIED);// ready
//            groupRepo.save(group);
//        }
//    }

    // ================== OWNERSHIP PERCENTAGE MANAGEMENT ==================

    /**
     * Lấy thông tin tỷ lệ sở hữu của user trong group (cho trang nhập tỷ lệ)
     */
    @Transactional
    public OwnershipPercentageResponseDTO getOwnershipPercentage(Long userId, Long groupId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        var share = shareRepo.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        var vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found for this group"));

        // Tính tổng tỷ lệ đã phân bổ
        BigDecimal totalAllocated = calculateTotalAllocatedPercentage(groupId);

        // Kiểm tra có thể edit không (chỉ khi group ACTIVE và user chưa confirm)
        boolean canEdit = group.getStatus() == GroupStatus.ACTIVE &&
                share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0;

        // Tính số tiền đầu tư
        BigDecimal investmentAmount = calculateInvestmentAmount(vehicle.getVehicleValue(), share.getOwnershipPercentage());

        // Determine ownership percentage status of user
        String ownershipStatus;
        if (share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0) {
            ownershipStatus = "PENDING"; // No ownership percentage entered
        } else if (group.getStatus() == GroupStatus.ACTIVE && canEdit) {
            ownershipStatus = "CONFIRMED"; // Ownership percentage entered, can be edited
        } else {
            ownershipStatus = "LOCKED"; // Locked, cannot be edited
        }

        return OwnershipPercentageResponseDTO.builder()
                .userId(userId)
                .groupId(groupId)
                .userName(user.getFullName())
                .ownershipPercentage(share.getOwnershipPercentage())
                .investmentAmount(investmentAmount)
                .vehicleValue(vehicle.getVehicleValue())
                .totalAllocatedPercentage(totalAllocated)
                .canEdit(canEdit)
                .status(ownershipStatus)
                .updatedAt(share.getUpdatedAt())
                .message(canEdit ? "You can enter ownership percentage" : "Ownership percentage has been confirmed")
                .build();
    }

    /**
     * Cập nhật tỷ lệ sở hữu của user (cho trang nhập tỷ lệ)
     */
    @Transactional
    public OwnershipPercentageResponseDTO updateOwnershipPercentage(Long userId, Long groupId,
                                                                    OwnershipPercentageRequestDTO request) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        // Kiểm tra group có ở trạng thái ACTIVE không
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalStateException("Cannot update ownership percentage when group is not ACTIVE");
        }

        var share = shareRepo.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        // Kiểm tra contract đã ký chưa - nếu đã ký thì không cho sửa tỷ lệ
        var contract = contractRepository.findByGroupGroupId(groupId).orElse(null);
        if (contract != null && contract.getApprovalStatus() == com.group8.evcoownership.enums.ContractApprovalStatus.SIGNED) {
            throw new IllegalStateException("Không thể sửa tỷ lệ sở hữu sau khi hợp đồng đã được ký");
        }

        // Kiểm tra tỷ lệ mới có hợp lệ không
        validateOwnershipPercentage(groupId, userId, request.getOwnershipPercentage());

        // Cập nhật tỷ lệ sở hữu
        share.setOwnershipPercentage(request.getOwnershipPercentage());
        share.setUpdatedAt(LocalDateTime.now());
        shareRepo.save(share);

        // Tính lại tổng tỷ lệ
        BigDecimal totalAllocated = calculateTotalAllocatedPercentage(groupId);

        var vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        BigDecimal investmentAmount = calculateInvestmentAmount(vehicle.getVehicleValue(), share.getOwnershipPercentage());

        // Xác định trạng thái ownership percentage sau khi cập nhật
        String ownershipStatus;
        boolean canEditAfterUpdate = totalAllocated.compareTo(new BigDecimal("100")) < 0;
        if (share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0) {
            ownershipStatus = "PENDING"; // Chưa nhập tỷ lệ
        } else if (group.getStatus() == GroupStatus.ACTIVE && canEditAfterUpdate) {
            ownershipStatus = "CONFIRMED"; // Đã nhập tỷ lệ, có thể chỉnh sửa
        } else {
            ownershipStatus = "LOCKED"; // Đã khóa, không thể chỉnh sửa
        }

        return OwnershipPercentageResponseDTO.builder()
                .userId(userId)
                .groupId(groupId)
                .userName(user.getFullName())
                .ownershipPercentage(share.getOwnershipPercentage())
                .investmentAmount(investmentAmount)
                .vehicleValue(vehicle.getVehicleValue())
                .totalAllocatedPercentage(totalAllocated)
                .canEdit(canEditAfterUpdate)
                .status(ownershipStatus)
                .updatedAt(share.getUpdatedAt())
                .message("Ownership percentage has been updated successfully")
                .build();
    }

    /**
     * Lấy tổng quan tỷ lệ sở hữu của group
     */
    @Transactional
    public GroupOwnershipSummaryResponseDTO getGroupOwnershipSummary(Long groupId, Long currentUserId) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        var vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        var shares = shareRepo.findByGroup_GroupId(groupId);

        BigDecimal totalAllocated = calculateTotalAllocatedPercentage(groupId);
        BigDecimal remainingPercentage = new BigDecimal("100").subtract(totalAllocated);
        boolean isFullyAllocated = totalAllocated.compareTo(new BigDecimal("100")) == 0;

        // Lấy vai trò của user hiện tại trong nhóm
        var currentUserShare = shares.stream()
                .filter(s -> s.getUser().getUserId().equals(currentUserId))
                .findFirst()
                .orElse(null);

        String currentUserRole = currentUserShare != null
                ? currentUserShare.getGroupRole().name()
                : "UNKNOWN";

        // Danh sach thanh vien
        var members = shares.stream()
                .map(share -> {
                    BigDecimal investmentAmount = calculateInvestmentAmount(vehicle.getVehicleValue(), share.getOwnershipPercentage());
                    boolean isCurrentUser = share.getUser().getUserId().equals(currentUserId);

                    // Xác định trạng thái ownership percentage của từng member
                    String memberStatus;
                    if (share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0) {
                        memberStatus = "PENDING"; // Chưa nhập tỷ lệ
                    } else if (group.getStatus() == GroupStatus.ACTIVE && !isFullyAllocated) {
                        memberStatus = "CONFIRMED"; // Đã nhập tỷ lệ, có thể chỉnh sửa
                    } else {
                        memberStatus = "LOCKED"; // Đã khóa, không thể chỉnh sửa
                    }

                    return GroupOwnershipSummaryResponseDTO.MemberOwnershipInfo.builder()
                            .userId(share.getUser().getUserId())
                            .userName(share.getUser().getFullName())
                            .userEmail(share.getUser().getEmail())
                            .ownershipPercentage(share.getOwnershipPercentage())
                            .investmentAmount(investmentAmount)
                            .status(memberStatus)
                            .isCurrentUser(isCurrentUser)
                            .groupRole(share.getGroupRole().name())
                            .build();
                })
                .toList();

        return GroupOwnershipSummaryResponseDTO.builder()
                .groupId(groupId)
                .groupName(group.getGroupName())
                .vehicleValue(vehicle.getVehicleValue())
                .totalMembers(shares.size())
                .memberCapacity(group.getMemberCapacity())
                .totalAllocatedPercentage(totalAllocated)
                .isFullyAllocated(isFullyAllocated)
                .remainingPercentage(remainingPercentage)
                .currentUserRole(currentUserRole)
                .members(members)
                .build();
    }

    /**
     * Reset tỷ lệ sở hữu của user về 0%
     */
    @Transactional
    public OwnershipPercentageResponseDTO resetOwnershipPercentage(Long userId, Long groupId) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalStateException("Cannot reset ownership percentage when group is not ACTIVE");
        }

        var share = shareRepo.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        share.setOwnershipPercentage(BigDecimal.ZERO);
        share.setUpdatedAt(LocalDateTime.now());
        shareRepo.save(share);

        BigDecimal totalAllocated = calculateTotalAllocatedPercentage(groupId);

        var vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        // Sau khi reset về 0%, status sẽ là PENDING
        String ownershipStatus = "PENDING"; // Đã reset về 0%, chưa nhập tỷ lệ

        return OwnershipPercentageResponseDTO.builder()
                .userId(userId)
                .groupId(groupId)
                .userName(share.getUser().getFullName())
                .ownershipPercentage(BigDecimal.ZERO)
                .investmentAmount(BigDecimal.ZERO)
                .vehicleValue(vehicle.getVehicleValue())
                .totalAllocatedPercentage(totalAllocated)
                .canEdit(true)
                .status(ownershipStatus)
                .updatedAt(share.getUpdatedAt())
                .message("Ownership percentage has been reset to 0%")
                .build();
    }

    /**
     * Lấy gợi ý tỷ lệ sở hữu dựa trên phần còn lại
     */
    @Transactional
    public List<BigDecimal> getOwnershipSuggestions(Long groupId) {
        BigDecimal totalAllocated = calculateTotalAllocatedPercentage(groupId);
        BigDecimal remaining = new BigDecimal("100").subtract(totalAllocated);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        return List.of(
                remaining.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP), // 25% của phần còn lại
                remaining.divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP), // 33% của phần còn lại
                remaining.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP), // 50% của phần còn lại
                remaining // 100% của phần còn lại
        );
    }

    // ================== HELPER METHODS ==================

    /**
     * Tính tổng tỷ lệ đã phân bổ trong group
     */
    private BigDecimal calculateTotalAllocatedPercentage(Long groupId) {
        return shareRepo.findByGroup_GroupId(groupId)
                .stream()
                .map(OwnershipShare::getOwnershipPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Tính số tiền đầu tư dựa trên tỷ lệ sở hữu
     */
    private BigDecimal calculateInvestmentAmount(BigDecimal vehicleValue, BigDecimal ownershipPercentage) {
        return vehicleValue.multiply(ownershipPercentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * Validate tỷ lệ sở hữu
     * Business rules:
     * 1. Tỷ lệ sở hữu phải lớn hơn 0%
     * 2. Tỷ lệ sở hữu không được vượt quá 100%
     * 3. Tỷ lệ sở hữu tối thiểu là 1% để đảm bảo tính co-ownership
     * 4. Tổng tỷ lệ sở hữu không được vượt quá 100%
     */
    private void validateOwnershipPercentage(Long groupId, Long userId, BigDecimal newPercentage) {
        if (newPercentage == null) {
            throw new IllegalArgumentException("Ownership percentage cannot be null");
        }

        if (newPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ownership percentage must be greater than 0%");
        }

        BigDecimal minimumOwnership = new BigDecimal("1.00");
        if (newPercentage.compareTo(minimumOwnership) < 0) {
            throw new IllegalArgumentException("Ownership percentage must be at least 1% to ensure co-ownership");
        }

        BigDecimal hundred = new BigDecimal("100");

        if (newPercentage.compareTo(hundred) > 0) {
            throw new IllegalArgumentException("Ownership percentage cannot exceed 100%");
        }

        // Tính tổng tỷ lệ hiện tại (trừ user này)
        var shares = shareRepo.findByGroup_GroupId(groupId);

        BigDecimal currentTotal = shares.stream()
                .filter(share -> !share.getUser().getUserId().equals(userId))
                .map(OwnershipShare::getOwnershipPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = currentTotal.add(newPercentage);

        if (newTotal.compareTo(hundred) > 0) {
            throw new IllegalArgumentException("Total ownership percentage cannot exceed 100%. Current total: " +
                    currentTotal + "%, New percentage: " + newPercentage + "%");
        }

        long remainingMembersWithoutPercentage = shares.stream()
                .filter(share -> !share.getUser().getUserId().equals(userId))
                .filter(share -> share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0)
                .count();

        if (newTotal.compareTo(hundred) < 0 && remainingMembersWithoutPercentage == 0) {
            throw new IllegalArgumentException("Total ownership percentage must equal 100% once all other members have set their percentages. Current total: " +
                    currentTotal + "%, Requested percentage: " + newPercentage + "% would result in " + newTotal + "%");
        }
    }

    // Removed validateUserDocuments method - moved to UserDocumentValidationService

    /**
     * Lấy thông tin xe của group (bao gồm biển số)
     */
    public VehicleResponseDTO getVehicleInfo(Long groupId) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        var vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found for group: " + groupId));

        return new VehicleResponseDTO(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getLicensePlate(), // Biển số xe
                vehicle.getChassisNumber(),
                vehicle.getOwnershipGroup().getGroupId(),
                vehicle.getVehicleValue(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }

    @Transactional
    public void validateOwnershipOnly(Long userId, Long groupId, OwnershipPercentageRequestDTO request) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalStateException("Group is not ACTIVE");
        }
        if (isOwnershipLockedByContract(groupId)) {
            throw new IllegalStateException("Contract is pending/signed/active; cannot change ownership percentage");
        }
        shareRepo.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));
        validateOwnershipPercentage(groupId, userId, request.getOwnershipPercentage());
    }


    private boolean isOwnershipLockedByContract(Long groupId) {
        var c = contractRepository.findByGroupGroupId(groupId).orElse(null);
        if (c == null) return false;
        var st = c.getApprovalStatus();
        boolean lockedByStatus =
                st == ContractApprovalStatus.PENDING
                        || st == ContractApprovalStatus.PENDING_MEMBER_APPROVAL
                        || st == ContractApprovalStatus.SIGNED
                        || st == ContractApprovalStatus.APPROVED;
        return lockedByStatus || Boolean.TRUE.equals(c.getIsActive());
    }

}
