package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.ContractFeedback;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractFeedbackRepository extends JpaRepository<ContractFeedback, Long> {
    
    List<ContractFeedback> findByContractId(Long contractId);
    
    List<ContractFeedback> findByContractIdAndStatus(Long contractId, MemberFeedbackStatus status);
    
    Optional<ContractFeedback> findByContractIdAndUser_UserId(Long contractId, Long userId);
    
    boolean existsByContractIdAndUser_UserId(Long contractId, Long userId);
    
    long countByContractId(Long contractId);
    
    long countByContractIdAndStatus(Long contractId, MemberFeedbackStatus status);
    
    long countByContractIdAndReactionType(Long contractId, ReactionType reactionType);
    
    long countByContractIdAndStatusAndReactionType(Long contractId, MemberFeedbackStatus status, ReactionType reactionType);
    
    List<ContractFeedback> findByContractIdAndIsProcessed(Long contractId, Boolean isProcessed);
    
    long countByContractIdAndIsProcessed(Long contractId, Boolean isProcessed);
}

