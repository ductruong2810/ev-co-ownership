package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.enums.FundType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SharedFundRepository extends JpaRepository<SharedFund, Long> {
    Optional<SharedFund> findByGroup_GroupId(long group_GroupId);

    Page<SharedFund> findAllByGroup_GroupId(Long groupId, Pageable pageable);

    boolean existsByGroup_GroupId(long group_GroupId);

    // New for Fund Repository
    Optional<SharedFund> findByGroup_GroupIdAndFundType(Long groupId, FundType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SharedFund f where f.group.groupId=:gid and f.fundType=:type")
    Optional<SharedFund> lockByGroupAndType(@Param("gid") Long gid, @Param("type") FundType type);

    boolean existsByGroup_GroupIdAndFundType(Long groupId, FundType type);

}
