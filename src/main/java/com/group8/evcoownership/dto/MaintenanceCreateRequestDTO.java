package com.group8.evcoownership.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceCreateRequestDTO {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Cost is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Cost must be greater than 0")
    private BigDecimal cost;

    @NotNull(message = "Estimated Duration is required")
    @DecimalMin(value = "1", inclusive = false, message = "estimatedDurationDays must be >= 1")
    private Integer estimatedDurationDays;
}
