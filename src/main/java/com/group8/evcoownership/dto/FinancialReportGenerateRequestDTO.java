package com.group8.evcoownership.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FinancialReportGenerateRequestDTO(
        @NotNull @Min(2000) Integer year,
        @NotNull @Min(1) @Max(12) Integer month
) {}
