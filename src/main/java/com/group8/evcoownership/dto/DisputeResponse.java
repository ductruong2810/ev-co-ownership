package com.group8.evcoownership.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Builder
public record DisputeResponse(
        Long id,
        Long fundId,
        Long createdBy,
        String disputeType,
        String relatedEntityType,
        Long relatedEntityId,
        String description,
        BigDecimal disputedAmount,
        String resolution,
        BigDecimal resolutionAmount,
        String status,
        Long resolvedById,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {}