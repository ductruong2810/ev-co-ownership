package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QrCheckOutRequestDTO(
        @NotNull Long bookingId,
        Integer odometer,
        BigDecimal batteryLevel,
        String cleanliness,
       String notes,
        String issues
) {
}

