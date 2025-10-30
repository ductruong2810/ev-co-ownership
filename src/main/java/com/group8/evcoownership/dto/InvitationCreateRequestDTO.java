package com.group8.evcoownership.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record InvitationCreateRequestDTO(
        @Email @NotBlank String inviteeEmail,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal suggestedPercentage // optional
) {
}
