package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.BookingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final BookingRepository bookingRepository;

    @Transactional
    public UsageBooking createBooking(User user, Vehicle vehicle,
                                 LocalDateTime start, LocalDateTime end) {

        if (!bookingRepository.findOverlaps(vehicle, start, end).isEmpty()) {
            throw new RuntimeException("Time slot not available!");
        }

        UsageBooking booking = UsageBooking.builder()
                .user(user)
                .vehicle(vehicle)
                .startDateTime(start)
                .endDateTime(end)
                .status(BookingStatus.Pending)
                .build();
        bookingRepository.save(booking);

        UsageBooking buffer = UsageBooking.builder()
                .user(user)
                .vehicle(vehicle)
                .startDateTime(end)
                .endDateTime(end.plusHours(1))
                .status(BookingStatus.Buffer)
                .build();
        bookingRepository.save(buffer);

        return booking;
    }

    @Transactional
    public UsageBooking confirmBooking(Long bookingId) {
        UsageBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        booking.setStatus(BookingStatus.Confirmed);
        return bookingRepository.save(booking);
    }
}
