package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.InvitationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvitationValidateResponseDTO(
        InvitationStatus status,
        Long groupId,
        String groupName,
        String inviteeEmail,
        BigDecimal suggestedPercentage,
        LocalDateTime expiresAt
) {
}