package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ImageApprovalStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record VehicleImageResponse(
        Long imageId,
        Long vehicleId,
        String imageUrl,
        String imageType,
        ImageApprovalStatus approvalStatus,
        String approvedByName,
        LocalDateTime approvedAt,
        String rejectionReason,
        LocalDateTime uploadedAt
) {
}
