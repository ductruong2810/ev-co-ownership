package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentCreateRequestDTO {

    @NotNull(message = "BookingId is required")
    private Long bookingId;

    private String description;

    @NotNull(message = "Actual cost is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Actual cost must be greater than 0")
    private BigDecimal actualCost;

    private String imageUrls; // optional
}
