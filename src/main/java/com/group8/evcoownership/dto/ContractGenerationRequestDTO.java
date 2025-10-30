package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ContractGenerationRequestDTO(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank @Size(max = 100) String location,
        @NotBlank @Size(max = 100) String signDate,
        @Size(max = 100) String contractNumber // Optional, sẽ tự generate nếu null
) {
}
