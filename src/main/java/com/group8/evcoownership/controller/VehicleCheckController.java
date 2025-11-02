package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.VehicleCheckRequestDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.VehicleCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Vehicle Checks", description = "Quản lý kiểm tra phương tiện")
public class VehicleCheckController {

    private final VehicleCheckService vehicleCheckService;
    private final UserRepository userRepository;

    /**
     * User tạo pre-use check với JWT authentication
     * Example:
     * POST /api/vehicle-checks/pre-use
     */
    @PostMapping("/pre-use")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Kiểm tra trước sử dụng", description = "Người dùng tạo báo cáo kiểm tra phương tiện trước khi sử dụng")
    public ResponseEntity<VehicleCheck> createPreUseCheck(@Valid @RequestBody VehicleCheckRequestDTO request) {
        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VehicleCheck check = vehicleCheckService.createPreUseCheck(
                request.getBookingId(),
                currentUser.getUserId(),
                request.getOdometer(),
                request.getBatteryLevel(),
                request.getCleanliness(),
                request.getNotes(),
                request.getIssues()
        );

        return ResponseEntity.ok(check);
    }

    /**
     * User tạo post-use check với JWT authentication
     * Example:
     * POST /api/vehicle-checks/post-use
     */
    @PostMapping("/post-use")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Kiểm tra sau sử dụng", description = "Người dùng tạo báo cáo kiểm tra phương tiện sau khi sử dụng")
    public ResponseEntity<VehicleCheck> createPostUseCheck(@Valid @RequestBody VehicleCheckRequestDTO request) {
        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        VehicleCheck check = vehicleCheckService.createPostUseCheck(
                request.getBookingId(),
                currentUser.getUserId(),
                request.getOdometer(),
                request.getBatteryLevel(),
                request.getCleanliness(),
                request.getNotes(),
                request.getIssues()
        );

        return ResponseEntity.ok(check);
    }

    /**
     * User từ chối xe với JWT authentication
     * Example:
     * POST /api/vehicle-checks/reject
     */
    @PostMapping("/reject")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Từ chối phương tiện", description = "Người dùng từ chối sử dụng phương tiện do có vấn đề")
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
    @Operation(summary = "Cập nhật trạng thái kiểm tra", description = "Kỹ thuật viên phê duyệt hoặc từ chối báo cáo kiểm tra")
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
    @Operation(summary = "Kiểm tra theo booking", description = "Lấy danh sách tất cả kiểm tra của một booking")
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
    @Operation(summary = "Kiểm tra theo phương tiện", description = "Lấy danh sách tất cả kiểm tra của một phương tiện")
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
    @Operation(summary = "Kiểm tra theo trạng thái", description = "Lấy danh sách kiểm tra theo trạng thái cụ thể")
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
    @Operation(summary = "Kiểm tra đã thực hiện", description = "Kiểm tra xem người dùng đã thực hiện kiểm tra loại nào chưa")
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
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Check-in bằng QR code", description = "Quét QR code để check-in và tìm booking đang hoạt động")
    public ResponseEntity<Map<String, Object>> qrCheckIn(@RequestParam String qrCode) {
        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> result = vehicleCheckService.processQrCheckIn(qrCode, currentUser.getUserId());
        return ResponseEntity.ok(result);
    }
}
