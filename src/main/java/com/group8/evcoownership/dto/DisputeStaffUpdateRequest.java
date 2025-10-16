package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.RelatedEntityType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DisputeStaffUpdateRequest(
        @NotNull RelatedEntityType relatedEntityType, // BẮT BUỘC
        @NotNull Long relatedEntityId,                // BẮT BUỘC
        @NotBlank String resolution,                  // BẮT BUỘC
        @Digits(integer = 10, fraction = 2) BigDecimal resolutionAmount // optional
) {
}
