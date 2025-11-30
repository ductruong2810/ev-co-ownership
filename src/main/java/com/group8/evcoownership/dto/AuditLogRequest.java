package com.group8.evcoownership.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Audit log payload sent from frontend")
public class AuditLogRequest {

    @Schema(description = "Logical type of the action (e.g. DOCUMENT_REVIEW, MAINTENANCE_REQUEST)", example = "DOCUMENT_REVIEW")
    private String type;

    @Schema(description = "ID of the related entity", example = "123")
    private String entityId;

    @Schema(description = "Type of entity (e.g. DOCUMENT, MAINTENANCE, VEHICLE_CHECK)", example = "DOCUMENT")
    private String entityType;

    @Schema(description = "Human readable message for the action", example = "Approved driver license front image")
    private String message;

    @Schema(description = "Optional structured metadata for this action")
    private Map<String, Object> metadata;
}

