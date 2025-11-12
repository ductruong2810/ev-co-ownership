package com.group8.evcoownership.dto;

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
public class SubmitMemberFeedbackResponseDTO {
    private Long feedbackId;
    private MemberFeedbackStatus status;
    private Boolean isProcessed;
    private Integer approveCount;
    private Integer rejectCount;
    private ReactionType reactionType;
    private String reason;
    private LocalDateTime submittedAt;
}

