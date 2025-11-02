package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.CancelBookingRequestDTO;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.repository.UsageBookingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class UsageBookingService {

    private final UsageBookingRepository usageBookingRepository;
    private final NotificationService notificationService;
    private final NotificationOrchestrator notificationOrchestrator;

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

    // lấy booking của co-owner trong tuần theo groupId
    public List<UsageBooking> getUpcomingBookingsByGroup(Long userId, Long groupId) {
        // Tính weekStart (đầu tuần hiện tại - Thứ 2 00:00:00)
        LocalDateTime weekStart = LocalDateTime.now()
                .with(DayOfWeek.MONDAY)
                .withHour(0)
                .withMinute(0)
                .withSecond(0);

        // Tính weekEnd (7 ngày sau weekStart)
        LocalDateTime weekEnd = weekStart.plusWeeks(1);

        // Gọi repository với 4 tham số
        return usageBookingRepository.findBookingsByUserInWeekAndGroup(userId, groupId, weekStart, weekEnd);
    }

}

