package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.BookingDetailResponseDTO;
import com.group8.evcoownership.dto.BookingResponseDTO;
import com.group8.evcoownership.dto.CancelBookingRequestDTO;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.UsageBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
     * Lấy danh sách booking của co-owner theo nhóm (có thể lọc theo tuần hoặc lấy tất cả upcoming)
     * Example:
     * GET /api/bookings/user-bookings?groupId=1
     * GET /api/bookings/user-bookings?groupId=1&weekStart=2025-10-06
     */
    @GetMapping("/user-bookings")
    @Operation(summary = "Đặt xe của tôi", description = "Lấy danh sách booking của người dùng theo nhóm, có thể lọc theo tuần")
    public ResponseEntity<?> getUserBookings(
            @RequestParam Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal String email
    ) {
        Long userId = getUserIdByEmail(email);

        List<UsageBooking> bookings;

        // Luôn lấy theo groupId
        bookings = usageBookingService.getUpcomingBookingsByGroup(userId, groupId);

        // Nếu không có booking, trả về message
        if (bookings.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No bookings found for this group and period");
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
                        b.getStatus().name(),
                        b.getQrCodeCheckin(),    // Thêm
                        b.getQrCodeCheckout(),   // Thêm
                        b.getCreatedAt()         // Thêm
                ))
                .toList();


        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/search")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Danh sách booking theo user và group", description = "Dành cho STAFF/ADMIN xem danh sách booking của một user trong một group")
    public ResponseEntity<Page<BookingResponseDTO>> getBookingsForStaff(
            @RequestParam Long userId,
            @RequestParam Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        Page<BookingResponseDTO> bookings = usageBookingService.getBookingsForStaff(userId, groupId, PageRequest.of(page, size));
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/admin/{bookingId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Chi tiết booking (bao gồm QR)", description = "Dành cho STAFF/ADMIN xem chi tiết một booking, bao gồm QR code")
    public ResponseEntity<BookingDetailResponseDTO> getBookingDetailForStaff(@PathVariable Long bookingId) {
        BookingDetailResponseDTO detail = usageBookingService.getBookingDetailForStaff(bookingId);
        return ResponseEntity.ok(detail);
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

        Long userId = getUserIdByEmail(email);

        UsageBooking booking = usageBookingService.cancelBooking(bookingId, userId);

        var vehicle = booking.getVehicle();
        BookingResponseDTO response = new BookingResponseDTO(
                booking.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getStatus().name(),
                booking.getQrCodeCheckin(),    // Thêm
                booking.getQrCodeCheckout(),   // Thêm
                booking.getCreatedAt()         // Thêm
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
    private Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email))
                .getUserId();
    }
}
