package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.VehicleRejection;
import com.group8.evcoownership.entity.VehicleReport;
import com.group8.evcoownership.enums.RejectionReason;
import com.group8.evcoownership.service.VehicleRejectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-rejections")
@RequiredArgsConstructor
public class VehicleRejectionController {

    private final VehicleRejectionService vehicleRejectionService;

    /**
     * User từ chối sử dụng xe
     * Example:
     * POST /api/vehicle-rejections
     */
    @PostMapping
    public ResponseEntity<VehicleRejection> rejectVehicle(
            @RequestParam Long bookingId,
            @RequestParam RejectionReason rejectionReason,
            @RequestParam String detailedReason,
            @RequestParam(required = false) List<String> photos) {

        VehicleRejection rejection = vehicleRejectionService.rejectVehicle(
                bookingId, rejectionReason, detailedReason, photos);

        return ResponseEntity.ok(rejection);
    }

    /**
     * Technician giải quyết từ chối
     * Example:
     * PUT /api/vehicle-rejections/{rejectionId}/resolve
     */
    @PutMapping("/{rejectionId}/resolve")
    public ResponseEntity<VehicleReport> resolveRejection(
            @PathVariable Long rejectionId,
            @RequestParam String resolutionNotes) {

        VehicleReport report = vehicleRejectionService.resolveRejection(rejectionId, resolutionNotes);
        return ResponseEntity.ok(report);
    }

    /**
     * Lấy danh sách từ chối đang chờ xử lý
     * Example:
     * GET /api/vehicle-rejections/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<VehicleRejection>> getPendingRejections() {
        List<VehicleRejection> rejections = vehicleRejectionService.getPendingRejections();
        return ResponseEntity.ok(rejections);
    }

    /**
     * Lấy lịch sử từ chối của xe
     * Example:
     * GET /api/vehicle-rejections/vehicle/{vehicleId}
     */
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<VehicleRejection>> getVehicleRejectionHistory(@PathVariable Long vehicleId) {
        List<VehicleRejection> history = vehicleRejectionService.getVehicleRejectionHistory(vehicleId);
        return ResponseEntity.ok(history);
    }
}
