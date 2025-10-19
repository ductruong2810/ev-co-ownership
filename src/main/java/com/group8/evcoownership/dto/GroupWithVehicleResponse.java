package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.GroupStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record GroupWithVehicleResponse(
        // Group information
        Long groupId,
        String groupName,
        String description,
        Integer memberCapacity,
        GroupStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        
        // Vehicle information
        Long vehicleId,
        String brand,
        String model,
        String licensePlate,
        String chassisNumber,
        String qrCode,
        BigDecimal vehicleValue,
        
        // Vehicle images - có thể là String hoặc String[]
        Map<String, Object> vehicleImages
) {
}
