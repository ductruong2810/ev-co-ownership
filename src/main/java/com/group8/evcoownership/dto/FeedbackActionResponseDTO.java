package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.FeedbackAdminAction;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackActionResponseDTO {
    private Long feedbackId;
    private MemberFeedbackStatus status;
    private Boolean isProcessed;
    private FeedbackAdminAction lastAdminAction;
    private LocalDateTime lastAdminActionAt;
    private ReactionType reactionType;
    private Long userId;
    private Long contractId;
    private String reason;
    private String adminNote; // Chỉ có khi reject
}

