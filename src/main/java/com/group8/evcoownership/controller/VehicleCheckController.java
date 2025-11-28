package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.VehicleCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicle-checks")
@RequiredArgsConstructor
@Tag(name = "Vehicle Checks", description = "Quản lý kiểm tra phương tiện")
public class VehicleCheckController {

    private final VehicleCheckService vehicleCheckService;
    private final UserRepository userRepository;
    @Value("${frontend.base.url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * User từ chối xe với JWT authentication
     * Example:
     * POST /api/vehicle-checks/reject
     */
    @PostMapping("/reject")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Từ chối phương tiện", description = "Người dùng từ chối sử dụng phương tiện do có vấn đề")
    public ResponseEntity<VehicleCheck> rejectVehicle(
            @RequestParam Long bookingId,
            @RequestParam String issues,
            @RequestParam(required = false) String notes) {

        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + userEmail));

        VehicleCheck check = vehicleCheckService.rejectVehicle(bookingId, currentUser.getUserId(), issues, notes);
        return ResponseEntity.ok(check);
    }

    /**
     *
     * khi user dem xe ve , tao report vehicle
     * Technician approve/reject check report vehicle
     * Example:
     * PUT /api/vehicle-checks/{checkId}/status
     */
    @PutMapping("/{checkId}/status")
    public ResponseEntity<VehicleCheck> updateCheckStatus(
            @PathVariable Long checkId,
            @RequestBody @Valid UpdateCheckStatusRequestDTO request) {
        VehicleCheck check =
                vehicleCheckService.updateCheckStatus(checkId, request.status(), request.notes(), request.issues());
        return ResponseEntity.ok(check);
    }

    /**
     * Lấy checks của booking
     * Example:
     * GET /api/vehicle-checks/booking/{bookingId}
     */
    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Kiểm tra theo booking", description = "Lấy danh sách tất cả kiểm tra của một booking")
    @PreAuthorize("hasAnyRole('TECHNICIAN','STAFF','ADMIN','CO_OWNER')")
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
    @PreAuthorize("hasAnyRole('TECHNICIAN','STAFF','ADMIN')")
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
    @PreAuthorize("hasAnyRole('TECHNICIAN','STAFF','ADMIN')")
    public ResponseEntity<List<VehicleCheck>> getChecksByStatus(@PathVariable String status) {
        List<VehicleCheck> checks = vehicleCheckService.getChecksByStatus(status);
        return ResponseEntity.ok(checks);
    }

    // lay all vehicle check
    // tra ra bookingId
    // khong lay ra object UsageBooking
