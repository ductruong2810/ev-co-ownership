package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.Map;

@Builder
public record ContractTemplateRequestDTO(
        @NotNull
        String templateType, // "REACT_TSX", "JSON", "MARKDOWN", "DOCX", "PDF"

        @NotNull
        Object template, // React TSX component, JSON object, Markdown string, hoặc base64 encoded file

        Map<String, Object> templateData, // Data để fill vào template

        String templateName, // Tên template

        String description, // Mô tả template

        Map<String, Object> templateProps // Props cho React component
) {
}
