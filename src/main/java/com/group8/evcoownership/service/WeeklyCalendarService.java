package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.exception.BookingValidationException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyCalendarService {

    private final UsageBookingRepository usageBookingRepository;
    private final VehicleRepository vehicleRepository;
    private final OwnershipGroupRepository groupRepository;
    private final OwnershipShareRepository ownershipShareRepository;
    private final UserRepository userRepository;
    private final VehicleCheckRepository vehicleCheckRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final IncidentRepository incidentRepository;

    /**
     * Get weekly calendar for group with user quota information
     */
    public WeeklyCalendarResponseDTO getWeeklyCalendar(Long groupId, Long userId, LocalDate weekStart) {

        // If weekStart is null, use current week
        if (weekStart == null) {
            weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        }

        // Validate group exists
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        // Get vehicle of the group
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found for this group"));

        // Calculate user quota
        Long totalQuota = usageBookingRepository.getQuotaLimitByOwnershipPercentage(userId, vehicle.getId());

        // Create daily slots for 7 days
        List<DailySlotResponseDTO> dailySlots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            DailySlotResponseDTO dailySlot = createDailySlot(vehicle.getId(), date, userId);
            dailySlots.add(dailySlot);
        }

        // Calculate quota by slots
        int slotDurationHour = 3;
        long totalQuotaHour = totalQuota != null ? totalQuota : 0L;
        int totalQuotaSlots = (int) (totalQuotaHour / slotDurationHour);

        // Count slots user has booked this week
        int usedQuotaSlots = calculateUsedQuotaSlots(dailySlots);

        int remainingQuotaSlots = totalQuotaSlots - usedQuotaSlots;

        // Get dashboard summary
        WeeklyCalendarDashboardDTO dashboard = getDashboardSummary(
                vehicle.getId(), group.getGroupId(), vehicle, weekStart, userId);

        return WeeklyCalendarResponseDTO.builder()
                .weekStart(weekStart)
                .weekEnd(weekStart.plusDays(6))
                .userQuota(UserQuotaResponseDTO.builder()
                        .totalSlots(totalQuotaSlots)
                        .usedSlots(usedQuotaSlots)
                        .remainingSlots(remainingQuotaSlots)
                        .build())
                .dailySlots(dailySlots)
                .dashboardSummary(dashboard)
                .build();
    }

    /**
     * Get dashboard summary for vehicle and group
     */
    private WeeklyCalendarDashboardDTO getDashboardSummary(Long vehicleId, Long groupId, Vehicle vehicle,
                                                           LocalDate weekStart, Long userId) {
        // 1. Get vehicle status from latest POST_USE check
        VehicleCheck latestCheck = getLatestVehicleCheck(vehicleId, groupId);

        Integer batteryPercent = null;
        Integer odometer = null;
        if (latestCheck != null) {
            odometer = latestCheck.getOdometer();
            if (latestCheck.getBatteryLevel() != null) {
                // Convert BigDecimal batteryLevel (0-100) to Integer percent
                batteryPercent = latestCheck.getBatteryLevel().intValue();
            }
        }

        // 2. Get maintenance dates
        MaintenanceDates maintenanceDates = getMaintenanceDates(vehicleId, groupId);

        // 3. Determine maintenanceStatus
        String maintenanceStatus = determineMaintenanceStatus(vehicleId, groupId, maintenanceDates.nextMaintenanceDate);

        // 4. Determine vehicleStatus
        String vehicleStatus = determineVehicleStatus(vehicleId, groupId);

        // 5. Calculate booking statistics for the week
        LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
        LocalDateTime weekEndDateTime = weekStart.plusDays(7).atStartOfDay();

        // Count total bookings for the week (CONFIRMED only)
        int totalBookings = countTotalBookingsInWeek(vehicleId, weekStartDateTime, weekEndDateTime);

        // Count user bookings for the week
        int userBookings = countUserBookingsInWeek(vehicleId, userId, weekStartDateTime, weekEndDateTime);

        // Get user's ownership percentage (tỷ lệ sở hữu)
        Double ownershipPercent = ownershipShareRepository.findById_UserIdAndGroup_GroupId(userId, groupId)
                .map(share -> {
                    // Convert BigDecimal to Double and round to 1 decimal place
                    double percentage = share.getOwnershipPercentage().doubleValue();
                    return Math.round(percentage * 10.0) / 10.0;
                })
                .orElse(null);

        return WeeklyCalendarDashboardDTO.builder()
                .groupId(groupId)
                .vehicleId(vehicleId)
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .licensePlate(vehicle.getLicensePlate())
                .vehicleValue(vehicle.getVehicleValue())
                .vehicleStatus(vehicleStatus)
                .batteryPercent(batteryPercent)
                .odometer(odometer)
                .lastMaintenanceDate(maintenanceDates.lastMaintenanceDate)
                .nextMaintenanceDate(maintenanceDates.nextMaintenanceDate)
                .maintenanceStatus(maintenanceStatus)
                .totalBookings(totalBookings)
                .userBookings(userBookings)
                .ownershipPercent(ownershipPercent)
                .build();
    }

    /**
     * Count total bookings for vehicle in the week (CONFIRMED and PENDING only)
     * Counts unique bookings that overlap with the week period
     */
    private int countTotalBookingsInWeek(Long vehicleId, LocalDateTime weekStart, LocalDateTime weekEnd) {
        // Use findAffectedBookings pattern to get all bookings that overlap with the week
        List<UsageBooking> bookings = usageBookingRepository.findAffectedBookings(
                vehicleId, weekStart, weekEnd);

        // Count unique bookings (CONFIRMED only, exclude BUFFER)
        Set<Long> uniqueBookingIds = new HashSet<>();
        bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .forEach(b -> uniqueBookingIds.add(b.getId()));

        return uniqueBookingIds.size();
    }

    /**
     * Count user bookings for vehicle in the week
     * Counts unique bookings that overlap with the week period
     */
    private int countUserBookingsInWeek(Long vehicleId, Long userId, LocalDateTime weekStart, LocalDateTime weekEnd) {
        // Use findAffectedBookings to get all bookings for vehicle that overlap with the week
        List<UsageBooking> bookings = usageBookingRepository.findAffectedBookings(
                vehicleId, weekStart, weekEnd);

        // Filter by user and count unique bookings (CONFIRMED only)
        Set<Long> uniqueBookingIds = new HashSet<>();
        bookings.stream()
                .filter(b -> b.getUser() != null && b.getUser().getUserId().equals(userId))
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .forEach(b -> uniqueBookingIds.add(b.getId()));

        return uniqueBookingIds.size();
    }

    /**
     * Get latest vehicle check from POST_USE checks of this group and vehicle
     */
    private VehicleCheck getLatestVehicleCheck(Long vehicleId, Long groupId) {
        // Try to get from latest completed booking first
        List<UsageBooking> latestCompletedBookings = usageBookingRepository.findLatestCompletedBookingByVehicleAndGroup(
                vehicleId, groupId);

        if (!latestCompletedBookings.isEmpty()) {
            UsageBooking latestCompletedBooking = latestCompletedBookings.get(0);
            // Get POST_USE check from the latest completed booking
            List<VehicleCheck> bookingChecks = vehicleCheckRepository.findByBookingId(latestCompletedBooking.getId());
            VehicleCheck latestCheck = bookingChecks.stream()
                    .filter(vc -> "POST_USE".equals(vc.getCheckType()))
                    .findFirst()
                    .orElse(null);

            if (latestCheck != null) {
                return latestCheck;
            }
        }

        // Fallback: if no POST_USE check from latest booking, get latest POST_USE check from any booking of this group
        List<VehicleCheck> groupPostUseChecks = vehicleCheckRepository.findLatestPostUseCheckByVehicleAndGroup(
                vehicleId, groupId, PageRequest.of(0, 1));
        return groupPostUseChecks.isEmpty() ? null : groupPostUseChecks.get(0);
    }

    /**
     * Helper class to hold maintenance dates
     */
    private static class MaintenanceDates {
        LocalDate lastMaintenanceDate;
        LocalDate nextMaintenanceDate;
    }

    /**
     * Get maintenance dates (lastMaintenanceDate and nextMaintenanceDate)
     */
    private MaintenanceDates getMaintenanceDates(Long vehicleId, Long groupId) {
        MaintenanceDates dates = new MaintenanceDates();

        Maintenance latestApprovedMaintenance = maintenanceRepository
                .findLatestApprovedMaintenance(vehicleId, groupId)
                .orElse(null);

        if (latestApprovedMaintenance != null) {
            // Use ApprovalDate as lastMaintenanceDate (when maintenance was approved/completed)
            if (latestApprovedMaintenance.getApprovalDate() != null) {
                dates.lastMaintenanceDate = latestApprovedMaintenance.getApprovalDate().toLocalDate();
            } else {
                // Fallback to requestDate if approvalDate is null
                dates.lastMaintenanceDate = latestApprovedMaintenance.getRequestDate().toLocalDate();
            }

            // Use NextDueDate from database if available, otherwise calculate 3 months after approval
            if (latestApprovedMaintenance.getNextDueDate() != null) {
                dates.nextMaintenanceDate = latestApprovedMaintenance.getNextDueDate();
            } else if (dates.lastMaintenanceDate != null) {
                // Calculate nextMaintenanceDate: 3 months after last maintenance
                dates.nextMaintenanceDate = dates.lastMaintenanceDate.plusMonths(3);
            }
        }

        return dates;
    }

    /**
     * Determine maintenance status for vehicle and group
     */
    private String determineMaintenanceStatus(Long vehicleId, Long groupId, LocalDate nextMaintenanceDate) {
        boolean hasPendingMaintenance = maintenanceRepository.existsByVehicle_IdAndGroupIdAndStatusPending(vehicleId, groupId);

        if (hasPendingMaintenance) {
            return "NEEDS_MAINTENANCE";
        } else if (nextMaintenanceDate != null && LocalDate.now().isAfter(nextMaintenanceDate.minusDays(7))) {
            // Maintenance due soon (within 7 days)
            return "NEEDS_MAINTENANCE";
        } else {
            return "NO_ISSUE";
        }
    }

    /**
     * Determine vehicle status for vehicle and group
     * Priority: Active Maintenance (APPROVED today) > VehicleCheck Issues > Incident unresolved
     * Note: Only show "Under Maintenance" when maintenance is actually being performed (APPROVED today)
     */
    private String determineVehicleStatus(Long vehicleId, Long groupId) {
        // Check if maintenance is being performed today (APPROVED with ApprovalDate = today)
        boolean hasActiveMaintenance = maintenanceRepository.existsActiveMaintenance(vehicleId, groupId);
        boolean hasVehicleCheckIssues = vehicleCheckRepository.existsPostUseCheckWithIssuesByVehicleAndGroup(vehicleId, groupId);
        boolean hasUnresolvedIncidents = incidentRepository.existsUnresolvedIncidentsByVehicleIdAndGroupId(vehicleId, groupId);

        if (hasActiveMaintenance) {
            return "Under Maintenance";
        } else if (hasVehicleCheckIssues || hasUnresolvedIncidents) {
            return "Has Issues";
        } else {
            return "Good";
        }
    }

    /**
     * Create daily slot for a specific day (24/7)
     */
    private DailySlotResponseDTO createDailySlot(Long vehicleId, LocalDate date, Long userId) {
        List<TimeSlotResponseDTO> slots = new ArrayList<>();

        // Create 12 slots according to UI layout: 00-03, 03-04, 04-07, 07-08, 08-11, 11-12, 12-15, 15-16, 16-19, 19-20, 20-23, 23-24
        int[][] ranges = new int[][]{
                {0, 3}, {3, 4}, {4, 7}, {7, 8}, {8, 11}, {11, 12}, {12, 15}, {15, 16}, {16, 19}, {19, 20}, {20, 23}, {23, 24}
        };

        for (int i = 0; i < ranges.length; i++) {
            int[] r = ranges[i];
            LocalDateTime slotStart = date.atTime(r[0], 0);
            LocalDateTime slotEnd = (r[1] == 24) ? date.plusDays(1).atTime(0, 0) : date.atTime(r[1], 0);

            TimeSlotResponseDTO slot;
            // Maintenance slots are odd-indexed slots in ranges (1, 3, 5, ...) => i % 2 == 1
            if (i % 2 == 1) {
                String timeDisplay = formatTimeSlot(slotStart, slotEnd);
                slot = TimeSlotResponseDTO.builder()
                        .time(timeDisplay)
                        .status("LOCKED")
                        .type("MAINTENANCE")
                        .bookedBy("Maintenance")
                        .bookable(false)
                        .build();
            } else {
                slot = createTimeSlot(vehicleId, slotStart, slotEnd, userId);
            }
            slots.add(slot);
        }

        return DailySlotResponseDTO.builder()
                .date(date)
                .dayOfWeek(date.getDayOfWeek().name())
                .slots(slots)
                .build();
    }

    /**
     * Create time slot with booking information (supports overnight booking)
     */
    private TimeSlotResponseDTO createTimeSlot(Long vehicleId, LocalDateTime start, LocalDateTime end, Long userId) {
        // Check if this slot is booked - supports overnight booking

        // Get bookings from start date
        List<UsageBooking> bookings = new ArrayList<>(usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, start.toLocalDate()));

        // If slot extends to next day, get bookings from that day as well
        if (!end.toLocalDate().equals(start.toLocalDate())) {
            bookings.addAll(usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, end.toLocalDate()));
        }

        // Filter bookings that overlap with this slot
        List<UsageBooking> overlapping = bookings.stream()
                .filter(b -> !b.getStartDateTime().isAfter(end) && !b.getEndDateTime().isBefore(start))
                .toList();

        String timeDisplay = formatTimeSlot(start, end);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockThreshold = start.plusMinutes(20);
        // CONFIRMED booking
        UsageBooking confirmedBooking = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .findFirst().orElse(null);
        if (confirmedBooking != null) {
            String slotType = getString(userId, confirmedBooking);

            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status(confirmedBooking.getStatus().name())
                    .type(slotType)
                    .bookedBy(confirmedBooking.getUser() != null ? confirmedBooking.getUser().getFullName() : "Unknown")
                    .bookable(false)
                    .bookingId(confirmedBooking.getId())
                    .build();
        }

        UsageBooking completedBooking = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .findFirst().orElse(null);
        if (completedBooking != null) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status(completedBooking.getStatus().name())
                    .type("COMPLETED")
                    .bookedBy(completedBooking.getUser() != null ? completedBooking.getUser().getFullName() : "Unknown")
                    .bookable(false)
                    .bookingId(completedBooking.getId())
                    .build();
        }

        UsageBooking awaitingReviewBooking = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.AWAITING_REVIEW)
                .findFirst().orElse(null);
        if (awaitingReviewBooking != null) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status(awaitingReviewBooking.getStatus().name())
                    .type("AWAITING_REVIEW")
                    .bookedBy(awaitingReviewBooking.getUser() != null ? awaitingReviewBooking.getUser().getFullName() : "Unknown")
                    .bookable(false)
                    .bookingId(awaitingReviewBooking.getId())
                    .build();
        }

        UsageBooking needsAttentionBooking = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.NEEDS_ATTENTION)
                .findFirst().orElse(null);
        if (needsAttentionBooking != null) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status(needsAttentionBooking.getStatus().name())
                    .type("NEEDS_ATTENTION")
                    .bookedBy(needsAttentionBooking.getUser() != null ? needsAttentionBooking.getUser().getFullName() : "Unknown")
                    .bookable(false)
                    .bookingId(needsAttentionBooking.getId())
                    .build();
        }

        //
        if (now.isAfter(lockThreshold) && now.isBefore(end)) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status("LOCKED")
                    .type("LOCKED")
                    .bookable(false)
                    .build();
        }

        if (!end.isAfter(now)) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status("LOCKED")
                    .type("LOCKED")
                    .bookable(false)
                    .build();
        }

        return TimeSlotResponseDTO.builder()
                .time(timeDisplay)
                .status("AVAILABLE")
                .type("AVAILABLE")
                .bookable(true)
                .build();
    }

    private static String getString(Long userId, UsageBooking confirmedBooking) {
        boolean bookedBySelf = confirmedBooking.getUser() != null && confirmedBooking.getUser().getUserId().equals(userId);
        boolean isCheckedIn = Boolean.TRUE.equals(confirmedBooking.getCheckinStatus());

        // Nếu đã check-in, trả về type riêng
        String slotType;
        if (isCheckedIn) {
            slotType = bookedBySelf ? "CHECKED_IN_SELF" : "CHECKED_IN_OTHER";
        } else {
            slotType = bookedBySelf ? "BOOKED_SELF" : "BOOKED_OTHER";
        }
        return slotType;
    }

    /**
     * Calculate used quota slots for user's bookings this week
     * Counts slots with types: BOOKED_SELF, CHECKED_IN_SELF
     */
    private int calculateUsedQuotaSlots(List<DailySlotResponseDTO> dailySlots) {
        Set<String> quotaTypes = Set.of("BOOKED_SELF", "CHECKED_IN_SELF");
        int usedQuotaSlots = 0;
        for (DailySlotResponseDTO day : dailySlots) {
            for (TimeSlotResponseDTO slot : day.getSlots()) {
                if (quotaTypes.contains(slot.getType())) {
                    usedQuotaSlots++;
                }
            }
        }
        return usedQuotaSlots;
    }

    /**
     * Format time slot to display overnight booking
     */
    private String formatTimeSlot(LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        if (start.toLocalDate().equals(end.toLocalDate())) {
            // Same day: 08:00-12:00
            return start.format(timeFormatter) + "-" + end.format(timeFormatter);
        } else {
            // Different day: 20:00-08:00+1
            return start.format(timeFormatter) + "-" + end.format(timeFormatter) + "+1";
        }
    }

    /**
     * Get suggestions for user based on quota and availability
     */
    public List<String> getBookingSuggestions(Long groupId, Long userId, LocalDate weekStart) {
        List<String> suggestions = new ArrayList<>();

        WeeklyCalendarResponseDTO calendar = getWeeklyCalendar(groupId, userId, weekStart);

        // Suggestion based on quota
        if (calendar.getUserQuota().getRemainingSlots() > 20) {
            suggestions.add("You have " + calendar.getUserQuota().getRemainingSlots() +
                    " unused slots left. Consider booking more to use your quota!");
        }

        if (calendar.getUserQuota().getRemainingSlots() < 5) {
            suggestions.add("You only have " + calendar.getUserQuota().getRemainingSlots() +
                    " slots left. Please book carefully!");
        }

        // Suggestion based on availability
        long availableSlots = calendar.getDailySlots().stream()
                .flatMap(daily -> daily.getSlots().stream())
                .filter(TimeSlotResponseDTO::isBookable)
                .count();

        if (availableSlots < 5) {
            suggestions.add("There are few available slots this week (" + availableSlots + " slots). Book soon!");
        }

        return suggestions;
    }

    // ========= Tạo booking flexible có qua đêm =========
    @Transactional
    public FlexibleBookingResponseDTO createFlexibleBooking(FlexibleBookingRequestDTO request, String userEmail) {
        // 1. Validate user và vehicle tồn tại trong hệ thống
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        // 2. Kiểm tra các ràng buộc về thời gian đầu vào
        if (request.getStartDateTime() == null || request.getEndDateTime() == null) {
            throw new BookingValidationException("Start time and end time are required");
        }

        // Không cho phép time bắt đầu và kết thúc trùng nhau
        if (request.getStartDateTime().equals(request.getEndDateTime())) {
            throw new BookingValidationException("Start time and end time cannot be the same");
        }

        // Time bắt đầu phải trước tie kết thúc
        if (request.getStartDateTime().isAfter(request.getEndDateTime())) {
            throw new BookingValidationException("Start time must be before end time");
        }

        // Lấy time hiện tại theo múi giờ Việt Nam
        ZonedDateTime nowVietnam = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime now = nowVietnam.toLocalDateTime();

        // Không cho phép đặt booking trong quá khứ
        if (request.getStartDateTime().isBefore(now)) {
            throw new BookingValidationException("Cannot book in the past. Start time must be in the future");
        }

        // Thời lượng tối thiểu: 60 phút
        long durationMinutes = Duration.between(request.getStartDateTime(), request.getEndDateTime()).toMinutes();
        if (durationMinutes < 60) {
            throw new BookingValidationException("Booking duration must be at least 1 hour");
        }

        // Giới hạn thời điểm bắt đầu: không được đặt trước quá 3 tháng
        LocalDateTime maxFutureDate = now.plusMonths(3);
        if (request.getStartDateTime().isAfter(maxFutureDate)) {
            throw new BookingValidationException("Cannot book more than 3 months in advance");
        }

        // 3. Kiểm tra quota (số giờ tối đa trong tuần dựa trên tỷ lệ sở hữu)
        // Xác định tuần chứa startDateTime (lấy thứ 2 đầu tuần, time = 00:00)
        LocalDateTime weekStart = request.getStartDateTime()
                .with(DayOfWeek.MONDAY)
                .with(LocalTime.MIN);

        // Tổng số giờ user đã book trong tuần này cho vehicle này
        long bookedHours = usageBookingRepository.getTotalBookedHoursThisWeek(
                user.getUserId(), request.getVehicleId(), weekStart);

        // Số giờ của booking mới đang yêu cầu
        long newBookingHours = Duration.between(request.getStartDateTime(), request.getEndDateTime()).toHours();

        // Hạn mức quota theo tỷ lệ sở hữu (số giờ tối đa/tuần)
        Long quotaLimit = usageBookingRepository.getQuotaLimitByOwnershipPercentage(
                user.getUserId(), request.getVehicleId());

        // Nếu null nghĩa là user không thuộc group sở hữu vehicle này
        if (quotaLimit == null) {
            throw new IllegalStateException("User is not a member of the vehicle's ownership group.");
        }

        // Nếu tổng giờ đã dùng + giờ mới > quota -> từ chối
        if (bookedHours + newBookingHours > quotaLimit) {
            long remainingHours = quotaLimit - bookedHours;
            throw new IllegalStateException(String.format(
                    "Weekly quota exceeded. You have used %d/%d hours this week. You can only book %d more hours.",
                    bookedHours, quotaLimit, Math.max(0, remainingHours)));
        }

        // 4. Tạo booking mới sau khi qua mọi validation
        UsageBooking booking = new UsageBooking();
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setStartDateTime(request.getStartDateTime());
        booking.setEndDateTime(request.getEndDateTime());
        booking.setStatus(BookingStatus.CONFIRMED); // Booking tạo ra là CONFIRMED luôn

        UsageBooking savedBooking = usageBookingRepository.save(booking);

        // 5. Sinh QR code check-in cho booking (QR checkout sẽ tạo sau khi trả xe)
        String qrCodeCheckin = generateCheckInQrPayload(savedBooking);

        // Gắn payload QR check-in vào booking và lưu lại
        savedBooking.setQrCodeCheckin(qrCodeCheckin);
        usageBookingRepository.save(savedBooking);

        // 6. Kiểm tra booking có qua đêm hay không (start và end khác ngày)
        boolean overnightBooking = !request.getStartDateTime().toLocalDate()
                .equals(request.getEndDateTime().toLocalDate());

        // 7. Trả về DTO kết quả cho client
        return FlexibleBookingResponseDTO.builder()
                .bookingId(savedBooking.getId())
                .status("CONFIRMED")
                .message(overnightBooking
                        ? "Overnight booking created successfully"
                        : "Booking created successfully")
                .totalHours(newBookingHours)
                .overnightBooking(overnightBooking)
                .qrCodeCheckin(qrCodeCheckin)
                .qrCodeCheckout(null)  // QR checkout sẽ tạo lúc trả xe
                .startDateTime(savedBooking.getStartDateTime())
                .endDateTime(savedBooking.getEndDateTime())
                .createdAt(savedBooking.getCreatedAt())
                .build();
    }

    // ========= Tạo payload cho qr-checkin =========
  // Payload là chuỗi JSON, chứa thông tin cơ bản để hệ thống xác định đúng booking khi quét QR
    private String generateCheckInQrPayload(UsageBooking booking) {
        // Nếu có thời gian thì wrap trong dấu " để thành JSON string, nếu không thì để null
        String startTime = booking.getStartDateTime() != null
                ? "\"" + booking.getStartDateTime() + "\""
                : "null";
        String endTime = booking.getEndDateTime() != null
                ? "\"" + booking.getEndDateTime() + "\""
                : "null";

        // Bỏ timestamp và nonce để payload gọn, dễ debug
        // JSON gồm: bookingId, userId, vehicleId, phase (CHECKIN), startTime, endTime
        return String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"vehicleId\":%d,\"phase\":\"CHECKIN\",\"startTime\":%s,\"endTime\":%s}",
                booking.getId(),
                booking.getUser() != null ? booking.getUser().getUserId() : null,
                booking.getVehicle() != null ? booking.getVehicle().getId() : null,
                startTime,
                endTime
        );
    }
}
