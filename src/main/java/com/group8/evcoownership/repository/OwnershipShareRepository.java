package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnershipShareRepository extends JpaRepository<OwnershipShare, Long> {

    /**
     * Lấy ownership percentage của user trong group
     */
    @Query("SELECT os.ownershipPercentage FROM OwnershipShare os " +
            "WHERE os.user.userId = :userId AND os.group.groupId = :groupId")
    Optional<BigDecimal> findOwnershipPercentageByUserAndGroup(@Param("userId") Long userId,
                                                               @Param("groupId") Long groupId);

    /**
     * Kiểm tra user có thuộc group không
     */
    @Query("SELECT COUNT(os) > 0 FROM OwnershipShare os " +
            "WHERE os.user.userId = :userId AND os.group.groupId = :groupId")
    boolean existsByUserAndGroup(@Param("userId") Long userId, @Param("groupId") Long groupId);

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
