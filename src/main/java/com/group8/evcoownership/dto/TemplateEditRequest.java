package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.TemplateSection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TemplateEditRequest(
        @NotNull TemplateSection section,
        @NotBlank String content,
        String description
) {
}
