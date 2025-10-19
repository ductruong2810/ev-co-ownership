package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OwnershipShareRepository extends JpaRepository<OwnershipShare, OwnershipShareId> {

    boolean existsByGroup_GroupIdAndUser_UserId(Long groupId, Long userId);

    long countByGroup_GroupId(Long groupId);

    List<OwnershipShare> findByGroup_GroupId(Long groupId);

    List<OwnershipShare> findByUser_UserId(Long userId);

    @Query("select coalesce(sum(s.ownershipPercentage), 0) " +
            "from OwnershipShare s where s.group.groupId = :groupId")
    BigDecimal sumPercentageByGroupId(Long groupId);
}