package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.WeeklyCalendarResponse;
import com.group8.evcoownership.dto.FlexibleBookingRequest;
import com.group8.evcoownership.dto.FlexibleBookingResponse;
import com.group8.evcoownership.service.WeeklyCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Tag(name = "Weekly Calendar", description = "Quản lý lịch tuần và quota sử dụng")
public class WeeklyCalendarController {

    private final WeeklyCalendarService weeklyCalendarService;

    @GetMapping("/groups/{groupId}/weekly")
    @Operation(summary = "Lấy lịch tuần", description = "Hiển thị lịch tuần với các slot đã book và quota của user")
    public ResponseEntity<WeeklyCalendarResponse> getWeeklyCalendar(
            @PathVariable Long groupId,
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        
        // Nếu không có weekStart, dùng tuần hiện tại
        if (weekStart == null) {
            weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        }
        
        WeeklyCalendarResponse response = weeklyCalendarService.getWeeklyCalendar(groupId, userId, weekStart);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/groups/{groupId}/suggestions")
    @Operation(summary = "Lấy gợi ý booking", description = "Gợi ý booking dựa trên quota và availability")
    public ResponseEntity<List<String>> getBookingSuggestions(
            @PathVariable Long groupId,
            @RequestParam Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        
        if (weekStart == null) {
            weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        }
        
        List<String> suggestions = weeklyCalendarService.getBookingSuggestions(groupId, userId, weekStart);
        return ResponseEntity.ok(suggestions);
    }

    @PostMapping("/flexible-booking")
    @Operation(summary = "Tạo booking linh hoạt", description = "Tạo booking với thời gian tùy chỉnh, hỗ trợ qua đêm")
    public ResponseEntity<FlexibleBookingResponse> createFlexibleBooking(@RequestBody FlexibleBookingRequest request) {
        FlexibleBookingResponse response = weeklyCalendarService.createFlexibleBooking(request);
        return ResponseEntity.ok(response);
    }
}
