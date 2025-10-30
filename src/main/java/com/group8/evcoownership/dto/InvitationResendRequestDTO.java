package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;

public record InvitationResendRequestDTO(
        @NotNull Long invitationId
) {
}
