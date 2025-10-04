package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Booking;
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
    public Booking createBooking(User user, Vehicle vehicle,
                                 LocalDateTime start, LocalDateTime end) {

        if (!bookingRepository.findOverlaps(vehicle, start, end).isEmpty()) {
            throw new RuntimeException("Time slot not available!");
        }

        Booking booking = Booking.builder()
                .user(user)
                .vehicle(vehicle)
                .startDateTime(start)
                .endDateTime(end)
                .status(BookingStatus.Pending)
                .build();
        bookingRepository.save(booking);

        Booking buffer = Booking.builder()
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
    public Booking confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));
        booking.setStatus(BookingStatus.Confirmed);
        return bookingRepository.save(booking);
    }
}
