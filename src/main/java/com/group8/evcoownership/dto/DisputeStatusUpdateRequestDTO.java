package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DisputeStatusUpdateRequestDTO(
        @NotBlank @Pattern(regexp = "(?i)OPEN|RESOLVED|REJECTED") String status,
        String resolutionNote,
        @NotNull Long resolvedById   // bắt buộc khi -> RESOLVED/REJECTED
) {
}
