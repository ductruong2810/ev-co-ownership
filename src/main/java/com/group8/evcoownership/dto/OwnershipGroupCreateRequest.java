package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OwnershipGroupCreateRequest(
        @NotBlank @Size(max = 100) String groupName,
        @Size(max = 4000) String description,     // optional: null được, nếu có thì ≤ 4000
        @NotNull @Positive Integer memberCapacity // > 0 và không được null
) {}