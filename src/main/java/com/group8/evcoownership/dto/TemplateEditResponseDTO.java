package com.group8.evcoownership.dto;

public record TemplateEditResponseDTO(
        boolean success,
        String message,
        String section,
        String preview
) {
}
