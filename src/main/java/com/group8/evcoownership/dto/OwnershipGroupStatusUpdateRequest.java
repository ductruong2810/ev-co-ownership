package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.GroupStatus;
import jakarta.validation.constraints.NotNull;

public record OwnershipGroupStatusUpdateRequest(
        @NotNull GroupStatus status
) {
}
