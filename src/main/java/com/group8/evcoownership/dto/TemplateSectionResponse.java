package com.group8.evcoownership.dto;

public record TemplateSectionResponse(
        String section,
        String content,
        String description,
        boolean hasChanges
) {
}
