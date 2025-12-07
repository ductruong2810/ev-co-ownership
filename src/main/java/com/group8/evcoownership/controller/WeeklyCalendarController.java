package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FlexibleBookingRequestDTO;
import com.group8.evcoownership.dto.FlexibleBookingResponseDTO;
import com.group8.evcoownership.dto.SmartSuggestionResponseDTO;
import com.group8.evcoownership.dto.UsageAnalyticsDTO;
import com.group8.evcoownership.dto.WeeklyCalendarResponseDTO;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.WeeklyCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Tag(name = "Weekly Calendar", description = "Quản lý lịch tuần và quota sử dụng")
@PreAuthorize("isAuthenticated()")
public class WeeklyCalendarController {

    private final WeeklyCalendarService weeklyCalendarService;
    private final UserRepository userRepository;

    @GetMapping("/groups/{groupId}/weekly")
    @Operation(summary = "Lấy lịch tuần", description = "Hiển thị lịch tuần với các slot đã book và quota của user")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','CO_OWNER')")
    public ResponseEntity<WeeklyCalendarResponseDTO> getWeeklyCalendar(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal String email) {

        // Lấy userId từ JWT
        Long userId = getUserIdByEmail(email);

        WeeklyCalendarResponseDTO response = weeklyCalendarService.getWeeklyCalendar(groupId, userId, weekStart);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/groups/{groupId}/suggestions")
    @Operation(summary = "Lấy gợi ý booking", description = "Gợi ý booking dựa trên quota và availability")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public ResponseEntity<List<String>> getBookingSuggestions(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @AuthenticationPrincipal String email) {

        // Lấy userId từ JWT
        Long userId = getUserIdByEmail(email);

        List<String> suggestions = weeklyCalendarService.getBookingSuggestions(groupId, userId, weekStart);
        return ResponseEntity.ok(suggestions);
    }

    //======= Tạo booking linh hoạt =======
    @PostMapping("/flexible-booking")
    @Operation(summary = "Tạo booking linh hoạt", description = "Tạo booking với thời gian tùy chỉnh, có thể qua đêm, dựa trên quota và lịch trống")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public ResponseEntity<FlexibleBookingResponseDTO> createFlexibleBooking(
            @RequestBody FlexibleBookingRequestDTO request,   // chứa groupId, thời gian bắt đầu/kết thúc, ghi chú
            @AuthenticationPrincipal String email             // email user hiện tại lấy từ JWT
    ) {
        // Gọi service xử lý logic tạo booking linh hoạt cho user này trong group tương ứng
        FlexibleBookingResponseDTO response = weeklyCalendarService.createFlexibleBooking(request, email);

        // Trả về thông tin booking vừa tạo (thời gian, trạng thái)
        return ResponseEntity.ok(response);
    }

    @GetMapping("/groups/{groupId}/usage-report")
    @Operation(summary = "Lấy báo cáo sử dụng", description = "Lấy báo cáo phân tích sử dụng xe cho user trong group, bao gồm fairness status, quota usage, và action items")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public ResponseEntity<UsageAnalyticsDTO> getUsageReport(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String email) {

        // Lấy userId từ JWT
        Long userId = getUserIdByEmail(email);

        UsageAnalyticsDTO response = weeklyCalendarService.getUsageReport(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/groups/{groupId}/smart-insights")
    @Operation(summary = "Lấy smart insights", description = "Lấy smart insights bao gồm analytics, suggestions và AI insights cho user trong group")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public ResponseEntity<SmartSuggestionResponseDTO> getSmartInsights(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String email) {

        // Lấy userId từ JWT
        Long userId = getUserIdByEmail(email);

        SmartSuggestionResponseDTO response = weeklyCalendarService.getSmartInsights(groupId, userId);
        return ResponseEntity.ok(response);
    }

    private Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email))
                .getUserId();
    }
}
