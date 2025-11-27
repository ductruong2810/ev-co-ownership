package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardChartDataDTO {
    // Revenue/Expense theo tháng (cho biểu đồ)
    private List<MonthlyFinancialData> monthlyFinancials;
    
    // Bookings theo tháng
    private List<MonthlyBookingData> monthlyBookings;
    
    // Groups theo tháng (số nhóm mới tạo)
    private List<MonthlyGroupData> monthlyGroups;
    
    // Users theo tháng (số user mới đăng ký)
    private List<MonthlyUserData> monthlyUsers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyFinancialData {
        private String month; // Format: "2025-01"
        private BigDecimal revenue; // Tổng payment COMPLETED
        private BigDecimal expense; // Tổng expense
        private BigDecimal netAmount; // revenue - expense
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyBookingData {
        private String month; // Format: "2025-01"
        private Long totalBookings;
        private Long completedBookings;
        private Long cancelledBookings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyGroupData {
        private String month; // Format: "2025-01"
        private Long newGroups;
        private Long activeGroups;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyUserData {
        private String month; // Format: "2025-01"
        private Long newUsers;
        private Long activeUsers;
    }
}

