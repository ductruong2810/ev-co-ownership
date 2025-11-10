package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContractTermsUpdateRequestDTO(
        @NotBlank(message = "Terms cannot be blank")
        @Size(min = 20, max = 20000, message = "Terms must be between 20 and 20000 characters")
        String terms
) {
}