//    @GetMapping
//    public ResponseEntity<List<VehicleCheck>> getAllChecks() {
//        Page<VehicleCheck> page = vehicleCheckService.getAllChecks(
//                PageRequest.of(0, 100)
//        );
//        // JSON trả về mỗi item:
//        // { "id": ..., "bookingId": 123, ... }  // KHÔNG có "booking": { ... }
//        return ResponseEntity.ok(page.getContent());
//    }

    @GetMapping
    @Operation(summary = "Get all vehicle checks",
            description = "Technician lấy ra tất cả vehicle check (page 0, size 100 mặc định).")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    public ResponseEntity<List<VehicleCheckResponseDTO>> getAllChecks() {
        Page<VehicleCheckResponseDTO> page = vehicleCheckService.getAllChecks(
                PageRequest.of(0, 100)
        );
        return ResponseEntity.ok(page.getContent());
    }


    /**
     * Kiểm tra user đã làm check chưa
     * Example:
     * GET /api/vehicle-checks/has-check/{bookingId}?checkType=PRE_USE
     */
    @GetMapping("/has-check/{bookingId}")
    @Operation(summary = "Kiểm tra đã thực hiện", description = "Kiểm tra xem người dùng đã thực hiện kiểm tra loại nào chưa")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Boolean> hasCheck(
            @PathVariable Long bookingId,
            @RequestParam String checkType) {

        Boolean hasCheck = vehicleCheckService.hasCheck(bookingId, checkType);
        return ResponseEntity.ok(hasCheck);
    }

    /**
     * Unified QR Scan endpoint - handles both check-in and checkout QR payloads
     * Example:
     * POST /api/vehicle-checks/qr-scan
     */
    @PostMapping("/qr-scan")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Quét QR code", description = "Quét QR code để xác định hành động (check-in/check-out)")
    public ResponseEntity<Map<String, Object>> qrScan(@Valid @RequestBody QrScanRequestDTO request) {
        // Lấy user từ JWT token
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + userEmail));

        Map<String, Object> result = vehicleCheckService.processQrScan(request.qrCode(), currentUser.getUserId());
        boolean success = Boolean.TRUE.equals(result.get("success"));
        result.put("status", success ? "success" : "fail");

        Long groupId = result.get("groupId") instanceof Number number ? number.longValue() : null;
        String message = result.getOrDefault("message", "").toString();
        Long bookingId = result.get("bookingId") instanceof Number bk ? bk.longValue() : null;

        if (!result.containsKey("redirectUrl")) {
            String responseType = result.getOrDefault("responseType", "CHECKIN").toString();
            boolean isCheckoutFlow = "CHECKOUT".equalsIgnoreCase(responseType)
                    || "CHECKOUT_FORM".equalsIgnoreCase(responseType)
                    || "TECH_REVIEW".equalsIgnoreCase(responseType)
                    || "COMPLETED".equalsIgnoreCase(responseType);

            String resultPath = isCheckoutFlow ? "checkout-result" : "checkin-result";

            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String bookingQuery = bookingId != null ? "&bookingId=" + bookingId : "";
            if (groupId != null) {
                String basePath = String.format("%s/dashboard/viewGroups/%d/%s", frontendBaseUrl, groupId, resultPath);
                String messageQuery = encodedMessage.isEmpty() ? "" : "&message=" + encodedMessage;
                result.put("redirectUrl", String.format("%s?status=%s%s%s", basePath,
                        success ? "success" : "fail", bookingQuery, messageQuery));
            } else {
                result.put("redirectUrl", String.format("%s/%s?status=%s%s&message=%s",
                        frontendBaseUrl,
                        resultPath,
                        success ? "success" : "fail",
                        bookingQuery,
                        encodedMessage));
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Confirm check-in with signature
     * Example:
     * POST /api/vehicle-checks/checkin/confirm
     */
    @PostMapping("/checkin/confirm")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Xác nhận check-in với chữ ký", description = "Xác nhận check-in và lưu chữ ký số")
    public ResponseEntity<Map<String, Object>> confirmCheckIn(@Valid @RequestBody QrCheckInRequestDTO request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + userEmail));

        Map<String, Object> result = vehicleCheckService.confirmCheckInWithSignature(
                request.qrCode(),
                request.signature(),
                currentUser.getUserId()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Checkout form submission - lưu kết quả kiểm tra sau sử dụng và chuyển trạng thái booking
     */
    @PostMapping("/checkout/submit")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(summary = "Check-out bằng QR code", description = "Quét QR code để check-out, cập nhật tình trạng xe và đóng booking")
    public ResponseEntity<Map<String, Object>> submitCheckoutForm(@Valid @RequestBody QrCheckOutRequestDTO request) {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + userEmail));

        Map<String, Object> result = vehicleCheckService.submitCheckoutForm(request, currentUser.getUserId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/booking/{bookingId}/post-use")
    @Operation(summary = "Lấy kiểm tra POST_USE", description = "Lấy kiểm tra POST_USE cho booking")
    public ResponseEntity<VehicleCheck> getLatestPostUse(@PathVariable Long bookingId) {
        VehicleCheck check = vehicleCheckService
                .findByBookingAndType(bookingId, "POST_USE")
                .orElseThrow(() -> new ResourceNotFoundException("No POST_USE check"));
        return ResponseEntity.ok(check);
    }

}
