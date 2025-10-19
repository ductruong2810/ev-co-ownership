package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleUpdateRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String licensePlate,
        @NotBlank String chassisNumber
) {}
