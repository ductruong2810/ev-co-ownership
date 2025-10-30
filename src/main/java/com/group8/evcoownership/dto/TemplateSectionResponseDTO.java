package com.group8.evcoownership.dto;

public record TemplateSectionResponseDTO(
        String section,
        String content,
        String description,
        boolean hasChanges
) {
}
