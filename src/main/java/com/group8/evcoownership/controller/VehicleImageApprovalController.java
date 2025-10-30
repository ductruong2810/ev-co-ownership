package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.VehicleImageApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff/vehicle-images")
@RequiredArgsConstructor
@Tag(name = "Vehicle Image Approval", description = "Duyệt và quản lý hình ảnh phương tiện (dành cho Staff)")
public class VehicleImageApprovalController {

    private final VehicleImageApprovalService approvalService;

    @GetMapping("/pending")
    @Operation(summary = "Hình ảnh chờ duyệt", description = "Lấy danh sách hình ảnh phương tiện chờ duyệt với phân trang")
    public ResponseEntity<Page<VehicleImageResponseDTO>> getPendingImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(approvalService.getPendingImages(pageable));
    }

    @GetMapping("/groups/pending")
    @Operation(summary = "Nhóm có hình ảnh chờ duyệt", description = "Lấy danh sách các nhóm có hình ảnh phương tiện chờ duyệt")
    public ResponseEntity<List<GroupImageApprovalSummaryDTO>> getGroupsWithPendingImages() {
        return ResponseEntity.ok(approvalService.getGroupsWithPendingImages());
    }

    @GetMapping("/groups/{groupId}/images")
    @Operation(summary = "Hình ảnh theo nhóm", description = "Lấy danh sách hình ảnh phương tiện của một nhóm cụ thể")
    public ResponseEntity<List<VehicleImageResponseDTO>> getImagesByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getImagesByGroupId(groupId));
    }

    @GetMapping("/groups/{groupId}/vehicle-with-images")
    @Operation(summary = "Phương tiện với hình ảnh", description = "Lấy thông tin phương tiện cùng với tất cả hình ảnh của nhóm")
    public ResponseEntity<VehicleWithImagesResponseDTO> getVehicleWithImagesByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getVehicleWithImagesByGroupId(groupId));
    }

    @PatchMapping("/groups/{groupId}/review")
    @Operation(summary = "Duyệt hình ảnh nhóm", description = "Staff duyệt tất cả hình ảnh phương tiện của một nhóm")
    public ResponseEntity<GroupApprovalResultDTO> reviewGroupImages(
            @PathVariable Long groupId,
            @RequestBody @Valid VehicleImageApprovalRequestDTO request,
            @AuthenticationPrincipal String staffEmail) {
        return ResponseEntity.ok(approvalService.approveGroupImages(groupId, request, staffEmail));
    }

    @PatchMapping("/{imageId}/review")
    @Operation(summary = "Duyệt hình ảnh đơn lẻ", description = "Staff duyệt một hình ảnh phương tiện cụ thể")
    public ResponseEntity<VehicleImageResponseDTO> reviewImage(
            @PathVariable Long imageId,
            @RequestBody @Valid VehicleImageApprovalRequestDTO request,
            @AuthenticationPrincipal String staffEmail) {
        return ResponseEntity.ok(approvalService.approveImage(imageId, request, staffEmail));
    }

    @GetMapping("/stats")
    @Operation(summary = "Thống kê hình ảnh", description = "Lấy thống kê số lượng hình ảnh theo trạng thái duyệt")
    public ResponseEntity<Map<String, Long>> getImageStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pending", approvalService.getImageCountByStatus(com.group8.evcoownership.enums.ImageApprovalStatus.PENDING));
        stats.put("approved", approvalService.getImageCountByStatus(com.group8.evcoownership.enums.ImageApprovalStatus.APPROVED));
        stats.put("rejected", approvalService.getImageCountByStatus(com.group8.evcoownership.enums.ImageApprovalStatus.REJECTED));
        return ResponseEntity.ok(stats);
    }
}
