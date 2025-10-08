package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UsageBookingService {

    private final UsageBookingRepository usageBookingRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    //Tạo booking mới — kiểm tra quota, trùng giờ, buffer.
    public UsageBooking createBooking(Long userId, Long vehicleId, LocalDateTime start, LocalDateTime end) {
        // Kiểm tra tồn tại user & vehicle
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // Tính quota (ví dụ: 20h/tuần)
        LocalDateTime weekStart = start.with(DayOfWeek.MONDAY).with(LocalTime.MIN);
        long bookedHours = usageBookingRepository.getTotalBookedHoursThisWeek(userId, vehicleId, weekStart);
        long newBookingHours = Duration.between(start, end).toHours();
        long quotaLimit = 20;

        if (bookedHours + newBookingHours > quotaLimit) {
            throw new IllegalStateException("Weekly quota exceeded for this vehicle.");
        }

        // Kiểm tra trùng giờ + buffer 1h
        long conflicts = usageBookingRepository.countOverlappingBookingsWithBuffer(vehicleId, start, end);
        if (conflicts > 0) {
            throw new IllegalStateException("Time slot not available. Please choose another period.");
        }

        // Tạo booking mới
        UsageBooking booking = new UsageBooking();
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setStartDateTime(start);
        booking.setEndDateTime(end);
        booking.setStatus(BookingStatus.Pending);

        return usageBookingRepository.save(booking);
    }

    //Lấy danh sách slot đã đặt trong ngày (để tạo lịch trống)
    public List<UsageBooking> getBookingsByVehicleAndDate(Long vehicleId, LocalDate date) {
        return usageBookingRepository.findByVehicleIdAndDate(vehicleId, date);
    }

    //Lấy các booking sắp tới của user
    public List<UsageBooking> getUpcomingBookings(Long userId) {
        return usageBookingRepository.findUpcomingBookingsByUser(userId);
    }

    //Lấy booking gần nhất đã hoàn thành
    public UsageBooking getLastCompletedBooking(Long vehicleId) {
        return usageBookingRepository.findTop1ByVehicleIdAndStatusOrderByEndDateTimeDesc(vehicleId, BookingStatus.Completed)
                .stream().findFirst().orElse(null);
    }
}

