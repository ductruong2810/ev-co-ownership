package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.FeedbackAdminAction;
import com.group8.evcoownership.enums.FeedbackHistoryAction;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ContractFeedbackHistoryItemDTO(
        Long historyId,
        Long feedbackId,
        Long userId,
        String userFullName,
        String userEmail,
        String userAvatarUrl,
        boolean isProcessed,
        MemberFeedbackStatus status,
        ReactionType reactionType,
        String reason,
        String adminNote,
        FeedbackAdminAction lastAdminAction,
        LocalDateTime lastAdminActionAt,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        FeedbackHistoryAction historyAction,
        String actionNote,
        LocalDateTime archivedAt
) {
}

