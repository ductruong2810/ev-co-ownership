package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.BookingDetailResponseDTO;
import com.group8.evcoownership.dto.BookingResponseDTO;
import com.group8.evcoownership.dto.CancelBookingRequestDTO;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.UsageBookingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Transactional(readOnly = true)
    public Page<BookingResponseDTO> getBookingsForStaff(Long userId, Long groupId, Pageable pageable) {
        return usageBookingRepository.findBookingsByUserAndGroup(userId, groupId, pageable)
                .map(this::toBookingResponseDTO);
    }

    @Transactional(readOnly = true)
    public BookingDetailResponseDTO getBookingDetailForStaff(Long bookingId) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        return BookingDetailResponseDTO.builder()
                .bookingId(booking.getId())
                .userId(booking.getUser() != null ? booking.getUser().getUserId() : null)
                .userFullName(booking.getUser() != null ? booking.getUser().getFullName() : null)
                .vehicleId(booking.getVehicle() != null ? booking.getVehicle().getId() : null)
                .licensePlate(booking.getVehicle() != null ? booking.getVehicle().getLicensePlate() : null)
                .brand(booking.getVehicle() != null ? booking.getVehicle().getBrand() : null)
                .model(booking.getVehicle() != null ? booking.getVehicle().getModel() : null)
                .startDateTime(booking.getStartDateTime())
                .endDateTime(booking.getEndDateTime())
                .status(booking.getStatus() != null ? booking.getStatus().name() : null)
                .qrCode(booking.getQrCode())
                .build();
    }

    private BookingResponseDTO toBookingResponseDTO(UsageBooking booking) {
        return new BookingResponseDTO(
                booking.getId(),
                booking.getVehicle() != null ? booking.getVehicle().getLicensePlate() : null,
                booking.getVehicle() != null ? booking.getVehicle().getBrand() : null,
                booking.getVehicle() != null ? booking.getVehicle().getModel() : null,
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                booking.getStatus() != null ? booking.getStatus().name() : null
        );
    }

}

