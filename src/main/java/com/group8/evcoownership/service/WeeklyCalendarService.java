package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyCalendarService {

    private final UsageBookingRepository usageBookingRepository;
    private final VehicleRepository vehicleRepository;
    private final OwnershipGroupRepository groupRepository;
    private final UserRepository userRepository;

    /**
     * Lấy lịch tuần cho group với thông tin quota của user
     */
    public WeeklyCalendarResponse getWeeklyCalendar(Long groupId, Long userId, LocalDate weekStart) {
        // Validate group tồn tại
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        // Lấy vehicle của group
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found for this group"));

        // Tính toán quota của user
        LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
        Long totalQuota = usageBookingRepository.getQuotaLimitByOwnershipPercentage(userId, vehicle.getId());
        Long usedHours = usageBookingRepository.getTotalBookedHoursThisWeek(userId, vehicle.getId(), weekStartDateTime);
        long remainingHours = totalQuota - usedHours;

        // Tạo daily slots cho 7 ngày
        List<DailySlotResponse> dailySlots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            DailySlotResponse dailySlot = createDailySlot(vehicle.getId(), date, userId);
            dailySlots.add(dailySlot);
        }

        return WeeklyCalendarResponse.builder()
                .weekStart(weekStart)
                .weekEnd(weekStart.plusDays(6))
                .userQuota(UserQuotaResponse.builder()
                        .totalHours(totalQuota)
                        .usedHours(usedHours)
                        .remainingHours(Math.max(0, remainingHours))
                        .build())
                .dailySlots(dailySlots)
                .build();
    }

    /**
     * Tạo daily slot cho một ngày cụ thể (24/7)
     */
    private DailySlotResponse createDailySlot(Long vehicleId, LocalDate date, Long userId) {
        List<TimeSlotResponse> slots = new ArrayList<>();

        // Tạo 12 slot theo layout UI: 00-03, 03-04, 04-07, 07-08, 08-11, 11-12, 12-15, 15-16, 16-19, 19-20, 20-23, 23-24
        int[][] ranges = new int[][]{
                {0, 3}, {3, 4}, {4, 7}, {7, 8}, {8, 11}, {11, 12}, {12, 15}, {15, 16}, {16, 19}, {19, 20}, {20, 23}, {23, 24}
        };

        for (int[] r : ranges) {
            LocalDateTime slotStart = date.atTime(r[0], 0);
            LocalDateTime slotEnd = (r[1] == 24) ? date.plusDays(1).atTime(0, 0) : date.atTime(r[1], 0);
            TimeSlotResponse slot = createTimeSlot(vehicleId, slotStart, slotEnd, userId);
            slots.add(slot);
        }

        return DailySlotResponse.builder()
                .date(date)
                .dayOfWeek(date.getDayOfWeek().name())
                .slots(slots)
                .build();
    }

    /**
     * Tạo time slot với thông tin booking (hỗ trợ overnight)
     */
    private TimeSlotResponse createTimeSlot(Long vehicleId, LocalDateTime start, LocalDateTime end, Long userId) {
        // Kiểm tra slot này có bị book chưa - hỗ trợ overnight booking

        // Lấy bookings từ ngày bắt đầu
        List<UsageBooking> bookings = new ArrayList<>(usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, start.toLocalDate()));

        // Nếu slot kéo dài qua ngày hôm sau, lấy thêm bookings từ ngày đó
        if (!end.toLocalDate().equals(start.toLocalDate())) {
            bookings.addAll(usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, end.toLocalDate()));
        }

        // Lọc các booking overlap với slot
        List<UsageBooking> overlapping = bookings.stream()
                .filter(b -> !b.getStartDateTime().isAfter(end) && !b.getEndDateTime().isBefore(start))
                .toList();

        String timeDisplay = formatTimeSlot(start, end);

        // Ưu tiên hiển thị: MAINTENANCE (BUFFER, user=null) → LOCKED (BUFFER, user!=null) → BOOKED (CONFIRMED)
        UsageBooking maintenance = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.BUFFER && b.getUser() == null)
                .findFirst().orElse(null);
        if (maintenance != null) {
            return TimeSlotResponse.builder()
                    .time(timeDisplay)
                    .status("BOOKED")
                    .type("MAINTENANCE")
                    .bookedBy("Maintenance")
                    .bookable(false)
                    .build();
        }

        UsageBooking locked = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.BUFFER && b.getUser() != null)
                .findFirst().orElse(null);
        if (locked != null) {
            return TimeSlotResponse.builder()
                    .time(timeDisplay)
                    .status("BOOKED")
                    .type("LOCKED")
                    .bookedBy(locked.getUser() != null ? locked.getUser().getFullName() : null)
                    .bookable(false)
                    .build();
        }

        UsageBooking confirmed = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .findFirst().orElse(null);
        if (confirmed != null) {
            boolean bookedBySelf = confirmed.getUser() != null && confirmed.getUser().getUserId().equals(userId);
            return TimeSlotResponse.builder()
                    .time(timeDisplay)
                    .status("BOOKED")
                    .type(bookedBySelf ? "BOOKED_SELF" : "BOOKED_OTHER")
                    .bookedBy(confirmed.getUser() != null ? confirmed.getUser().getFullName() : "Unknown")
                    .bookable(false)
                    .build();
        }

        return TimeSlotResponse.builder()
                .time(timeDisplay)
                .status("AVAILABLE")
                .type("AVAILABLE")
                .bookable(true)
                .build();
    }

    /**
     * Format time slot để hiển thị overnight booking
     */
    private String formatTimeSlot(LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        if (start.toLocalDate().equals(end.toLocalDate())) {
            // Cùng ngày: 08:00-12:00
            return start.format(timeFormatter) + "-" + end.format(timeFormatter);
        } else {
            // Khác ngày: 20:00-08:00+1
            return start.format(timeFormatter) + "-" + end.format(timeFormatter) + "+1";
        }
    }

    /**
     * Lấy suggestions cho user dựa trên quota và availability
     */
    public List<String> getBookingSuggestions(Long groupId, Long userId, LocalDate weekStart) {
        List<String> suggestions = new ArrayList<>();

        WeeklyCalendarResponse calendar = getWeeklyCalendar(groupId, userId, weekStart);

        // Suggestion dựa trên quota
        if (calendar.getUserQuota().getRemainingHours() > 20) {
            suggestions.add("Bạn còn " + calendar.getUserQuota().getRemainingHours() +
                    " giờ chưa sử dụng. Nên book thêm để tận dụng quota!");
        }

        if (calendar.getUserQuota().getRemainingHours() < 5) {
            suggestions.add("Bạn chỉ còn " + calendar.getUserQuota().getRemainingHours() +
                    " giờ. Hãy cân nhắc khi book!");
        }

        // Suggestion dựa trên availability
        long availableSlots = calendar.getDailySlots().stream()
                .flatMap(daily -> daily.getSlots().stream())
                .filter(TimeSlotResponse::isBookable)
                .count();

        if (availableSlots < 5) {
            suggestions.add("Tuần này còn ít slot trống (" + availableSlots + " slots). Hãy book sớm!");
        }

        return suggestions;
    }

    /**
     * Tạo flexible booking (hỗ trợ overnight và custom duration)
     */
    @Transactional
    public FlexibleBookingResponse createFlexibleBooking(FlexibleBookingRequest request) {
        // Validate user và vehicle
        var user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        // Kiểm tra quota
        LocalDateTime weekStart = request.getStartDateTime().with(DayOfWeek.MONDAY).with(LocalTime.MIN);
        long bookedHours = usageBookingRepository.getTotalBookedHoursThisWeek(
                request.getUserId(), request.getVehicleId(), weekStart);
        long newBookingHours = Duration.between(request.getStartDateTime(), request.getEndDateTime()).toHours();

        Long quotaLimit = usageBookingRepository.getQuotaLimitByOwnershipPercentage(
                request.getUserId(), request.getVehicleId());

        if (quotaLimit == null) {
            throw new IllegalStateException("User is not a member of the vehicle's ownership group.");
        }

        if (bookedHours + newBookingHours > quotaLimit) {
            long remainingHours = quotaLimit - bookedHours;
            throw new IllegalStateException(String.format(
                    "Weekly quota exceeded. You have used %d/%d hours this week. You can only book %d more hours.",
                    bookedHours, quotaLimit, Math.max(0, remainingHours)));
        }

        // Kiểm tra conflict với buffer 1h
        long conflicts = usageBookingRepository.countOverlappingBookingsWithBuffer(
                request.getVehicleId(), request.getStartDateTime(), request.getEndDateTime());
        if (conflicts > 0) {
            throw new IllegalStateException("Time slot not available. There is a 1-hour buffer period after each booking.");
        }

        // Tạo booking
        UsageBooking booking = new UsageBooking();
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setStartDateTime(request.getStartDateTime());
        booking.setEndDateTime(request.getEndDateTime());
        booking.setStatus(BookingStatus.PENDING);

        UsageBooking savedBooking = usageBookingRepository.save(booking);

        // Xác định có phải overnight booking không
        boolean overnightBooking = !request.getStartDateTime().toLocalDate()
                .equals(request.getEndDateTime().toLocalDate());

        return FlexibleBookingResponse.builder()
                .bookingId(savedBooking.getId())
                .status("PENDING")
                .message(overnightBooking ? "Overnight booking created successfully" : "Booking created successfully")
                .totalHours(newBookingHours)
                .overnightBooking(overnightBooking)
                .build();
    }
}
