package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ContractApprovalStatus;
import lombok.Builder;

import java.util.List;

@Builder
public record ContractFeedbackHistoryResponseDTO(
        Long contractId,
        ContractApprovalStatus contractStatus,
        long totalMembersSubmitted,
        long totalFeedbacks,
        long acceptedCount,
        long pendingDisagreeCount,
        long approvedFeedbacksCount,
        long rejectedFeedbacksCount,
        int totalEntries,
        List<ContractFeedbackHistoryItemDTO> history
) {
}

