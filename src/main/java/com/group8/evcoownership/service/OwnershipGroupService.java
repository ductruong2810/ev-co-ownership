package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.OwnershipGroupCreateRequest;
import com.group8.evcoownership.dto.OwnershipGroupResponse;
import com.group8.evcoownership.dto.OwnershipGroupStatusUpdateRequest;
import com.group8.evcoownership.dto.OwnershipGroupUpdateRequest;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OwnershipGroupService {

    private final OwnershipGroupRepository repo;
    private final OwnershipShareRepository ownershipShareRepository;
    private final UserRepository userRepository;

    // ---- mapping ----
    private OwnershipGroupResponse toDto(OwnershipGroup e) {
        return new OwnershipGroupResponse(
                e.getGroupId(),
                e.getGroupName(),
                e.getDescription(),
                e.getMemberCapacity(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private void applyMutableFields(OwnershipGroup e, String name, String desc, Integer capacity) {
        e.setGroupName(name);
        e.setDescription(desc);
        e.setMemberCapacity(capacity);
    }

    // ---- CRUD ----
    @Transactional
    public OwnershipGroupResponse create(OwnershipGroupCreateRequest req) {
        if (repo.existsByGroupNameIgnoreCase(req.groupName())) {
            throw new IllegalStateException("GroupName already exists");
        }
        var entity = OwnershipGroup.builder()
                .groupName(req.groupName())
                .description(req.description())
                .memberCapacity(req.memberCapacity())
                .build(); // status = PENDING at @PrePersist
        return toDto(repo.save(entity));
    }

    @Transactional
    public OwnershipGroupResponse updateByUser(Long groupId, OwnershipGroupUpdateRequest req) {
        var e = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        if (e.getStatus() != GroupStatus.PENDING) {
            throw new AccessDeniedException("Only PENDING group can be updated by user");
        }
        if (!e.getGroupName().equalsIgnoreCase(req.groupName())
                && repo.existsByGroupNameIgnoreCase(req.groupName())) {
            throw new IllegalStateException("GroupName already exists");
        }

        applyMutableFields(e, req.groupName(), req.description(), req.memberCapacity());
        return toDto(repo.save(e));
    }

    @Transactional
    public OwnershipGroupResponse updateStatus(Long groupId, OwnershipGroupStatusUpdateRequest req) {
        var e = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        var target = req.status();
        if (target == null) throw new IllegalArgumentException("Status is required");

        // Rule: cho phép ACTIVE -> INACTIVE; KHÔNG cho ACTIVE -> PENDING
        if (e.getStatus() == GroupStatus.ACTIVE && target == GroupStatus.PENDING) {
            throw new IllegalArgumentException("Cannot revert ACTIVE -> PENDING");
        }
        if (e.getStatus() == target) return toDto(e);

        e.setStatus(target);
        return toDto(repo.save(e));
    }

    public OwnershipGroupResponse getById(Long groupId) {
        return repo.findById(groupId)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
    }

    /**
     * list: dùng các hàm derived query đã có trong Repository (không dùng Specification)
     * - keyword: tìm theo GroupName (contains, ignore case)
     * - status: PENDING/ACTIVE/INACTIVE
     * - fromDate/toDate: lọc theo CreatedAt (inclusive), dùng BETWEEN các method đã khai báo
     */
    public Page<OwnershipGroupResponse> list(String keyword,
                                             GroupStatus status,
                                             LocalDate fromDate,
                                             LocalDate toDate,
                                             Pageable pageable) {

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = status != null;
        boolean hasDate = (fromDate != null || toDate != null);

        // Chuẩn hóa mốc thời gian (bao phủ trọn ngày)
        LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : LocalDateTime.MIN;
        LocalDateTime end = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().minusNanos(1) // inclusive style
                : LocalDateTime.MAX;

        Page<OwnershipGroup> page;

        if (hasDate && hasKeyword && hasStatus) {
            page = repo.findByGroupNameContainingIgnoreCaseAndStatusAndCreatedAtBetween(
                    keyword, status, start, end, pageable);
        } else if (hasDate && hasKeyword) {
            page = repo.findByGroupNameContainingIgnoreCaseAndCreatedAtBetween(
                    keyword, start, end, pageable);
        } else if (hasDate && hasStatus) {
            page = repo.findByStatusAndCreatedAtBetween(
                    status, start, end, pageable);
        } else if (hasDate) {
            page = repo.findByCreatedAtBetween(start, end, pageable);
        } else if (hasKeyword && hasStatus) {
            page = repo.findByGroupNameContainingIgnoreCaseAndStatus(keyword, status, pageable);
        } else if (hasKeyword) {
            page = repo.findByGroupNameContainingIgnoreCase(keyword, pageable);
        } else if (hasStatus) {
            page = repo.findByStatus(status, pageable);
        } else {
            page = repo.findAll(pageable);
        }

        return page.map(this::toDto);
    }

    @Transactional
    public void delete(Long groupId) {
        var e = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        if (e.getStatus() == GroupStatus.ACTIVE) {
            throw new IllegalStateException("Cannot delete ACTIVE group");
        }
        repo.delete(e);
    }

    // ---- Authorization methods ----

    /**
     * Kiểm tra user có phải là admin của group không
     */
    public boolean isGroupAdmin(String userEmail, Long groupId) {
        try {
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

            var ownershipShare = ownershipShareRepository.findById_UserIdAndGroup_GroupId(user.getUserId(), groupId)
                    .orElse(null);

            return ownershipShare != null && ownershipShare.getGroupRole() == GroupRole.ADMIN;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Kiểm tra user có phải là member của group không
     */
    public boolean isGroupMember(String userEmail, Long groupId) {
        try {
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

            var ownershipShare = ownershipShareRepository.findById_UserIdAndGroup_GroupId(user.getUserId(), groupId)
                    .orElse(null);

            return ownershipShare != null;
        } catch (Exception e) {
            return false;
        }
    }
}
