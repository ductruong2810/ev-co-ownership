package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ContractApprovalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record ContractApprovalRequest(
        @NotNull
        ContractApprovalStatus status,

        String rejectionReason
) {
}
