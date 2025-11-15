package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContractMemberFeedbackRequestDTO(
        @NotBlank(message = "Reaction type is required")
        String reactionType, // "AGREE" or "DISAGREE"

        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason // Required if reactionType is DISAGREE
) {
    public boolean isValid() {
        if ("DISAGREE".equalsIgnoreCase(reactionType)) {
            return reason != null && !reason.trim().isEmpty() && reason.trim().length() >= 10;
        }
        return "AGREE".equalsIgnoreCase(reactionType);
    }
}

