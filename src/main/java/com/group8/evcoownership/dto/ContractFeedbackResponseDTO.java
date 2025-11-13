package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.enums.ReactionType;
import com.group8.evcoownership.enums.FeedbackAdminAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractFeedbackResponseDTO {
    private Long feedbackId;
    private Long userId;
    private String fullName;
    private String email;
    private MemberFeedbackStatus status;
    private Boolean isProcessed;
    private FeedbackAdminAction lastAdminAction;
    private LocalDateTime lastAdminActionAt;
    private ReactionType reactionType;
    private String reason;
    private String adminNote;
    private LocalDateTime submittedAt;
}

