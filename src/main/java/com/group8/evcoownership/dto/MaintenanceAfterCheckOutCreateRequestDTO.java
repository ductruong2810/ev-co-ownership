package com.group8.evcoownership.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceAfterCheckOutCreateRequestDTO {

    @NotNull(message = "vehicleId is required")
    private Long vehicleId;

    @NotNull(message = "liableUserId is required")
    private Long liableUserId;   // co-owner làm hư xe

    @NotBlank(message = "description is required")
    @Size(min = 5, max = 2000, message = "description must be 5-2000 characters")
    private String description;

    @NotNull(message = "cost is required")
    @DecimalMin(value = "0.01", message = "cost must be > 0")
    private BigDecimal cost;

    @NotNull(message = "estimatedDurationDays is required")
    @Min(value = 1, message = "estimatedDurationDays must be >= 1")
    private Integer estimatedDurationDays;
}