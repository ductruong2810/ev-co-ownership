package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OwnershipGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OwnershipGroupRepository extends JpaRepository<OwnershipGroup, Long> {

    boolean existsByGroupName(String groupName);
}
