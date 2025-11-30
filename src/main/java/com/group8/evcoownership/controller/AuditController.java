package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.AuditLogListResponse;
import com.group8.evcoownership.dto.AuditLogRequest;
import com.group8.evcoownership.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Endpoints for recording and viewing audit logs")
public class AuditController {

    private final AuditService auditService;

    @PostMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','TECHNICIAN')")
    @Operation(
            summary = "Create an audit log entry",
            description = """
                    Lightweight endpoint used by the frontend to record important actions such as:
                    - Staff approving / rejecting user documents
                    - Technician creating or completing maintenance tasks
                    - Staff reviewing vehicle inspection reports

                    The backend persists this data to the database for compliance/debugging.
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "Log accepted and recorded"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid payload",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    public ResponseEntity<Void> createAuditLog(@RequestBody AuditLogRequest request) {
        auditService.createAuditLog(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "[ADMIN] Get audit logs with filters",
            description = """
                    Retrieve audit logs with optional filters:
                    - page: Page number (0-indexed, default: 0)
                    - size: Page size (default: 20, max: 100)
                    - userId: Filter by user ID
                    - actionType: Filter by action type (APPROVE, REJECT, CREATE, UPDATE, DELETE, REVIEW)
                    - entityType: Filter by entity type (DOCUMENT, MAINTENANCE, VEHICLE_CHECK, GROUP, CONTRACT)
                    - from: Start date (ISO format)
                    - to: End date (ISO format)
                    - search: Search in message, user name, or action type
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Audit logs retrieved successfully",
                            content = @Content(schema = @Schema(implementation = AuditLogListResponse.class))
                    )
            }
    )
    public ResponseEntity<AuditLogListResponse> getAuditLogs(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String search
    ) {
        AuditLogListResponse response = auditService.getAuditLogs(
                page, size, userId, actionType, entityType, from, to, search
        );
        return ResponseEntity.ok(response);
    }
}

