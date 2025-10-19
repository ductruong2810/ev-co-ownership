package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.enums.GroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface OwnershipGroupRepository extends JpaRepository<OwnershipGroup, Long> {

    // đã có
    boolean existsByGroupNameIgnoreCase(String groupName);

    Page<OwnershipGroup> findByGroupNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<OwnershipGroup> findByStatus(GroupStatus status, Pageable pageable);

    Page<OwnershipGroup> findByGroupNameContainingIgnoreCaseAndStatus(String keyword, GroupStatus status, Pageable pageable);

    // ====== Theo thời gian ======
    Page<OwnershipGroup> findByCreatedAtBetween(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<OwnershipGroup> findByUpdatedAtBetween(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Kết hợp status/keyword + thời gian
    Page<OwnershipGroup> findByStatusAndCreatedAtBetween(
            GroupStatus status, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<OwnershipGroup> findByGroupNameContainingIgnoreCaseAndCreatedAtBetween(
            String keyword, LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<OwnershipGroup> findByGroupNameContainingIgnoreCaseAndStatusAndCreatedAtBetween(
            String keyword, GroupStatus status, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
