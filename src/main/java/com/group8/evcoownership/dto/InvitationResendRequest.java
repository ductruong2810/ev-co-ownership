package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;

public record InvitationResendRequest(
        @NotNull Long invitationId
) {
}
