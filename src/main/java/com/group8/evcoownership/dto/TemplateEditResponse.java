package com.group8.evcoownership.dto;

public record TemplateEditResponse(
        boolean success,
        String message,
        String section,
        String preview
) {
}
