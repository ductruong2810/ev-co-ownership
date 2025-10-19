package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
}
