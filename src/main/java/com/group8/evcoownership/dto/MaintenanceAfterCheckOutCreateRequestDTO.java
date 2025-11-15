package com.group8.evcoownership.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceAfterCheckOutCreateRequestDTO {

    @NotBlank(message = "description is required")
    @Size(min = 5, max = 2000, message = "description must be 5-2000 characters")
    private String description;

    @NotNull(message = "cost is required")
    @DecimalMin(value = "10000", message = "cost must be > 10000")
    private BigDecimal cost;

    @NotNull(message = "estimatedDurationDays is required")
    @Min(value = 1, message = "estimatedDurationDays must be >= 1")
    private Integer estimatedDurationDays;
}