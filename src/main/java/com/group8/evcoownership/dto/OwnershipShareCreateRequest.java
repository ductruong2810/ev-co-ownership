package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OwnershipShareCreateRequest(
        @NotNull Long userId,
        @NotNull Long groupId,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal ownershipPercentage
) {
}
