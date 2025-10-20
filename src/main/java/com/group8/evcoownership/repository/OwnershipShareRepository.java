package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.enums.GroupRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnershipShareRepository extends JpaRepository<OwnershipShare, OwnershipShareId> {

    boolean existsByGroup_GroupIdAndUser_UserId(Long groupId, Long userId);

    long countByGroup_GroupId(Long groupId);

    List<OwnershipShare> findByGroup_GroupId(Long groupId);

    List<OwnershipShare> findByUser_UserId(Long userId);

    @Query("select coalesce(sum(s.ownershipPercentage), 0) " +
            "from OwnershipShare s where s.group.groupId = :groupId")
    BigDecimal sumPercentageByGroupId(Long groupId);

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

    // === check quyền ADMIN trong group ===
    boolean existsByGroup_GroupIdAndUser_UserIdAndGroupRole(
            Long groupId, Long userId, GroupRole groupRole
    );}