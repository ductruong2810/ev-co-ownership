package com.group8.evcoownership.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateVotingRequestDTO {
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotBlank(message = "Voting type is required")
    private String votingType; // EXPENSE_APPROVAL, YES_NO, MULTIPLE_CHOICE

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    private LocalDateTime deadline;

    @NotNull(message = "Options are required")
    @Size(min = 2, message = "At least 2 options are required")
    private List<VotingOption> options;

    private Long relatedExpenseId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be positive")
    private BigDecimal estimatedAmount;

    @Data
    public static class VotingOption {
        @NotBlank(message = "Option key is required")
        private String key;

        @NotBlank(message = "Option label is required")
        private String label;
    }
}
