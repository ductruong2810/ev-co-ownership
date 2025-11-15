package com.group8.evcoownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record FundTopupRequestDTO(
        @NotNull(message = "userId is required")
        @Pattern(regexp = "\\d+", message = "userId must be a valid number")
        @Schema(description = "ID của user", example = "1")
        String userId,


        @NotNull(message = "groupId is required")
        @Pattern(regexp = "\\d+", message = "groupId must be a valid number")
        @Schema(description = "ID của group", example = "5")
        String groupId,      // Operating fund

        @NotNull(message = "Amount is required")
        @Min(value = 10000, message = "Amount must be at least 10,000 VND")
        @Max(value = 100000000, message = "Amount must not exceed 100,000,000 VND")
        BigDecimal amount,

        String note
) {
}
