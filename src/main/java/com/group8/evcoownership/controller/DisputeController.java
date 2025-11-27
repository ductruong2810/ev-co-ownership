package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DisputeCreateRequestDTO;
import com.group8.evcoownership.dto.DisputeResolveRequestDTO;
import com.group8.evcoownership.dto.DisputeResponseDTO;
import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.DisputeType;
import com.group8.evcoownership.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Tag(name = "Disputes", description = "Quản lý tranh chấp")
@PreAuthorize("isAuthenticated()")
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * Tạo tranh chấp mới (CO_OWNER)
     */
    @PostMapping
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(
            summary = "[CO_OWNER] Tạo tranh chấp",
            description = "Người dùng tạo tranh chấp mới về nhóm của mình"
    )
    public ResponseEntity<DisputeResponseDTO> createDispute(
            @Valid @RequestBody DisputeCreateRequestDTO request,
            Authentication auth
    ) {
        String username = auth.getName();
        DisputeResponseDTO dispute = disputeService.create(request, username);
        return ResponseEntity.ok(dispute);
    }

    /**
     * Lấy danh sách tranh chấp với lọc (STAFF/ADMIN)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(
            summary = "[STAFF/ADMIN] Danh sách tranh chấp",
            description = "Lấy danh sách tranh chấp với khả năng lọc theo status, type, group, date range"
    )
    public ResponseEntity<Page<DisputeResponseDTO>> getDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) DisputeType disputeType,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Page<DisputeResponseDTO> disputes = disputeService.getFiltered(
                status, disputeType, groupId, from, to, page, size
        );
        return ResponseEntity.ok(disputes);
    }

    /**
     * Lấy danh sách tranh chấp chờ xử lý (OPEN)
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(
            summary = "[STAFF/ADMIN] Tranh chấp chờ xử lý",
            description = "Lấy danh sách các tranh chấp đang ở trạng thái OPEN"
    )
    public ResponseEntity<List<DisputeResponseDTO>> getPendingDisputes() {
        List<DisputeResponseDTO> disputes = disputeService.getPendingDisputes();
        return ResponseEntity.ok(disputes);
    }

    /**
     * Lấy chi tiết một tranh chấp
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(
            summary = "Chi tiết tranh chấp",
            description = "Lấy thông tin chi tiết của một tranh chấp"
    )
    public ResponseEntity<DisputeResponseDTO> getDisputeById(@PathVariable Long id) {
        DisputeResponseDTO dispute = disputeService.getOne(id);
        return ResponseEntity.ok(dispute);
    }

    /**
     * Lấy tranh chấp của user hiện tại (CO_OWNER)
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(
            summary = "[CO_OWNER] Tranh chấp của tôi",
            description = "Lấy danh sách các tranh chấp do người dùng hiện tại tạo"
    )
    public ResponseEntity<List<DisputeResponseDTO>> getMyDisputes(Authentication auth) {
        String username = auth.getName();
        List<DisputeResponseDTO> disputes = disputeService.getMyDisputes(username);
        return ResponseEntity.ok(disputes);
    }

    /**
     * Lấy tranh chấp theo nhóm
     */
    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(
            summary = "Tranh chấp theo nhóm",
            description = "Lấy danh sách tranh chấp của một nhóm cụ thể"
    )
    public ResponseEntity<List<DisputeResponseDTO>> getDisputesByGroup(@PathVariable Long groupId) {
        List<DisputeResponseDTO> disputes = disputeService.getDisputesByGroup(groupId);
        return ResponseEntity.ok(disputes);
    }

    /**
     * Giải quyết tranh chấp (STAFF/ADMIN)
     */
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(
            summary = "[STAFF/ADMIN] Giải quyết tranh chấp",
            description = "Staff/Admin giải quyết tranh chấp (RESOLVED hoặc REJECTED)"
    )
    public ResponseEntity<DisputeResponseDTO> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody DisputeResolveRequestDTO request,
            Authentication auth
    ) {
        String username = auth.getName();
        DisputeResponseDTO dispute = disputeService.resolveDispute(id, request, username);
        return ResponseEntity.ok(dispute);
    }

    /**
     * Cập nhật trạng thái tranh chấp (STAFF/ADMIN)
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(
            summary = "[STAFF/ADMIN] Cập nhật trạng thái",
            description = "Staff/Admin cập nhật trạng thái tranh chấp"
    )
    public ResponseEntity<DisputeResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam DisputeStatus status,
            Authentication auth
    ) {
        String username = auth.getName();
        DisputeResponseDTO dispute = disputeService.updateStatus(id, status, username);
        return ResponseEntity.ok(dispute);
    }
}

