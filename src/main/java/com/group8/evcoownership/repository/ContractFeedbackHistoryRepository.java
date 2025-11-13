package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.ContractFeedbackHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractFeedbackHistoryRepository extends JpaRepository<ContractFeedbackHistory, Long> {
    List<ContractFeedbackHistory> findByContractIdOrderByArchivedAtDesc(Long contractId);
}

