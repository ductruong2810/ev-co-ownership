package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvitationAcceptRequest(
        @NotBlank @Size(min = 6, max = 6) String otp
) {
}
