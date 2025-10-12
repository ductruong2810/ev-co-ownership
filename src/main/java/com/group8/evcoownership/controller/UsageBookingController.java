package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.service.UsageBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class UsageBookingController {

    private final UsageBookingService usageBookingService;

    /**
     * Tạo booking mới
     * Example:
     * POST /api/bookings/create?userId=1&vehicleId=2&start=2025-10-10T08:00&end=2025-10-10T12:00
     */
    @PostMapping("/create")
    public ResponseEntity<BookingResponseDTO> createBooking(@RequestBody BookingRequestDTO dto) {
        UsageBooking booking = usageBookingService.createBooking(
                dto.getUserId(), dto.getVehicleId(), dto.getStartDateTime(), dto.getEndDateTime()
        );

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
     * Lấy các slot đã đặt của xe trong ngày với thông tin user
     * Example:
     * GET /api/bookings/slots?vehicleId=2&date=2025-10-10
     */
    @GetMapping("/slots")
    public ResponseEntity<List<BookingSlotWithUserDTO>> getVehicleBookingsForDay(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<UsageBooking> bookings = usageBookingService.getBookingsByVehicleAndDate(vehicleId, date);
        List<BookingSlotWithUserDTO> slots = bookings.stream()
                .map(b -> new BookingSlotWithUserDTO(
                        b.getId(),
                        b.getUser().getFullName(),
                        b.getUser().getEmail(),
                        b.getStartDateTime(),
                        b.getEndDateTime(),
                        b.getStatus().name()
                ))
                .toList();

        return ResponseEntity.ok(slots);
    }

    /**
     * Lấy danh sách booking của user (có thể lọc theo tuần hoặc lấy tất cả upcoming)
     * Example:
     * GET /api/bookings/user-bookings?userId=1
     * GET /api/bookings/user-bookings?userId=1&weekStart=2025-10-06T00:00:00
     */
    @GetMapping("/user-bookings")
    public ResponseEntity<List<BookingResponseDTO>> getUserBookings(
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime weekStart
    ) {
        List<UsageBooking> bookings;

        if (weekStart != null) {
            // Lấy booking trong tuần cụ thể
            bookings = usageBookingService.getBookingsByUserInWeek(userId, weekStart);
        } else {
            // Lấy booking sắp tới
            bookings = usageBookingService.getUpcomingBookings(userId);
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
     * Lấy quota còn lại của user cho xe trong tuần
     * Example:
     * GET /api/bookings/quota?userId=1&vehicleId=2&weekStart=2025-10-06T00:00:00
     */
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getUserQuotaForWeek(
            @RequestParam Long userId,
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime weekStart
    ) {
        Map<String, Object> quotaInfo = usageBookingService.getUserQuotaInfo(userId, vehicleId, weekStart);
        return ResponseEntity.ok(quotaInfo);
    }

    /**
     * Xác nhận booking (chuyển từ Pending → Confirmed)
     * Example:
     * PUT /api/bookings/1/confirm
     */
    @PutMapping("/{bookingId}/confirm")
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
    public ResponseEntity<BookingResponseDTO> cancelBooking(@PathVariable Long bookingId) {
        UsageBooking booking = usageBookingService.cancelBooking(bookingId);

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
    public ResponseEntity<Map<String, Object>> cancelBookingWithReason(
            @PathVariable Long bookingId,
            @RequestBody CancelBookingRequestDTO request) {
        Map<String, Object> result = usageBookingService.cancelBookingWithReason(bookingId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Tạo maintenance booking và cancel các booking bị ảnh hưởng
     * Example:
     * POST /api/bookings/maintenance
     */
    @PostMapping("/maintenance")
    public ResponseEntity<Map<String, Object>> createMaintenanceBooking(
            @RequestBody MaintenanceBookingRequestDTO request) {
        Map<String, Object> result = usageBookingService.createMaintenanceBooking(request);
        return ResponseEntity.ok(result);
    }

    /**
     * Hoàn thành booking và tạo buffer time (chuyển sang Completed + tạo Buffer)
     * Example:
     * PUT /api/bookings/1/complete
     */
    @PutMapping("/{bookingId}/complete")
    public ResponseEntity<Map<String, Object>> completeBooking(@PathVariable Long bookingId) {
        UsageBooking booking = usageBookingService.completeBooking(bookingId);
        UsageBooking bufferBooking = usageBookingService.createBufferBooking(bookingId);

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

        BookingResponseDTO bufferResponse = new BookingResponseDTO(
                bufferBooking.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                bufferBooking.getStartDateTime(),
                bufferBooking.getEndDateTime(),
                bufferBooking.getStatus().name()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("completedBooking", bookingResponse);
        result.put("bufferBooking", bufferResponse);
        result.put("message", "Booking completed. 1-hour buffer period created for technical inspection and charging.");

        return ResponseEntity.ok(result);
    }
}
