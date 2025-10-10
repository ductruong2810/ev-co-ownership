package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.BookingRequestDTO;
import com.group8.evcoownership.dto.BookingResponseDTO;
import com.group8.evcoownership.dto.BookingSlotDTO;
import com.group8.evcoownership.dto.BookingSummaryDTO;
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
     * Lấy các slot đã đặt của xe trong ngày
     * Example:
     * GET /api/bookings/slots?vehicleId=2&date=2025-10-10
     */
    @GetMapping("/slots")
    public ResponseEntity<List<BookingSlotDTO>> getVehicleBookingsForDay(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        List<UsageBooking> bookings = usageBookingService.getBookingsByVehicleAndDate(vehicleId, date);
        List<BookingSlotDTO> slots = bookings.stream()
                .map(b -> new BookingSlotDTO(b.getStartDateTime(), b.getEndDateTime()))
                .toList();

        return ResponseEntity.ok(slots);
    }

    /**
     * Lấy các booking sắp tới của người dùng
     * Example:
     * GET /api/bookings/upcoming?userId=1
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<BookingSummaryDTO>> getUpcomingBookings(@RequestParam Long userId) {
        List<UsageBooking> bookings = usageBookingService.getUpcomingBookings(userId);
        List<BookingSummaryDTO> result = bookings.stream()
                .map(b -> new BookingSummaryDTO(
                        b.getId(),
                        b.getVehicle().getLicensePlate(),
                        b.getVehicle().getBrand(),
                        b.getVehicle().getModel(),
                        b.getStartDateTime(),
                        b.getEndDateTime(),
                        b.getStatus().name()
                ))
                .toList();

        return ResponseEntity.ok(result);
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

    /**
     * Lấy danh sách các booking của user trong tuần tương ứng với weekStart
     * Example:
     * GET /api/bookings/week?userId=1&weekStart=2025-10-06T00:00:00
     */
    @GetMapping("/week")
    public ResponseEntity<List<BookingResponseDTO>> getUserBookingsByWeek(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime weekStart
    ) {
        List<UsageBooking> bookings = usageBookingService.getBookingsByUserInWeek(userId, weekStart);

        // map entity → DTO
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
}
