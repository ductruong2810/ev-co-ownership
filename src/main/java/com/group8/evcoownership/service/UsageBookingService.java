package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.CancelBookingRequestDTO;
import com.group8.evcoownership.dto.MaintenanceBookingRequestDTO;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.exception.InvalidBookingException;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class UsageBookingService {

    private final UsageBookingRepository usageBookingRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;

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

        // Tính quota dựa trên ownership percentage (168h/tuần * ownership%)
        LocalDateTime weekStart = start.with(DayOfWeek.MONDAY).with(LocalTime.MIN);
        long bookedHours = usageBookingRepository.getTotalBookedHoursThisWeek(userId, vehicleId, weekStart);
        long newBookingHours = Duration.between(start, end).toHours();

        // Lấy quota limit dựa trên ownership percentage
        Long quotaLimit = usageBookingRepository.getQuotaLimitByOwnershipPercentage(userId, vehicleId);
        if (quotaLimit == null) {
            throw new IllegalStateException("User is not a member of the vehicle's ownership group.");
        }

        if (bookedHours + newBookingHours > quotaLimit) {
            long remainingHours = quotaLimit - bookedHours;
            throw new IllegalStateException(String.format(
                    "Weekly quota exceeded. You have used %d/%d hours this week. You can only book %d more hours based on your ownership percentage.",
                    bookedHours, quotaLimit, Math.max(0, remainingHours)));
        }

        // Tạo booking mới
        UsageBooking booking = new UsageBooking();
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setStartDateTime(start);
        booking.setEndDateTime(end);
        booking.setStatus(BookingStatus.PENDING);

        UsageBooking savedBooking = usageBookingRepository.save(booking);

        // Send notification
        notificationOrchestrator.sendBookingNotification(
                userId,
                NotificationType.BOOKING_CREATED,
                "Booking Created",
                String.format("You have successfully booked %s %s from %s to %s",
                        vehicle.getBrand(), vehicle.getModel(), start, end),
                savedBooking.getId()
        );

        return savedBooking;
    }

    //Lấy danh sách slot đã đặt trong ngày với thông tin user (để tạo lịch trống)
    public List<UsageBooking> getBookingsByVehicleAndDate(Long vehicleId, LocalDate date) {
        return usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, date);
    }

    //Lấy các booking sắp tới của user
    //Lấy các booking sắp tới của user trong tuần hiện tại
    public List<UsageBooking> getUpcomingBookings(Long userId) {
        // Tính weekStart (đầu tuần hiện tại - Thứ 2 00:00:00)
        LocalDateTime weekStart = LocalDateTime.now()
                .with(DayOfWeek.MONDAY)
                .withHour(0)
                .withMinute(0)
                .withSecond(0);

        // Tính weekEnd (7 ngày sau weekStart)
        LocalDateTime weekEnd = weekStart.plusWeeks(1);

        // Gọi repository với 3 tham số
        return usageBookingRepository.findBookingsByUserInWeek(userId, weekStart, weekEnd);
    }



    //Xác nhận booking (Pending → Confirmed) - kiểm tra conflict trước khi confirm
    public UsageBooking confirmBooking(Long bookingId) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only pending bookings can be confirmed");
        }

        // Kiểm tra conflict với buffer time trước khi confirm (loại trừ booking hiện tại)
        long conflicts = usageBookingRepository.countOverlappingBookingsWithBufferExcluding(
                booking.getVehicle().getId(),
                booking.getId(),
                booking.getStartDateTime(),
                booking.getEndDateTime()
        );

        if (conflicts > 0) {
            throw new IllegalStateException("Cannot confirm booking. Time slot conflicts with existing bookings or buffer periods. Please choose another time slot.");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        UsageBooking savedBooking = usageBookingRepository.save(booking);

        // Send booking status notification
        notificationOrchestrator.sendBookingNotification(
                booking.getUser().getUserId(),
                NotificationType.BOOKING_CREATED,
                "Booking Confirmed",
                String.format("Your booking for %s %s has been confirmed",
                        booking.getVehicle().getBrand(), booking.getVehicle().getModel()),
                savedBooking.getId()
        );

        return savedBooking;
    }

    //Hủy booking (bất kỳ status nào → Cancelled)
    public UsageBooking cancelBooking(Long bookingId, Long userId) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed bookings");
        }

        if (booking.getUser() == null || !booking.getUser().getUserId().equals(userId)) {
            throw new IllegalStateException("You can only cancel your own bookings");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return usageBookingRepository.save(booking);
    }

    //Hoàn thành booking (Confirmed → Completed)
    public UsageBooking completeBooking(Long bookingId) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be completed");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        UsageBooking savedBooking = usageBookingRepository.save(booking);

        // Send booking completed notification (reuse BOOKING_CREATED type for completed event)
        notificationOrchestrator.sendBookingNotification(
                booking.getUser().getUserId(),
                NotificationType.BOOKING_CREATED,
                "Booking Completed",
                String.format("Your booking for %s %s has been completed",
                        booking.getVehicle().getBrand(), booking.getVehicle().getModel()),
                savedBooking.getId()
        );

        return savedBooking;
    }

    //Hủy booking với lý do (dành cho admin/kỹ thuật viên)
    public Map<String, Object> cancelBookingWithReason(Long bookingId, CancelBookingRequestDTO request) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed bookings");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        usageBookingRepository.save(booking);

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("status", "Cancelled");
        result.put("reason", request.getReason());
        result.put("message", "Booking cancelled due to: " + request.getReason());

        // Gửi notification cho user nếu request.notifyUsers = true
        if (request.isNotifyUsers()) {
            notificationService.sendNotification(
                    booking.getUser(),
                    "Booking Cancelled",
                    "Your booking has been cancelled due to: " + request.getReason(),
                    "SYSTEM"
            );
            result.put("notificationSent", true);
            result.put("notificationMessage", "Your booking has been cancelled due to: " + request.getReason());
        }

        return result;
    }

    //Tạo maintenance booking và cancel các booking bị ảnh hưởng
    public Map<String, Object> createMaintenanceBooking(MaintenanceBookingRequestDTO request) {
        var vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        // Tạo maintenance booking
        UsageBooking maintenanceBooking = new UsageBooking();
        maintenanceBooking.setUser(null); // System booking
        maintenanceBooking.setVehicle(vehicle);
        maintenanceBooking.setStartDateTime(request.getStartDateTime());
        maintenanceBooking.setEndDateTime(request.getEndDateTime());
        maintenanceBooking.setStatus(BookingStatus.BUFFER); // Sử dụng Buffer status cho maintenance

        UsageBooking savedMaintenance = usageBookingRepository.save(maintenanceBooking);

        Map<String, Object> result = new HashMap<>();
        result.put("maintenanceBooking", savedMaintenance);
        result.put("reason", request.getReason());

        if (request.isCancelAffectedBookings()) {
            // Tìm và cancel các booking bị ảnh hưởng
            List<UsageBooking> affectedBookings = usageBookingRepository.findAffectedBookings(
                    request.getVehicleId(),
                    request.getStartDateTime(),
                    request.getEndDateTime()
            );

            List<Map<String, Object>> cancelledBookings = new ArrayList<>();
            for (UsageBooking booking : affectedBookings) {
                if (booking.getStatus() != BookingStatus.COMPLETED) {
                    booking.setStatus(BookingStatus.CANCELLED);
                    usageBookingRepository.save(booking);

                    Map<String, Object> cancelledBooking = new HashMap<>();
                    cancelledBooking.put("bookingId", booking.getId());
                    cancelledBooking.put("userId", booking.getUser().getUserId());
                    cancelledBooking.put("userName", booking.getUser().getFullName());
                    cancelledBooking.put("startTime", booking.getStartDateTime());
                    cancelledBooking.put("endTime", booking.getEndDateTime());
                    cancelledBooking.put("reason", "Vehicle maintenance: " + request.getReason());
                    cancelledBookings.add(cancelledBooking);
                }
            }

            result.put("cancelledBookings", cancelledBookings);
            result.put("totalCancelled", cancelledBookings.size());

            // Gửi notification cho tất cả user bị ảnh hưởng
            if (request.isNotifyUsers()) {
                for (UsageBooking booking : affectedBookings) {
                    if (booking.getStatus() == BookingStatus.CANCELLED) {
                        notificationService.sendNotification(
                                booking.getUser(),
                                "Booking Cancelled - Vehicle Maintenance",
                                "Your booking has been cancelled due to vehicle maintenance: " + request.getReason(),
                                "MAINTENANCE"
                        );
                    }
                }
                result.put("notificationsSent", true);
                result.put("notificationMessage", "Your booking has been cancelled due to vehicle maintenance: " + request.getReason());
            }
        }

        return result;
    }
}

