package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnershipShareService {

    private final OwnershipShareRepository shareRepo;
    private final OwnershipGroupRepository groupRepo;
    private final UserRepository userRepo;
    private final VehicleRepository vehicleRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final UserDocumentValidationService userDocumentValidationService;

    // ------- mapping -------
    private OwnershipShareResponse toDto(OwnershipShare s) {
        return new OwnershipShareResponse(
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
    public OwnershipShareResponse addGroupShare(OwnershipShareCreateRequest req) {
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

        shareRepo.delete(share);
//        tryActivate(groupId);
    }

    public List<OwnershipShareResponse> listByGroup(Long groupId) {
        if (!groupRepo.existsById(groupId)) throw new EntityNotFoundException("Group not found");
        return shareRepo.findByGroup_GroupId(groupId).stream().map(this::toDto).toList();
    }

    public List<OwnershipShareResponse> listByUser(Long userId) {
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
    public OwnershipPercentageResponse getOwnershipPercentage(Long userId, Long groupId) {
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
        
        // Xác định trạng thái ownership percentage của user
        String ownershipStatus;
        if (share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0) {
            ownershipStatus = "PENDING"; // Chưa nhập tỷ lệ
        } else if (group.getStatus() == GroupStatus.ACTIVE && canEdit) {
            ownershipStatus = "CONFIRMED"; // Đã nhập tỷ lệ, có thể chỉnh sửa
        } else {
            ownershipStatus = "LOCKED"; // Đã khóa, không thể chỉnh sửa
        }
        
        return OwnershipPercentageResponse.builder()
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
                .message(canEdit ? "Bạn có thể nhập tỷ lệ sở hữu" : "Tỷ lệ sở hữu đã được xác nhận")
                .build();
    }

    /**
     * Cập nhật tỷ lệ sở hữu của user (cho trang nhập tỷ lệ)
     */
    @Transactional
    public OwnershipPercentageResponse updateOwnershipPercentage(Long userId, Long groupId, 
                                                               OwnershipPercentageRequest request) {
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
        
        return OwnershipPercentageResponse.builder()
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
                .message("Tỷ lệ sở hữu đã được cập nhật thành công")
                .build();
    }

    /**
     * Lấy tổng quan tỷ lệ sở hữu của group
     */
    @Transactional
    public GroupOwnershipSummaryResponse getGroupOwnershipSummary(Long groupId, Long currentUserId) {
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
                    
                    return GroupOwnershipSummaryResponse.MemberOwnershipInfo.builder()
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
        
        return GroupOwnershipSummaryResponse.builder()
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
    public OwnershipPercentageResponse resetOwnershipPercentage(Long userId, Long groupId) {
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
        
        return OwnershipPercentageResponse.builder()
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
                .message("Tỷ lệ sở hữu đã được đặt lại về 0%")
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
     */
    private void validateOwnershipPercentage(Long groupId, Long userId, BigDecimal newPercentage) {
        if (newPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ownership percentage must be greater than 0%");
        }
        
        if (newPercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Ownership percentage cannot exceed 100%");
        }
        
        // Tính tổng tỷ lệ hiện tại (trừ user này)
        BigDecimal currentTotal = shareRepo.findByGroup_GroupId(groupId)
                .stream()
                .filter(share -> !share.getUser().getUserId().equals(userId))
                .map(OwnershipShare::getOwnershipPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal newTotal = currentTotal.add(newPercentage);
        
        if (newTotal.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Total ownership percentage cannot exceed 100%. Current total: " + 
                    currentTotal + "%, New percentage: " + newPercentage + "%");
        }
    }

    // Removed validateUserDocuments method - moved to UserDocumentValidationService

    /**
     * Lấy thông tin xe của group (bao gồm biển số)
     */
    public VehicleResponse getVehicleInfo(Long groupId) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
        
        var vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found for group: " + groupId));
        
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getLicensePlate(), // Biển số xe
                vehicle.getChassisNumber(),
                vehicle.getQrCode(),
                vehicle.getOwnershipGroup().getGroupId(),
                vehicle.getVehicleValue(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
