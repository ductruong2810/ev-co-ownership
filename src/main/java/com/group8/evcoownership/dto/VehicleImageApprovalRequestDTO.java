package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.ImageApprovalStatus;
import lombok.Builder;

@Builder
public record VehicleImageApprovalRequestDTO(
        ImageApprovalStatus status,
        String rejectionReason
) {
}
