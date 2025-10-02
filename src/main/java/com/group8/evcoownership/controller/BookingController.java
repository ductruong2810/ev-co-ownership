package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.Booking;
import com.group8.evcoownership.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

//    @PostMapping
//    public BookingResponseDTO createBooking(@RequestBody BookingRequestDTO dto) {
//        User user = new User(); user.setUserId(dto.getUserId());
//        Vehicle vehicle = new Vehicle(); vehicle.setVehicleId(dto.getVehicleId());
//
//        Booking booking = bookingService.createBooking(
//                user, vehicle,
//                LocalDateTime.parse(dto.getStart()),
//                LocalDateTime.parse(dto.getEnd())
//        );
//
//        return BookingResponseDTO.builder()
//                .bookingId(booking.getBookingId())
//                .userId(user.getUserId())
//                .vehicleId(vehicle.getVehicleId())
//                .startDateTime(booking.getStartDateTime().toString())
//                .endDateTime(booking.getEndDateTime().toString())
//                .status(booking.getStatus().name())
//                .build();
//    }

    @PutMapping("/{id}/confirm")
    public Booking confirmBooking(@PathVariable Long id) {
        return bookingService.confirmBooking(id);
    }
}
