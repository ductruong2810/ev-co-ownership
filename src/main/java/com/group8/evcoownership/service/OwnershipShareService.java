package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.OwnershipShareCreateRequest;
import com.group8.evcoownership.dto.OwnershipShareResponse;
import com.group8.evcoownership.dto.OwnershipShareUpdateDepositStatusRequest;
import com.group8.evcoownership.dto.OwnershipShareUpdatePercentageRequest;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnershipShareService {

    private final OwnershipShareRepository shareRepo;
    private final OwnershipGroupRepository groupRepo;
    private final UserRepository userRepo;

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

        if (group.getStatus() != GroupStatus.PENDING) {
            throw new IllegalStateException("Group is not PENDING");
        }

        if (shareRepo.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId())) {
            throw new IllegalStateException("Membership already exists");
        }

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
//        tryActivate(group.getGroupId());
        return toDto(saved);
    }

    /**
     * Cập nhật % sở hữu (chỉ khi group PENDING)
     */
    @Transactional
    public OwnershipShareResponse updatePercentage(Long groupId, Long userId,
                                                   OwnershipShareUpdatePercentageRequest req) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        if (group.getStatus() != GroupStatus.PENDING) {
            throw new IllegalStateException("Group is not PENDING");
        }

        var id = new OwnershipShareId(userId, groupId);
        var share = shareRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));

        share.setOwnershipPercentage(req.ownershipPercentage());
        var saved = shareRepo.save(share);

//        tryActivate(groupId);
        return toDto(saved);
    }

    /**
     * Cập nhật trạng thái đặt cọc (tuỳ nhu cầu, không phụ thuộc PENDING)
     */
    @Transactional
    public OwnershipShareResponse updateDepositStatus(Long groupId, Long userId,
                                                      OwnershipShareUpdateDepositStatusRequest req) {
        var id = new OwnershipShareId(userId, groupId);
        var share = shareRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));

        share.setDepositStatus(req.newStatus());
        var saved = shareRepo.save(share);

        // Nếu bạn muốn yêu cầu tất cả PAID mới Active, hãy bật điều kiện trong tryActivate và gọi ở đây:
        // tryActivate(groupId);

        return toDto(saved);
    }

    /**
     * Xoá 1 thành viên (chỉ khi PENDING)
     */
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        var id = new OwnershipShareId(userId, groupId);
        var share = shareRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));

        var group = share.getGroup();
        if (group.getStatus() != GroupStatus.PENDING) {
            throw new IllegalStateException("Cannot remove member when group is not PENDING");
        }

        shareRepo.delete(share);
//        tryActivate(groupId);
    }

    public OwnershipShareResponse getOne(Long groupId, Long userId) {
        var id = new OwnershipShareId(userId, groupId);
        return shareRepo.findById(id).map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));
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

    /**
     * Đủ điều kiện thì chuyển ACTIVE -> VERIFIED (không phụ thuộc role/cọc, trừ khi bạn muốn bật).
     */
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
}
