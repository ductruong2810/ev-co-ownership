package com.group8.evcoownership.dto;


import com.group8.evcoownership.enums.DisputeType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DisputeCreateRequestDTO(
        @NotNull Long fundId,
        @NotNull Long createdBy,
        @NotBlank String description,
        @NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull DisputeType disputeType   // phải thuộc DISPUTE_TYPES
) {
}
