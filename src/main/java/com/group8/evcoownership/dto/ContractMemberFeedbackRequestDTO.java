package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContractMemberFeedbackRequestDTO(
        @NotNull(message = "Contract ID is required")
        Long contractId,
        
        @NotBlank(message = "Status is required")
        String status, // "APPROVED" or "REJECTED"
        
        @Size(max = 1000, message = "Reason must not exceed 1000 characters")
        String reason // Required if status is REJECTED
) {
    public boolean isValid() {
        if ("REJECTED".equalsIgnoreCase(status)) {
            return reason != null && !reason.trim().isEmpty() && reason.trim().length() >= 10;
        }
        return "APPROVED".equalsIgnoreCase(status);
    }
}

