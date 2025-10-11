package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.VehicleReport;
import com.group8.evcoownership.enums.ReportType;
import com.group8.evcoownership.service.VehicleReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/vehicle-reports")
@RequiredArgsConstructor
public class VehicleReportController {

    private final VehicleReportService vehicleReportService;

    /**
     * User tạo check-out report
     * Example:
     * POST /api/vehicle-reports/user-checkout
     */
    @PostMapping("/user-checkout")
    public ResponseEntity<VehicleReport> createUserCheckoutReport(
            @RequestParam Long bookingId,
            @RequestParam Integer odometer,
            @RequestParam BigDecimal batteryLevel,
            @RequestParam(required = false) String damages,
            @RequestParam String cleanliness,
            @RequestParam(required = false) String notes) {

        VehicleReport report = vehicleReportService.createUserCheckoutReport(
                bookingId, odometer, batteryLevel, damages, cleanliness, notes);

        return ResponseEntity.ok(report);
    }

    /**
     * Technician xác nhận report và báo cáo thêm lỗi
     * Example:
     * POST /api/vehicle-reports/technician-verification-with-findings
     */
    @PostMapping("/technician-verification-with-findings")
    public ResponseEntity<VehicleReport> createTechnicianVerificationWithFindings(
            @RequestParam Long userReportId,
            @RequestParam String technicianNotes,
            @RequestParam(required = false) String additionalDamages,
            @RequestParam(required = false) String additionalNotes) {

        VehicleReport verification = vehicleReportService.createTechnicianVerification(
                userReportId, technicianNotes, additionalDamages, additionalNotes);

        return ResponseEntity.ok(verification);
    }

    /**
     * Technician phát hiện lỗi nghiêm trọng và tạo Maintenance
     * Example:
     * POST /api/vehicle-reports/create-maintenance-from-findings
     */
    @PostMapping("/create-maintenance-from-findings")
    public ResponseEntity<Map<String, Object>> createMaintenanceFromFindings(
            @RequestParam Long userReportId,
            @RequestParam String criticalIssues,
            @RequestParam String maintenanceDescription) {

        Map<String, Object> result = vehicleReportService.createMaintenanceFromTechnicianFindings(
                userReportId, criticalIssues, maintenanceDescription);

        return ResponseEntity.ok(result);
    }

    /**
     * Lấy thông tin xe cho user check-in
     * Example:
     * GET /api/vehicle-reports/checkin-info/{vehicleId}
     */
    @GetMapping("/checkin-info/{vehicleId}")
    public ResponseEntity<Optional<VehicleReport>> getVehicleInfoForCheckin(@PathVariable Long vehicleId) {
        Optional<VehicleReport> report = vehicleReportService.getVehicleInfoForCheckin(vehicleId);
        return ResponseEntity.ok(report);
    }

    /**
     * Kiểm tra pin có đủ không
     * Example:
     * GET /api/vehicle-reports/battery-check/{vehicleId}?requiredBattery=80
     */
    @GetMapping("/battery-check/{vehicleId}")
    public ResponseEntity<Boolean> checkBatterySufficient(
            @PathVariable Long vehicleId,
            @RequestParam Integer requiredBattery) {

        Boolean isSufficient = vehicleReportService.isBatterySufficient(vehicleId, requiredBattery);
        return ResponseEntity.ok(isSufficient);
    }

    /**
     * Technician tạo vehicle report (legacy method)
     * Example:
     * POST /api/vehicle-reports
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createVehicleReport(
            @RequestParam Long vehicleId,
            @RequestParam Long technicianId,
            @RequestParam ReportType reportType,
            @RequestParam(required = false) Integer mileage,
            @RequestParam(required = false) BigDecimal chargeLevel,
            @RequestParam(required = false) String damages,
            @RequestParam(required = false) String cleanliness,
            @RequestParam(required = false) String notes) {

        Map<String, Object> result = vehicleReportService.createVehicleReport(
                vehicleId, technicianId, reportType, mileage,
                chargeLevel, damages, cleanliness, notes);

        return ResponseEntity.ok(result);
    }
}
