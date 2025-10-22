package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
@Tag(name = "Maintenance", description = "Quản lý bảo trì và sửa chữa phương tiện")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    /**
     * Technician tạo maintenance request
     * Example:
     * POST /api/maintenance/request
     */
    @PostMapping("/request")
    @Operation(summary = "Tạo yêu cầu bảo trì", description = "Kỹ thuật viên tạo yêu cầu bảo trì cho phương tiện")
    public ResponseEntity<Maintenance> createMaintenanceRequest(
            @RequestParam Long vehicleId,
            @RequestParam Long technicianId,
            @RequestParam String description,
            @RequestParam BigDecimal estimatedCost) {

        Maintenance maintenance = maintenanceService.createMaintenanceRequest(
                vehicleId, technicianId, description, estimatedCost);

        return ResponseEntity.ok(maintenance);
    }

    /**
     * Staff/Admin approve maintenance và tự động cancel bookings
     * Example:
     * PUT /api/maintenance/1/approve
     */
    @PutMapping("/{maintenanceId}/approve")
    @Operation(summary = "Phê duyệt bảo trì", description = "Staff/Admin phê duyệt yêu cầu bảo trì và tự động hủy các booking bị ảnh hưởng")
    public ResponseEntity<Map<String, Object>> approveMaintenance(
            @PathVariable Long maintenanceId,
            @RequestParam Long approvedByUserId,
            @RequestParam LocalDateTime startDateTime,
            @RequestParam LocalDateTime endDateTime) {

        Map<String, Object> result = maintenanceService.approveMaintenanceAndCancelBookings(
                maintenanceId, approvedByUserId, startDateTime, endDateTime);

        return ResponseEntity.ok(result);
    }
}
