package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record QrCheckOutRequestDTO(
        @NotBlank String qrCode,
        Integer odometer,
        BigDecimal batteryLevel,
        String cleanliness,
        String notes,
        String issues
) {
}

