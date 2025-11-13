package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.ContractFeedback;
import com.group8.evcoownership.enums.FeedbackAdminAction;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractFeedbackRepository extends JpaRepository<ContractFeedback, Long> {
    
    List<ContractFeedback> findByContractId(Long contractId);

    Optional<ContractFeedback> findByContractIdAndUser_UserId(Long contractId, Long userId);
    
    boolean existsByContractIdAndUser_UserId(Long contractId, Long userId);

    long countByContractIdAndStatusAndReactionType(Long contractId, MemberFeedbackStatus status, ReactionType reactionType);

    long countByContractIdAndLastAdminAction(Long contractId, FeedbackAdminAction lastAdminAction);

    long countByContractId(Long contractId);

}

