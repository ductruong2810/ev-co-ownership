package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.VehicleCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle-checks")
@RequiredArgsConstructor
public class VehicleCheckController {

    private final VehicleCheckService vehicleCheckService;
    private final UserRepository userRepository;

    /**
     * User tạo pre-use check với JWT authentication
     * Example:
     * POST /api/vehicle-checks/pre-use
     */
    @PostMapping("/pre-use")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VehicleCheck> createPreUseCheck(
            @RequestParam Long bookingId,
            @RequestParam(required = false) Integer odometer,
            @RequestParam(required = false) BigDecimal batteryLevel,
            @RequestParam(required = false) String cleanliness,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String issues) {

        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VehicleCheck check = vehicleCheckService.createPreUseCheck(
                bookingId, currentUser.getUserId(), odometer, batteryLevel, cleanliness, notes, issues);

        return ResponseEntity.ok(check);
    }

    /**
     * User tạo post-use check với JWT authentication
     * Example:
     * POST /api/vehicle-checks/post-use
     */
    @PostMapping("/post-use")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VehicleCheck> createPostUseCheck(
            @RequestParam Long bookingId,
            @RequestParam(required = false) Integer odometer,
            @RequestParam(required = false) BigDecimal batteryLevel,
            @RequestParam(required = false) String cleanliness,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String issues) {

        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VehicleCheck check = vehicleCheckService.createPostUseCheck(
                bookingId, currentUser.getUserId(), odometer, batteryLevel, cleanliness, notes, issues);

        return ResponseEntity.ok(check);
    }

    /**
     * User từ chối xe với JWT authentication
     * Example:
     * POST /api/vehicle-checks/reject
     */
    @PostMapping("/reject")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VehicleCheck> rejectVehicle(
            @RequestParam Long bookingId,
            @RequestParam String issues,
            @RequestParam(required = false) String notes) {

        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VehicleCheck check = vehicleCheckService.rejectVehicle(bookingId, currentUser.getUserId(), issues, notes);
        return ResponseEntity.ok(check);
    }

    /**
     * Technician approve/reject check
     * Example:
     * PUT /api/vehicle-checks/{checkId}/status
     */
    @PutMapping("/{checkId}/status")
    public ResponseEntity<VehicleCheck> updateCheckStatus(
            @PathVariable Long checkId,
            @RequestParam String status,
            @RequestParam(required = false) String notes) {

        VehicleCheck check = vehicleCheckService.updateCheckStatus(checkId, status, notes);
        return ResponseEntity.ok(check);
    }

    /**
     * Lấy checks của booking
     * Example:
     * GET /api/vehicle-checks/booking/{bookingId}
     */
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<VehicleCheck>> getChecksByBookingId(@PathVariable Long bookingId) {
        List<VehicleCheck> checks = vehicleCheckService.getChecksByBookingId(bookingId);
        return ResponseEntity.ok(checks);
    }

    /**
     * Lấy checks của vehicle
     * Example:
     * GET /api/vehicle-checks/vehicle/{vehicleId}
     */
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<VehicleCheck>> getChecksByVehicleId(@PathVariable Long vehicleId) {
        List<VehicleCheck> checks = vehicleCheckService.getChecksByVehicleId(vehicleId);
        return ResponseEntity.ok(checks);
    }

    /**
     * Lấy checks theo status
     * Example:
     * GET /api/vehicle-checks/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<VehicleCheck>> getChecksByStatus(@PathVariable String status) {
        List<VehicleCheck> checks = vehicleCheckService.getChecksByStatus(status);
        return ResponseEntity.ok(checks);
    }

    /**
     * Kiểm tra user đã làm check chưa
     * Example:
     * GET /api/vehicle-checks/has-check/{bookingId}?checkType=PRE_USE
     */
    @GetMapping("/has-check/{bookingId}")
    public ResponseEntity<Boolean> hasCheck(
            @PathVariable Long bookingId,
            @RequestParam String checkType) {

        Boolean hasCheck = vehicleCheckService.hasCheck(bookingId, checkType);
        return ResponseEntity.ok(hasCheck);
    }

    /**
     * QR Code Check-in endpoint - Quét QR để tìm booking active của user
     * Example:
     * POST /api/vehicle-checks/qr-checkin
     */
    @PostMapping("/qr-checkin")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> qrCheckIn(@RequestParam String qrCode) {
        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> result = vehicleCheckService.processQrCheckIn(qrCode, currentUser.getUserId());
        return ResponseEntity.ok(result);
    }
}
