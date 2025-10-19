package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.OwnershipShareCreateRequest;
import com.group8.evcoownership.dto.OwnershipShareResponse;
import com.group8.evcoownership.dto.OwnershipShareUpdatePercentageRequest;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnershipShareService {

    private final OwnershipShareRepository shareRepo;
    private final OwnershipGroupRepository groupRepo;
    private final UserRepository userRepo;

    // -------- mapping --------
    private OwnershipShareResponse toDto(OwnershipShare s) {
        return new OwnershipShareResponse(
                s.getUser().getUserId(),
                s.getGroup().getGroupId(),
                s.getGroupRole(),
                s.getOwnershipPercentage(),
                s.getJoinDate(),
                s.getUpdatedAt()
        );
    }

    // ================== BUSINESS/APIs ==================

    /** Thêm thành viên + % sở hữu vào group (FE đã kiểm quyền). */
    @Transactional
    public OwnershipShareResponse addGroupShare(OwnershipShareCreateRequest req) {
        var user = userRepo.findById(req.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var group = groupRepo.findById(req.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // chỉ cho phép khi Pending
        if (group.getStatus() != GroupStatus.PENDING) {
            throw new IllegalStateException("Group is not PENDING");
        }

        // không trùng membership
        if (shareRepo.existsByGroup_GroupIdAndUser_UserId(group.getGroupId(), user.getUserId())) {
            throw new IllegalStateException("Membership already exists");
        }

        // đếm thành viên hiện có
        long currentMembers = shareRepo.countByGroup_GroupId(group.getGroupId());
        long capacity = group.getMemberCapacity() == null ? Long.MAX_VALUE : group.getMemberCapacity();
        if (currentMembers + 1 > capacity) throw new IllegalStateException("MemberCapacity exceeded");

// role tự động: người đầu tiên = ADMIN, còn lại = MEMBER
        var roleToSet = (currentMembers == 0)
                ? GroupRole.ADMIN   // hoặc OWNER nếu bạn muốn duy nhất chủ nhóm
                : GroupRole.MEMBER;

// tạo share
        var id = new OwnershipShareId(user.getUserId(), group.getGroupId());
        var share = OwnershipShare.builder()
                .id(id)
                .user(user)
                .group(group)
                .groupRole(roleToSet)
                .ownershipPercentage(req.ownershipPercentage())
                .joinDate(LocalDateTime.now())
                .build();

        var saved = shareRepo.save(share);
        tryActivate(group.getGroupId());
        return toDto(saved);

    }

    /** Cập nhật % sở hữu của 1 thành viên trong group (chỉ khi group Pending). */
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

        tryActivate(groupId);
        return toDto(saved);
    }

    /** Xoá 1 thành viên khỏi group (chỉ khi Pending). */
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
        tryActivate(groupId); // vẫn gọi để giữ logic thống nhất
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

    /** Nếu đủ điều kiện thì chuyển PENDING -> ACTIVE, ngược lại giữ nguyên. */
    @Transactional
    protected void tryActivate(Long groupId) {
        var group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        if (group.getStatus() != GroupStatus.PENDING) return;

        BigDecimal total = shareRepo.sumPercentageByGroupId(groupId); // có thể null -> đã coalesce 0 trong query
        long members = shareRepo.countByGroup_GroupId(groupId);

        boolean pctOk = total != null && total.compareTo(new BigDecimal("100.00")) == 0;
        boolean membersOk = members >= 1;
        boolean capacityOk = group.getMemberCapacity() == null || members <= group.getMemberCapacity();

        if (pctOk && membersOk && capacityOk) {
            group.setStatus(GroupStatus.ACTIVE);
            groupRepo.save(group);
        }
    }
}
