package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveContractDataRequest(
//        @NotBlank(message = "Terms cannot be blank")
//        @Size(max = 10000, message = "Terms cannot exceed 10000 characters")
//        String terms
) {
}
