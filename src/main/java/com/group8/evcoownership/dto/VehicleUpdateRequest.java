package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VehicleUpdateRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String licensePlate,
        @NotBlank String chassisNumber,
        @DecimalMin(value = "0.00") 
        @DecimalMax(value = "100000000000", message = "Vehicle value cannot exceed 100 billion VND")
        @NotNull BigDecimal vehicleValue
) {
}
