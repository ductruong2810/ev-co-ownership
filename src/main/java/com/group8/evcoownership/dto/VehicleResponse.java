package com.group8.evcoownership.dto;

import java.time.LocalDateTime;

public record VehicleResponse(
        Long vehicleId,
        String brand,
        String model,
        String licensePlate,
        String chassisNumber,
        String qrCode,
        Long groupId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}