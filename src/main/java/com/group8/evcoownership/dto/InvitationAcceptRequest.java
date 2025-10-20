package com.group8.evcoownership.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record InvitationAcceptRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 6, max = 6) String otp,
        @NotNull Long acceptUserId,
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal ownershipPercentage // nếu null: dùng suggestedPercentage
) {
}
