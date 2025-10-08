package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.SharedFund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
 @Repository
public interface SharedFundRepository extends JpaRepository<SharedFund,Long> {
    Optional<SharedFund> findByGroup_GroupId(long group_GroupId);
    boolean existsByGroup_GroupId(long group_GroupId);
}
