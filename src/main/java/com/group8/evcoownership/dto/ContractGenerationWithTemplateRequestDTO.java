package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ContractGenerationWithTemplateRequestDTO(
        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotBlank
        String terms,

        @NotBlank
        String htmlTemplate
) {
}
