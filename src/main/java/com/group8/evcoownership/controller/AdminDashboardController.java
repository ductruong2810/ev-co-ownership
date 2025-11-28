package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DashboardChartDataDTO;
import com.group8.evcoownership.dto.DashboardStatisticsDTO;
import com.group8.evcoownership.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Thống kê tổng hợp cho Admin Dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/statistics")
    @Operation(
            summary = "[ADMIN] Thống kê tổng hợp",
            description = "Lấy tất cả thống kê tổng hợp cho Admin Dashboard. Có thể lọc theo khoảng thời gian với query params from và to, và periodType (DAY/WEEK/MONTH) để tính revenue theo period."
    )
    public ResponseEntity<DashboardStatisticsDTO> getDashboardStatistics(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false, defaultValue = "DAY") String periodType
    ) {
        DashboardStatisticsDTO statistics;
        if (from != null || to != null) {
            statistics = dashboardService.getDashboardStatistics(from, to, periodType);
        } else {
            statistics = dashboardService.getDashboardStatistics();
        }
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/chart-data")
    @Operation(
            summary = "[ADMIN] Dữ liệu biểu đồ",
            description = "Lấy dữ liệu cho biểu đồ dashboard: revenue/expense theo tháng, bookings theo tháng, groups/users theo tháng. Mặc định 12 tháng gần nhất."
    )
    public ResponseEntity<DashboardChartDataDTO> getChartData(
            @RequestParam(required = false, defaultValue = "12") Integer months
    ) {
        DashboardChartDataDTO chartData = dashboardService.getChartData(months);
        return ResponseEntity.ok(chartData);
    }
}

