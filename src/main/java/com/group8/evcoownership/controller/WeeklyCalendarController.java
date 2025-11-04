package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FlexibleBookingRequestDTO;
import com.group8.evcoownership.dto.FlexibleBookingResponseDTO;
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
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER', 'TECHNICIAN')")
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

    @PostMapping("/flexible-booking")
    @Operation(summary = "Tạo booking linh hoạt", description = "Tạo booking với thời gian tùy chỉnh, hỗ trợ qua đêm")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public ResponseEntity<FlexibleBookingResponseDTO> createFlexibleBooking(
            @RequestBody FlexibleBookingRequestDTO request,
            @AuthenticationPrincipal String email) {

        FlexibleBookingResponseDTO response = weeklyCalendarService.createFlexibleBooking(request, email);
        return ResponseEntity.ok(response);
    }

    private Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email))
                .getUserId();
    }
}
