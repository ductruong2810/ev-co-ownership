package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AmountRequestDTO(
        @NotNull @Positive BigDecimal amount
) {
}