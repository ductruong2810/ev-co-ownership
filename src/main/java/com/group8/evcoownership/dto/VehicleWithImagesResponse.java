package com.group8.evcoownership.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record VehicleWithImagesResponse(
        // Vehicle information
        Long vehicleId,
        String brand,
        String model,
        String licensePlate,
        String chassisNumber,
        String qrCode,
        BigDecimal vehicleValue,
        LocalDateTime vehicleCreatedAt,
        LocalDateTime vehicleUpdatedAt,

        // Group information
        Long groupId,
        String groupName,

        // Images information
        List<VehicleImageResponse> images,

        // Summary
        int totalImages,
        int pendingImages,
        int approvedImages,
        int rejectedImages
) {
}
