package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VehicleCreateRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String licensePlate,
        @NotBlank String chassisNumber,
        @DecimalMin(value = "0.00") @NotNull BigDecimal vehicleValue,
        @NotNull Long groupId
) {
}
