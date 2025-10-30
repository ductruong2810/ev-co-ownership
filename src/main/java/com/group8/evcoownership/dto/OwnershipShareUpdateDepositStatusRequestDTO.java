package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.DepositStatus;
import jakarta.validation.constraints.NotNull;

public record OwnershipShareUpdateDepositStatusRequestDTO(
        @NotNull DepositStatus newStatus
) {
}
