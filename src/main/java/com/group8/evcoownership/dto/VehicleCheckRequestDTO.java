package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VehicleCheckRequestDTO {
    @NotNull(message = "Booking ID is required!!")
    private Long bookingId;
    private Integer odometer;
    private BigDecimal batteryLevel;
    private String cleanliness;
    private String notes;
    private String issues;
}
