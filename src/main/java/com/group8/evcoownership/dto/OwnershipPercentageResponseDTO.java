package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OwnershipPercentageResponseDTO {
    private Long userId;
    private Long groupId;
    private String userName;
    private BigDecimal ownershipPercentage;
    private BigDecimal investmentAmount;
    private BigDecimal vehicleValue;
    private BigDecimal totalAllocatedPercentage;
    private boolean canEdit;
    private String status; // PENDING, CONFIRMED, LOCKED
    private LocalDateTime updatedAt;
    private String message;
}
