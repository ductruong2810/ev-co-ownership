package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.SharedFund;

import java.util.Optional;

public interface SharedFundRepository {
    Optional<SharedFund> findByGroup_GroupId(long group_GroupId);
    boolean existsByGroup_GroupId(long group_GroupId);
}
