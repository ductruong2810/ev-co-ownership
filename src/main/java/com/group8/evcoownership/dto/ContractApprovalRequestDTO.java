package com.group8.evcoownership.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ContractApprovalRequestDTO {

    @NotNull(message = "Contract ID is required")
    @Positive(message = "Contract ID must be a positive number")
    private Long contractId;

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(APPROVE|REJECT)$",
            message = "Action must be either 'APPROVE' or 'REJECT'")
    private String action;

    @Size(min = 10, max = 500,
            message = "Rejection reason must be between 10 and 500 characters")
    private String reason;
}
