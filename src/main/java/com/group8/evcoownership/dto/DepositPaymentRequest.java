package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record DepositPaymentRequest(
        @NotNull
        Long userId,

        @NotNull
        Long groupId,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount,

        @NotNull
        String paymentMethod
) {
}
