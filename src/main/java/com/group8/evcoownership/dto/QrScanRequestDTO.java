package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;

public record QrScanRequestDTO(@NotBlank String qrCode) {
}


