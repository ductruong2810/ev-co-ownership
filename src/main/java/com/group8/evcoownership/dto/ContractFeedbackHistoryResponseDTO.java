package com.group8.evcoownership.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ContractFeedbackHistoryResponseDTO(
        Long contractId,
        long totalFeedbacks,
        long acceptedCount,
        long pendingDisagreeCount,
        long approvedFeedbacksCount,
        long rejectedFeedbacksCount,
        int totalEntries,
        List<ContractFeedbackHistoryItemDTO> history
) {
}

