package com.group8.evcoownership.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VehicleResponseDTO(
        Long vehicleId,
        String brand,
        String model,
        String licensePlate,
        String chassisNumber,
        String qrCode,
        Long groupId,
        BigDecimal vehicleValue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}