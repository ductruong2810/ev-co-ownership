package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query("""
                SELECT c FROM Contract c
                ORDER BY
                    CASE
                        WHEN c.approvalStatus = 'PENDING' THEN 0
                        WHEN c.approvalStatus = 'SIGNED' THEN 1
                        WHEN c.approvalStatus = 'APPROVED' THEN 2
                        WHEN c.approvalStatus = 'REJECTED' THEN 3
                        ELSE 4
                    END
            """)
    List<Contract> findAllSortedByStatus();


    Optional<Contract> findByGroupGroupId(Long groupId);

    Optional<Contract> findByGroup(OwnershipGroup group);

    List<Contract> findByApprovalStatus(ContractApprovalStatus status);


}
