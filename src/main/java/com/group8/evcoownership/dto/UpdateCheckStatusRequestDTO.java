package com.group8.evcoownership.dto;


import jakarta.validation.constraints.NotBlank;

public record UpdateCheckStatusRequestDTO(
        @NotBlank(message = "status is required") String status,
        String notes,
        String issues
) {
}
