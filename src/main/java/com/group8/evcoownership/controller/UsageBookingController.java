package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.service.UsageBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    public ResponseEntity<UsageBooking> createBooking(
            @RequestParam Long userId,
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        UsageBooking booking = usageBookingService.createBooking(userId, vehicleId, start, end);
        return ResponseEntity.ok(booking);
    }

    /**
     * Lấy các slot đã đặt của xe trong ngày
     * Example:
     * GET /api/bookings/slots?vehicleId=2&date=2025-10-10
     */
    @GetMapping("/slots")
    public ResponseEntity<List<UsageBooking>> getVehicleBookingsForDay(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(usageBookingService.getBookingsByVehicleAndDate(vehicleId, date));
    }

    /**
     * Lấy các booking sắp tới của người dùng
     * Example:
     * GET /api/bookings/upcoming?userId=1
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<UsageBooking>> getUpcomingBookings(
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(usageBookingService.getUpcomingBookings(userId));
    }

    /**
     * Lấy booking gần nhất đã hoàn thành (để biết xe sẵn sàng chưa)
     * Example:
     * GET /api/bookings/last-completed?vehicleId=2
     */
    @GetMapping("/last-completed")
    public ResponseEntity<UsageBooking> getLastCompletedBooking(
            @RequestParam Long vehicleId
    ) {
        return ResponseEntity.ok(usageBookingService.getLastCompletedBooking(vehicleId));
    }
}
