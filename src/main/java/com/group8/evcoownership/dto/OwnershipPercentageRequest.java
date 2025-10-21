package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OwnershipPercentageRequest {
    @NotNull(message = "Ownership percentage is required")
    @DecimalMin(value = "0.0", message = "Ownership percentage must be at least 0%")
    @DecimalMax(value = "100.0", message = "Ownership percentage cannot exceed 100%")
    private BigDecimal ownershipPercentage;
    
    private String reason; // Lý do thay đổi tỷ lệ sở hữu
}
