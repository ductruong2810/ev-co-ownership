package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.exception.BookingValidationException;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.repository.IncidentRepository;
import com.group8.evcoownership.repository.MaintenanceRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleCheckRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;

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

        // Count slots user has booked (CONFIRMED, BOOKED_SELF...) this week
        int usedQuotaSlots = 0;
        for (DailySlotResponseDTO day : dailySlots) {
            for (TimeSlotResponseDTO slot : day.getSlots()) {
                if ("BOOKED_SELF".equals(slot.getType()) && slot.getBookedBy() != null) {
                    usedQuotaSlots++;
                }
            }
        }
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
        // 1. MAINTENANCE (BUFFER, user=null)
        UsageBooking maintenance = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.BUFFER && b.getUser() == null)
                .findFirst().orElse(null);
        if (maintenance != null) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status("BOOKED")
                    .type("MAINTENANCE")
                    .bookedBy("Maintenance")
                    .bookable(false)
                    .build();
        }
        // 2. LOCKED (BUFFER, user!=null)
        UsageBooking locked = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.BUFFER && b.getUser() != null)
                .findFirst().orElse(null);
        if (locked != null) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status("BOOKED")
                    .type("LOCKED")
                    .bookedBy(locked.getUser() != null ? locked.getUser().getFullName() : null)
                    .bookable(false)
                    .build();
        }

        // 3. CONFIRMED booking
        UsageBooking booking = overlapping.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .findFirst().orElse(null);
        if (booking != null) {
            boolean bookedBySelf = booking.getUser() != null && booking.getUser().getUserId().equals(userId);

            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status(booking.getStatus().name())
                    .type(bookedBySelf ? "BOOKED_SELF" : "BOOKED_OTHER")
                    .bookedBy(booking.getUser() != null ? booking.getUser().getFullName() : "Unknown")
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

    /**
     * Create flexible booking (supports overnight and custom duration)
     */
    @Transactional
    public FlexibleBookingResponseDTO createFlexibleBooking(FlexibleBookingRequestDTO request, String userEmail) {
        // Validate user and vehicle
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        if (request.getStartDateTime() == null || request.getEndDateTime() == null) {
            throw new BookingValidationException("Start time and end time are required");
        }

        if (request.getStartDateTime().equals(request.getEndDateTime())) {
            throw new BookingValidationException("Start time and end time cannot be the same");
        }

        if (request.getStartDateTime().isAfter(request.getEndDateTime())) {
            throw new BookingValidationException("Start time must be before end time");
        }

        ZonedDateTime nowVietnam = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime now = nowVietnam.toLocalDateTime();

        if (request.getStartDateTime().isBefore(now)) {
            throw new BookingValidationException("Cannot book in the past. Start time must be in the future");
        }

        long durationMinutes = Duration.between(request.getStartDateTime(), request.getEndDateTime()).toMinutes();
        if (durationMinutes < 60) {
            throw new BookingValidationException("Booking duration must be at least 1 hour");
        }

        LocalDateTime maxFutureDate = now.plusMonths(3);
        if (request.getStartDateTime().isAfter(maxFutureDate)) {
            throw new BookingValidationException("Cannot book more than 3 months in advance");
        }

        // Check quota
        LocalDateTime weekStart = request.getStartDateTime().with(DayOfWeek.MONDAY).with(LocalTime.MIN);
        long bookedHours = usageBookingRepository.getTotalBookedHoursThisWeek(
                user.getUserId(), request.getVehicleId(), weekStart);
        long newBookingHours = Duration.between(request.getStartDateTime(), request.getEndDateTime()).toHours();

        Long quotaLimit = usageBookingRepository.getQuotaLimitByOwnershipPercentage(
                user.getUserId(), request.getVehicleId());

        if (quotaLimit == null) {
            throw new IllegalStateException("User is not a member of the vehicle's ownership group.");
        }

        if (bookedHours + newBookingHours > quotaLimit) {
            long remainingHours = quotaLimit - bookedHours;
            throw new IllegalStateException(String.format(
                    "Weekly quota exceeded. You have used %d/%d hours this week. You can only book %d more hours.",
                    bookedHours, quotaLimit, Math.max(0, remainingHours)));
        }

        // Create booking
        UsageBooking booking = new UsageBooking();
        booking.setUser(user);
        booking.setVehicle(vehicle);
        booking.setStartDateTime(request.getStartDateTime());
        booking.setEndDateTime(request.getEndDateTime());
        booking.setStatus(BookingStatus.CONFIRMED);

        UsageBooking savedBooking = usageBookingRepository.save(booking);

        // Determine if this is an overnight booking
        boolean overnightBooking = !request.getStartDateTime().toLocalDate()
                .equals(request.getEndDateTime().toLocalDate());

        return FlexibleBookingResponseDTO.builder()
                .bookingId(savedBooking.getId())
                .status("CONFIRMED")
                .message(overnightBooking ? "Overnight booking created successfully" : "Booking created successfully")
                .totalHours(newBookingHours)
                .overnightBooking(overnightBooking)
                .build();
    }
}
