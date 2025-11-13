package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.ContractFeedbackHistory;
import com.group8.evcoownership.enums.FeedbackHistoryAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContractFeedbackHistoryRepository extends JpaRepository<ContractFeedbackHistory, Long> {
    List<ContractFeedbackHistory> findByContractIdOrderByArchivedAtDesc(Long contractId);
    
    @Query("SELECT COUNT(DISTINCT h.feedback.id) FROM ContractFeedbackHistory h WHERE h.contract.id = :contractId")
    long countDistinctFeedbacksByContractId(@Param("contractId") Long contractId);
    
    @Query("SELECT COUNT(DISTINCT h.user.userId) FROM ContractFeedbackHistory h WHERE h.contract.id = :contractId")
    long countDistinctUsersByContractId(@Param("contractId") Long contractId);
    
    long countByContractIdAndHistoryAction(Long contractId, FeedbackHistoryAction historyAction);
    
    // Tìm history entry cuối cùng của một feedback
    Optional<ContractFeedbackHistory> findFirstByFeedbackIdOrderByArchivedAtDesc(Long feedbackId);
}

