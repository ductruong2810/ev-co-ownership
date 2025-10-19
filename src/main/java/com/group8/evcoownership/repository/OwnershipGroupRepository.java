package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.enums.GroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    /**
     * Tìm OwnershipShare theo userId và groupId
     */
    @Query("SELECT os FROM OwnershipShare os " +
            "WHERE os.user.userId = :userId AND os.group.groupId = :groupId")
    Optional<OwnershipShare> findById_UserIdAndGroup_GroupId(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /**
     * Lấy tất cả OwnershipShare theo groupId
     */
    @Query("SELECT os FROM OwnershipShare os WHERE os.group.groupId = :groupId")
    List<OwnershipShare> findByGroupGroupId(@Param("groupId") Long groupId);
}
