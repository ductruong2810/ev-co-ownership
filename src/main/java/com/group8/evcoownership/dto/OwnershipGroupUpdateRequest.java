package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OwnershipGroupUpdateRequest(
        @NotBlank @Size(max = 100) String groupName,
        @Size(max = 4000) String description,
        @Positive Integer memberCapacity
) {
}