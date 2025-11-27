package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;

public record QrCheckInRequestDTO(
        @NotNull String qrCode,
        String signature // Base64 encoded signature image (optional but recommended)
) {
}

