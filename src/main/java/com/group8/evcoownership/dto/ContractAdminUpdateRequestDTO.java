package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ContractAdminUpdateRequestDTO(
        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @Size(min = 20, max = 20000, message = "Terms must be between 20 and 20000 characters")
        String terms
) {
    public boolean isInvalidDateRange() {
        return !isValidDateRange();
    }
    public boolean isValidDateRange() {
        return endDate != null && startDate != null && endDate.isAfter(startDate);
    }
}

