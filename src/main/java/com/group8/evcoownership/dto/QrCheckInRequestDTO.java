package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;

public record QrCheckInRequestDTO(@NotBlank String qrCode) {
}

