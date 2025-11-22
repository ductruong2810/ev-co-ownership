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
import java.util.*;

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
     * Lấy lịch tuần cho nhóm với thông tin quota của người dùng
     * param groupId ID của nhóm sở hữu
     * param userId ID của người dùng
     * param weekStart Ngày bắt đầu tuần (thứ 2), nếu null thì lấy tuần hiện tại
     * return DTO chứa thông tin lịch tuần, quota, và dashboard
     */
    public WeeklyCalendarResponseDTO getWeeklyCalendar(Long groupId, Long userId, LocalDate weekStart) {

        // Nếu weekStart là null, sử dụng tuần hiện tại
        //
        if (weekStart == null) {
            weekStart = LocalDate.now();
        }

        // Kiểm tra nhóm có tồn tại không
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        // Lấy xe của nhóm
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found for this group"));

        // Tính toán quota của người dùng (dựa trên tỷ lệ sở hữu)
        Long totalQuota = usageBookingRepository.getQuotaLimitByOwnershipPercentage(userId, vehicle.getId());

        // Tạo các slot hàng ngày cho 7 ngày
        List<DailySlotResponseDTO> dailySlots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            DailySlotResponseDTO dailySlot = createDailySlot(vehicle.getId(), date, userId);
            dailySlots.add(dailySlot);
        }

        // Tính quota theo số slot (mỗi slot = 3 giờ)
        int slotDurationHour = 3;
        long totalQuotaHour = totalQuota != null ? totalQuota : 0L;
        int totalQuotaSlots = (int) (totalQuotaHour / slotDurationHour);

        // Đếm số slot người dùng đã đặt trong tuần này
        int usedQuotaSlots = calculateUsedQuotaSlots(dailySlots);

        int remainingQuotaSlots = totalQuotaSlots - usedQuotaSlots;

        // Lấy thông tin tổng quan dashboard
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
     * Lấy thông tin tổng quan dashboard cho xe và nhóm
     * Bao gồm: trạng thái xe, pin, đồng hồ đo, bảo dưỡng, thống kê booking
     */
    private WeeklyCalendarDashboardDTO getDashboardSummary(Long vehicleId, Long groupId, Vehicle vehicle,
                                                           LocalDate weekStart, Long userId) {
        // 1. Lấy trạng thái xe từ lần kiểm tra POST_USE mới nhất
        VehicleCheck latestCheck = getLatestVehicleCheck(vehicleId, groupId);

        Integer batteryPercent = null;
        Integer odometer = null;
        if (latestCheck != null) {
            odometer = latestCheck.getOdometer();
            if (latestCheck.getBatteryLevel() != null) {
                // Chuyển đổi BigDecimal batteryLevel (0-100) sang Integer phần trăm
                batteryPercent = latestCheck.getBatteryLevel().intValue();
            }
        }

        // 2. Lấy các ngày bảo dưỡng
        MaintenanceDates maintenanceDates = getMaintenanceDates(vehicleId, groupId);

        // 3. Xác định trạng thái bảo dưỡng
        String maintenanceStatus = determineMaintenanceStatus(vehicleId, groupId, maintenanceDates.nextMaintenanceDate);

        // 4. Xác định trạng thái xe
        String vehicleStatus = determineVehicleStatus(vehicleId, groupId);

        // 5. Tính toán thống kê booking cho tuần
        LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
        LocalDateTime weekEndDateTime = weekStart.plusDays(7).atStartOfDay();

        // Đếm tổng số booking trong tuần
        int totalBookings = countTotalBookingsInWeek(vehicleId, weekStartDateTime, weekEndDateTime);

        // Đếm số booking của người dùng trong tuần
        int userBookings = countUserBookingsInWeek(vehicleId, userId, weekStartDateTime, weekEndDateTime);

        // Lấy tỷ lệ sở hữu của người dùng
        Double ownershipPercent = ownershipShareRepository.findById_UserIdAndGroup_GroupId(userId, groupId)
                .map(share -> {
                    // Chuyển đổi BigDecimal sang Double và làm tròn đến 1 chữ số thập phân
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
     * Đếm tổng số booking của xe trong tuần (tất cả status)
     * Đếm các booking duy nhất có thời gian trùng với khoảng thời gian của tuần
     */
    private int countTotalBookingsInWeek(Long vehicleId, LocalDateTime weekStart, LocalDateTime weekEnd) {
        List<UsageBooking> bookings = usageBookingRepository.findAffectedBookings(
                vehicleId, weekStart, weekEnd);

        // Đếm tất cả các booking duy nhất (không lọc status)
        Set<Long> uniqueBookingIds = new HashSet<>();
        bookings.forEach(b -> uniqueBookingIds.add(b.getId()));

        return uniqueBookingIds.size();
    }

    private int countUserBookingsInWeek(Long vehicleId, Long userId, LocalDateTime weekStart, LocalDateTime weekEnd) {
        List<UsageBooking> bookings = usageBookingRepository.findAffectedBookings(
                vehicleId, weekStart, weekEnd);

        // Lọc theo người dùng và đếm tất cả các booking duy nhất (không lọc status)
        Set<Long> uniqueBookingIds = new HashSet<>();
        bookings.stream()
                .filter(b -> b.getUser() != null
                        && b.getUser().getUserId() != null
                        && Objects.equals(b.getUser().getUserId(), userId))
                .forEach(b -> uniqueBookingIds.add(b.getId()));

        return uniqueBookingIds.size();
    }

    /**
     * Lấy lần kiểm tra xe mới nhất từ các kiểm tra POST_USE của nhóm và xe này
     */
    private VehicleCheck getLatestVehicleCheck(Long vehicleId, Long groupId) {
        // Thử lấy từ booking hoàn thành mới nhất trước
        List<UsageBooking> latestCompletedBookings = usageBookingRepository.findLatestCompletedBookingByVehicleAndGroup(
                vehicleId, groupId);

        if (!latestCompletedBookings.isEmpty()) {
            UsageBooking latestCompletedBooking = latestCompletedBookings.get(0);
            // Lấy kiểm tra POST_USE từ booking hoàn thành mới nhất
            List<VehicleCheck> bookingChecks = vehicleCheckRepository.findByBookingId(latestCompletedBooking.getId());
            VehicleCheck latestCheck = bookingChecks.stream()
                    .filter(vc -> "POST_USE".equals(vc.getCheckType()))
                    .findFirst()
                    .orElse(null);

            if (latestCheck != null) {
                return latestCheck;
            }
        }

        // Dự phòng: nếu không có kiểm tra POST_USE từ booking mới nhất, 
        // lấy kiểm tra POST_USE mới nhất từ bất kỳ booking nào của nhóm này
        List<VehicleCheck> groupPostUseChecks = vehicleCheckRepository.findLatestPostUseCheckByVehicleAndGroup(
                vehicleId, groupId, PageRequest.of(0, 1));
        return groupPostUseChecks.isEmpty() ? null : groupPostUseChecks.get(0);
    }

    /**
     * Record helper để chứa các ngày bảo dưỡng
     * param lastMaintenanceDate Ngày bảo dưỡng cuối cùng
     * param nextMaintenanceDate Ngày bảo dưỡng tiếp theo
     */
    private record MaintenanceDates(LocalDate lastMaintenanceDate, LocalDate nextMaintenanceDate) {}

    /**
     * Lấy các ngày bảo dưỡng (ngày bảo dưỡng cuối và ngày bảo dưỡng tiếp theo)
     */
    private MaintenanceDates getMaintenanceDates(Long vehicleId, Long groupId) {
        LocalDate lastMaintenanceDate = null;
        LocalDate nextMaintenanceDate = null;

        Maintenance latestApprovedMaintenance = maintenanceRepository
                .findLatestApprovedMaintenance(vehicleId, groupId)
                .orElse(null);

        if (latestApprovedMaintenance != null) {
            // Sử dụng ApprovalDate làm lastMaintenanceDate (khi bảo dưỡng được phê duyệt/hoàn thành)
            if (latestApprovedMaintenance.getApprovalDate() != null) {
                lastMaintenanceDate = latestApprovedMaintenance.getApprovalDate().toLocalDate();
            } else {
                // Dự phòng: sử dụng requestDate nếu approvalDate là null
                lastMaintenanceDate = latestApprovedMaintenance.getRequestDate().toLocalDate();
            }

            // Sử dụng NextDueDate từ database nếu có, nếu không thì tính 3 tháng sau khi phê duyệt
            if (latestApprovedMaintenance.getNextDueDate() != null) {
                nextMaintenanceDate = latestApprovedMaintenance.getNextDueDate();
            } else if (lastMaintenanceDate != null) {
                // Tính nextMaintenanceDate: 3 tháng sau lần bảo dưỡng cuối
                nextMaintenanceDate = lastMaintenanceDate.plusMonths(3);
            }
        }

        return new MaintenanceDates(lastMaintenanceDate, nextMaintenanceDate);
    }

    /**
     * Xác định trạng thái bảo dưỡng cho xe và nhóm
     */
    private String determineMaintenanceStatus(Long vehicleId, Long groupId, LocalDate nextMaintenanceDate) {
        boolean hasPendingMaintenance = maintenanceRepository.existsByVehicle_IdAndGroupIdAndStatusPending(vehicleId, groupId);

        if (hasPendingMaintenance) {
            return "NEEDS_MAINTENANCE";
        } else if (nextMaintenanceDate != null && LocalDate.now().isAfter(nextMaintenanceDate.minusDays(7))) {
            // Bảo dưỡng sắp đến hạn (trong vòng 7 ngày)
            return "NEEDS_MAINTENANCE";
        } else {
            return "NO_ISSUE";
        }
    }

    /**
     * Xác định trạng thái xe cho xe và nhóm
     * Ưu tiên: Bảo dưỡng đang diễn ra (APPROVED hôm nay) > Vấn đề kiểm tra xe > Sự cố chưa giải quyết
     * Lưu ý: Chỉ hiển thị "Under Maintenance" khi bảo dưỡng thực sự đang được thực hiện (APPROVED hôm nay)
     */
    private String determineVehicleStatus(Long vehicleId, Long groupId) {
        // Kiểm tra xem bảo dưỡng có đang được thực hiện hôm nay không (APPROVED với ApprovalDate = hôm nay)
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
     * Tạo slot hàng ngày cho một ngày cụ thể (24/7)
     */
    private DailySlotResponseDTO createDailySlot(Long vehicleId, LocalDate date, Long userId) {
        List<TimeSlotResponseDTO> slots = new ArrayList<>();

        // Tạo 12 slot theo layout UI: 00-03, 03-04, 04-07, 07-08, 08-11, 11-12, 12-15, 15-16, 16-19, 19-20, 20-23, 23-24
        int[][] ranges = new int[][]{
                {0, 3}, {3, 4}, {4, 7}, {7, 8}, {8, 11}, {11, 12}, {12, 15}, {15, 16}, {16, 19}, {19, 20}, {20, 23}, {23, 24}
        };

        for (int i = 0; i < ranges.length; i++) {
            int[] r = ranges[i];
            LocalDateTime slotStart = date.atTime(r[0], 0);
            LocalDateTime slotEnd = (r[1] == 24) ? date.plusDays(1).atTime(0, 0) : date.atTime(r[1], 0);

            TimeSlotResponseDTO slot;
            // Các slot bảo dưỡng là các slot có chỉ số lẻ trong ranges (1, 3, 5, ...) => i % 2 == 1
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
     * Tạo time slot với thông tin booking (hỗ trợ booking qua đêm)
     */
    private TimeSlotResponseDTO createTimeSlot(Long vehicleId, LocalDateTime start, LocalDateTime end, Long userId) {
        // Kiểm tra xem slot này có được đặt chưa - hỗ trợ booking qua đêm

        // Lấy các booking từ ngày bắt đầu
        List<UsageBooking> bookings = new ArrayList<>(usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, start.toLocalDate()));

        // Nếu slot kéo dài sang ngày hôm sau, lấy các booking từ ngày đó nữa
        if (!end.toLocalDate().equals(start.toLocalDate())) {
            bookings.addAll(usageBookingRepository.findByVehicleIdAndDateWithUser(vehicleId, end.toLocalDate()));
        }

        // Lọc các booking trùng với slot này
        List<UsageBooking> overlapping = bookings.stream()
                .filter(b -> !b.getStartDateTime().isAfter(end) && !b.getEndDateTime().isBefore(start))
                .toList();

        String timeDisplay = formatTimeSlot(start, end);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockThreshold = start.plusMinutes(20);
        
        // Booking CONFIRMED
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

        // Booking COMPLETED
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

        // Booking AWAITING_REVIEW
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

        // Booking NEEDS_ATTENTION
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

        // Kiểm tra slot có bị khóa không (đã qua thời điểm bắt đầu + 20 phút hoặc đã qua thời điểm kết thúc)
        if (now.isAfter(lockThreshold) && now.isBefore(end)) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status("LOCKED")
                    .type("LOCKED")
                    .bookable(false)
                    .build();
        }

        // Slot đã qua thời điểm kết thúc
        if (!end.isAfter(now)) {
            return TimeSlotResponseDTO.builder()
                    .time(timeDisplay)
                    .status("LOCKED")
                    .type("LOCKED")
                    .bookable(false)
                    .build();
        }

        // Slot còn trống và có thể đặt
        return TimeSlotResponseDTO.builder()
                .time(timeDisplay)
                .status("AVAILABLE")
                .type("AVAILABLE")
                .bookable(true)
                .build();
    }

    /**
     * Xác định loại slot dựa trên người đặt và trạng thái check-in
     * param userId ID của người dùng đang xem
     * param confirmedBooking Booking đã được xác nhận
     * return Loại slot: BOOKED_SELF, BOOKED_OTHER, CHECKED_IN_SELF, hoặc CHECKED_IN_OTHER
     */
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
     * Tính số slot quota đã sử dụng cho các booking của người dùng trong tuần này
     * Đếm các slot có loại: BOOKED_SELF, CHECKED_IN_SELF
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
     * Định dạng time slot để hiển thị booking qua đêm
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
     * Lấy các gợi ý cho người dùng dựa trên quota và tính khả dụng
     */
    public List<String> getBookingSuggestions(Long groupId, Long userId, LocalDate weekStart) {
        List<String> suggestions = new ArrayList<>();

        WeeklyCalendarResponseDTO calendar = getWeeklyCalendar(groupId, userId, weekStart);

        // Gợi ý dựa trên quota
        if (calendar.getUserQuota().getRemainingSlots() > 20) {
            suggestions.add("You have " + calendar.getUserQuota().getRemainingSlots() +
                    " unused slots left. Consider booking more to use your quota!");
        }

        if (calendar.getUserQuota().getRemainingSlots() < 5) {
            suggestions.add("You only have " + calendar.getUserQuota().getRemainingSlots() +
                    " slots left. Please book carefully!");
        }

        // Gợi ý dựa trên tính khả dụng
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
     * Tạo booking linh hoạt có thể qua đêm
     * param request Thông tin booking request
     * param userEmail Email của người dùng đặt booking
     * return DTO chứa thông tin booking đã tạo
     */
    @Transactional
    public FlexibleBookingResponseDTO createFlexibleBooking(FlexibleBookingRequestDTO request, String userEmail) {
        // 1. Kiểm tra user và vehicle có tồn tại trong hệ thống không
        var user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        // 2. Kiểm tra các ràng buộc về thời gian đầu vào
        if (request.getStartDateTime() == null || request.getEndDateTime() == null) {
            throw new BookingValidationException("Start time and end time are required");
        }

        // Không cho phép thời gian bắt đầu và kết thúc trùng nhau
        if (request.getStartDateTime().equals(request.getEndDateTime())) {
            throw new BookingValidationException("Start time and end time cannot be the same");
        }

        // Thời gian bắt đầu phải trước thời gian kết thúc
        if (request.getStartDateTime().isAfter(request.getEndDateTime())) {
            throw new BookingValidationException("Start time must be before end time");
        }

        // Lấy thời gian hiện tại theo múi giờ Việt Nam
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

        // 3.5. Kiểm tra overlap với các booking đã tồn tại (CRITICAL: Prevent race condition)
        // Kiểm tra xem có booking nào đã CONFIRMED trùng với khoảng thời gian này không
        List<UsageBooking> overlappingBookings = usageBookingRepository.findAffectedBookings(
                request.getVehicleId(),
                request.getStartDateTime(),
                request.getEndDateTime()
        );
        
        // Lọc chỉ các booking CONFIRMED (loại trừ COMPLETED, CANCELLED, etc.)
        boolean hasOverlap = overlappingBookings.stream()
                .anyMatch(b -> b.getStatus() == BookingStatus.CONFIRMED 
                        || b.getStatus() == BookingStatus.AWAITING_REVIEW
                        || b.getStatus() == BookingStatus.NEEDS_ATTENTION);
        
        if (hasOverlap) {
            throw new BookingValidationException(
                    "This time slot is already booked by another member. Please select a different time slot.");
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

    /**
     * Tạo payload cho QR check-in
     * Payload là chuỗi JSON, chứa thông tin cơ bản để hệ thống xác định đúng booking khi quét QR
     * param booking Booking cần tạo QR code
     * return Chuỗi JSON payload cho QR code
     */
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
