package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    // ===============================================================
    // [CO_OWNER] — Tạo incident mới khi gặp sự cố
    // ===============================================================
    @PostMapping
    @Operation(
            summary = "[CO_OWNER] Create new incident",
            description = "User reports a new vehicle incident with description, cost, and image URLs."
    )
    @PreAuthorize("hasRole('CO_OWNER')")
    public ResponseEntity<IncidentResponseDTO> createIncident(
            @RequestBody IncidentCreateRequestDTO request,
            Authentication auth
    ) {
        String username = auth.getName();
        return ResponseEntity.ok(incidentService.create(request, username));
    }

    // ===============================================================
    // [CO_OWNER] — Cập nhật incident khi vẫn còn trạng thái PENDING
    // ===============================================================
    @PutMapping("/{id}")
    @Operation(
            summary = "[CO_OWNER] Update pending incident",
            description = "Allows the reporter to update description, cost, or images only when status = PENDING."
    )
    @PreAuthorize("hasRole('CO_OWNER')")
    public ResponseEntity<IncidentResponseDTO> updateIncident(
            @PathVariable Long id,
            @RequestBody IncidentUpdateRequestDTO request,
            Authentication auth
    ) {
        String username = auth.getName();
        return ResponseEntity.ok(incidentService.update(id, request, username));
    }

    // ===============================================================
// [STAFF / ADMIN] — Approve an incident
// ===============================================================
    @PutMapping("/{id}/approve")
    @Operation(
            summary = "[STAFF / ADMIN] Approve an incident",
            description = "Approve a pending incident and automatically create a related Expense entry."
    )
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<IncidentResponseDTO> approveIncident(
            @PathVariable Long id,
            Authentication auth
    ) {
        String username = auth.getName();
        return ResponseEntity.ok(incidentService.approveIncident(id, username));
    }

    // ===============================================================
// [STAFF / ADMIN] — Reject an incident
// ===============================================================
    @PutMapping("/{id}/reject")
    @Operation(
            summary = "[STAFF / ADMIN] Reject an incident",
            description = "Reject a pending incident with a specified rejection category and reason."
    )
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<IncidentResponseDTO> rejectIncident(
            @PathVariable Long id,
            @RequestBody IncidentRejectRequestDTO request,
            Authentication auth
    ) {
        String username = auth.getName();
        return ResponseEntity.ok(incidentService.rejectIncident(id, request, username));
    }


    // ===============================================================
    // [CO_OWNER] — Xem tất cả incident do chính mình tạo
    // ===============================================================
    @GetMapping("/my")
    @Operation(
            summary = "[CO_OWNER] Get my incidents",
            description = "Returns the list of incidents created by the logged-in user."
    )
    @PreAuthorize("hasRole('CO_OWNER')")
    public ResponseEntity<List<IncidentResponseDTO>> getMyIncidents(Authentication auth) {
        String username = auth.getName();
        return ResponseEntity.ok(incidentService.getMyIncidents(username));
    }

    // ===============================================================
    // [STAFF / ADMIN] — Xem tất cả incident trong hệ thống
    // ===============================================================
    @GetMapping
    @Operation(
            summary = "[STAFF / ADMIN] Get all incidents",
            description = "Returns all incidents for review and verification."
    )
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<List<IncidentResponseDTO>> getAllIncidents() {
        return ResponseEntity.ok(incidentService.getAll());
    }

    // ===============================================================
    // [ALL ROLES: CO_OWNER / STAFF / ADMIN] — Xem chi tiết 1 incident
    // ===============================================================
    @GetMapping("/{id}")
    @Operation(
            summary = "[CO_OWNER / STAFF / ADMIN] Get one incident detail",
            description = "Returns the details of a specific incident by ID."
    )
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    public ResponseEntity<IncidentResponseDTO> getIncidentById(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getOne(id));
    }
}
