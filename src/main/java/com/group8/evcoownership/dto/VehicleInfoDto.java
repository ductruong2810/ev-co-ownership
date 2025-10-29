package com.group8.evcoownership.dto;

public record VehicleInfoDto(
        String brand,
        String model,
        String year,
        String licensePlate,
        String chassisNumber
) {
}
