package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.InvitationStatus;
import lombok.Builder;

@Builder
public record InvitationResponse(
        Long invitationId,
        Long groupId,
        String groupName,
        InvitationStatus status,
        java.math.BigDecimal suggestedPercentage,
        java.time.LocalDateTime expiresAt,
        Integer resendCount,
        java.time.LocalDateTime createdAt,
//        java.time.LocalDateTime updatedAt,
        java.time.LocalDateTime acceptedAt,
        Long acceptedByUserId
) {
}

