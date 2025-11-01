package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.BookingResponseDTO;
import com.group8.evcoownership.dto.CancelBookingRequestDTO;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.UsageBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Usage Bookings", description = "Quản lý đặt xe và sử dụng phương tiện")
public class UsageBookingController {

    private final UsageBookingService usageBookingService;
    private final UserRepository userRepository;

    /**
     * Lấy danh sách booking của user (có thể lọc theo tuần hoặc lấy tất cả upcoming)
     * Example:
     * GET /api/bookings/user-bookings?userId=1
     * GET /api/bookings/user-bookings?userId=1&weekStart=2025-10-06T00:00:00
     */
    @GetMapping("/user-bookings")
    @Operation(summary = "Đặt xe của tôi", description = "Lấy danh sách booking của người dùng, có thể lọc theo tuần hoặc lấy tất cả sắp tới")
    public ResponseEntity<?> getUserBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal String email
    ) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow().getUserId();

        List<UsageBooking> bookings;

        if (weekStart != null) {
            // Convert LocalDate to LocalDateTime at start of day
            LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
            bookings = usageBookingService.getBookingsByUserInWeek(userId, weekStartDateTime);
        } else {
            bookings = usageBookingService.getUpcomingBookings(userId);
        }

        // Nếu không có booking, trả về message
        if (bookings.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No bookings found for this period");
            response.put("bookings", List.of());
            return ResponseEntity.ok(response);
        }

        List<BookingResponseDTO> response = bookings.stream()
                .map(b -> new BookingResponseDTO(
                        b.getId(),
                        b.getVehicle().getLicensePlate(),
                        b.getVehicle().getBrand(),
                        b.getVehicle().getModel(),
                        b.getStartDateTime(),
                        b.getEndDateTime(),
                        b.getStatus().name()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Xác nhận booking (chuyển từ Pending → Confirmed)
     * Example:
     * PUT /api/bookings/1/confirm
     */
    @PutMapping("/{bookingId}/confirm")
    @Operation(summary = "Xác nhận đặt xe", description = "Xác nhận booking từ trạng thái Pending sang Confirmed")
    public ResponseEntity<BookingResponseDTO> confirmBooking(@PathVariable Long bookingId) {
        UsageBooking booking = usageBookingService.confirmBooking(bookingId);

        var vehicle = booking.getVehicle();
        BookingResponseDTO response = new BookingResponseDTO(
                booking.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getStatus().name()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Hủy booking (chuyển sang Cancelled)
     * Example:
     * PUT /api/bookings/1/cancel
     */
    @PutMapping("/{bookingId}/cancel")
    @Operation(summary = "Hủy đặt xe", description = "Hủy booking và chuyển sang trạng thái Cancelled")
    public ResponseEntity<BookingResponseDTO> cancelBooking(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal String email) {

        Long userId = userRepository.findByEmail(email)
                .orElseThrow().getUserId();

        UsageBooking booking = usageBookingService.cancelBooking(bookingId, userId);

        var vehicle = booking.getVehicle();
        BookingResponseDTO response = new BookingResponseDTO(
                booking.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getStatus().name()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Hủy booking với lý do (dành cho admin/kỹ thuật viên)
     * Example:
     * PUT /api/bookings/1/cancel-with-reason
     */
    @PutMapping("/{bookingId}/cancel-with-reason")
    @Operation(summary = "Hủy đặt xe có lý do", description = "Hủy booking với lý do cụ thể (dành cho admin/kỹ thuật viên)")
    public ResponseEntity<Map<String, Object>> cancelBookingWithReason(
            @PathVariable Long bookingId,
            @RequestBody CancelBookingRequestDTO request) {
        Map<String, Object> result = usageBookingService.cancelBookingWithReason(bookingId, request);
        return ResponseEntity.ok(result);
    }

    // Removed to avoid duplication. Use MaintenanceController under /api/maintenance instead.

    /**
     * Hoàn thành booking và tạo buffer time (chuyển sang Completed + tạo Buffer)
     * Example:
     * PUT /api/bookings/1/complete
     */
    @PutMapping("/{bookingId}/complete")
    @Operation(summary = "Hoàn thành đặt xe", description = "Hoàn thành booking và tự động tạo buffer time cho kiểm tra kỹ thuật và sạc")
    public ResponseEntity<Map<String, Object>> completeBooking(@PathVariable Long bookingId) {
        UsageBooking booking = usageBookingService.completeBooking(bookingId);

        var vehicle = booking.getVehicle();
        BookingResponseDTO bookingResponse = new BookingResponseDTO(
                booking.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getStatus().name()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("completedBooking", bookingResponse);
        result.put("message", "Booking completed.");
        return ResponseEntity.ok(result);
    }
}
