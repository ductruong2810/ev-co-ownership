package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VehicleCreateRequestDTO(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String licensePlate,
        @NotBlank String chassisNumber,
        @DecimalMin(value = "0.00")
        @DecimalMax(value = "1000000000", message = "Vehicle value cannot exceed 1 billion VND")
        @NotNull BigDecimal vehicleValue,
        @NotNull Long groupId
) {
}
