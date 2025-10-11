package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    /**
     * Technician tạo maintenance request
     * Example:
     * POST /api/maintenance/request
     */
    @PostMapping("/request")
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
