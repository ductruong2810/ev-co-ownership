package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ContractUpdateRequestDTO(
        @NotNull(message = "Start date is required")
        LocalDate startDate,
        
        @NotNull(message = "End date is required")
        LocalDate endDate
) {
    // Validation: End date must be after start date
    public boolean isValidDateRange() {
        return endDate != null && startDate != null && endDate.isAfter(startDate);
    }
    public boolean isInvalidDateRange() {
        return !isValidDateRange();
    }
}

