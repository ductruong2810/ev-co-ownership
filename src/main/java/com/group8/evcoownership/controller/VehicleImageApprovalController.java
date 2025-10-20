package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.VehicleImageApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff/vehicle-images")
@RequiredArgsConstructor
public class VehicleImageApprovalController {

    private final VehicleImageApprovalService approvalService;

    @GetMapping("/pending")
    public ResponseEntity<Page<VehicleImageResponse>> getPendingImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(approvalService.getPendingImages(pageable));
    }

    @GetMapping("/groups/pending")
    public ResponseEntity<List<GroupImageApprovalSummary>> getGroupsWithPendingImages() {
        return ResponseEntity.ok(approvalService.getGroupsWithPendingImages());
    }

    @GetMapping("/groups/{groupId}/images")
    public ResponseEntity<List<VehicleImageResponse>> getImagesByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getImagesByGroupId(groupId));
    }

    @GetMapping("/groups/{groupId}/vehicle-with-images")
    public ResponseEntity<VehicleWithImagesResponse> getVehicleWithImagesByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getVehicleWithImagesByGroupId(groupId));
    }

    @PatchMapping("/groups/{groupId}/approve")
    public ResponseEntity<GroupApprovalResult> approveGroupImages(
            @PathVariable Long groupId,
            @RequestBody @Valid VehicleImageApprovalRequest request,
            Authentication authentication) {

        String staffEmail = authentication.getName();
        return ResponseEntity.ok(approvalService.approveGroupImages(groupId, request, staffEmail));
    }

    @PatchMapping("/{imageId}/approve")
    public ResponseEntity<VehicleImageResponse> approveImage(
            @PathVariable Long imageId,
            @RequestBody @Valid VehicleImageApprovalRequest request,
            Authentication authentication) {

        String staffEmail = authentication.getName();
        return ResponseEntity.ok(approvalService.approveImage(imageId, request, staffEmail));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getImageStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pending", approvalService.getImageCountByStatus(com.group8.evcoownership.enums.ImageApprovalStatus.PENDING));
        stats.put("approved", approvalService.getImageCountByStatus(com.group8.evcoownership.enums.ImageApprovalStatus.APPROVED));
        stats.put("rejected", approvalService.getImageCountByStatus(com.group8.evcoownership.enums.ImageApprovalStatus.REJECTED));
        return ResponseEntity.ok(stats);
    }
}
