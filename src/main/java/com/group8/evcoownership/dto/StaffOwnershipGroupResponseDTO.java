package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.GroupStatus;

import java.time.LocalDateTime;

public record StaffOwnershipGroupResponseDTO(
        Long groupId,
        String groupName,
        String description,
        Integer memberCapacity,
        GroupStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String vehicleDescription, // Custom description theo status
        String rejectionReason      // Lý do từ chối nếu status = INACTIVE
) {
}
