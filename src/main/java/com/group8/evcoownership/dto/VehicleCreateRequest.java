package com.group8.evcoownership.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehicleCreateRequest(
        @NotBlank String brand,
        @NotBlank String model,
        @NotBlank String licensePlate,
        @NotBlank String chassisNumber,
        @NotNull Long groupId
) {}
