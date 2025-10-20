package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {
    Optional<Contract> findByGroupGroupId(Long groupId);

    Optional<Contract> findByGroup(OwnershipGroup group);
    
    Page<Contract> findByApprovalStatus(ContractApprovalStatus approvalStatus, Pageable pageable);
}
