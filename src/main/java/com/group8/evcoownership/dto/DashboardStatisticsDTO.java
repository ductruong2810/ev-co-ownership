package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatisticsDTO {

    // Nested DTOs for better structure
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentStatistics {
        private Long total;
        private Long pending;
        private Long completed;
        private Long failed;
        private Long refunded;
        private BigDecimal totalAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserStatistics {
        private Long total;
        private Long active;
        private Long banned;
        private Long coOwners;
        private Long staff;
        private Long technicians;
        private Long admins;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupStatistics {
        private Long total;
        private Long pending;
        private Long active;
        private Long closed;
    }

    // Tổng số groups
    private Long totalGroups;
    private Map<String, Long> groupsByStatus; // PENDING, ACTIVE, CLOSED

    // Tổng số users
    private Long totalUsers;
    private Map<String, Long> usersByStatus; // ACTIVE, BANNED
    private Map<String, Long> usersByRole; // CO_OWNER, STAFF, ADMIN, TECHNICIAN

    // Tổng số bookings
    private Long totalBookings;
    private Map<String, Long> bookingsByStatus; // CONFIRMED, COMPLETED, CANCELLED, etc.

    // Tổng số vehicles
    private Long totalVehicles;

    // Tổng số disputes
    private Long totalDisputes;
    private Map<String, Long> disputesByStatus; // OPEN, RESOLVED, REJECTED

    // Tổng số incidents
    private Long totalIncidents;
    private Map<String, Long> incidentsByStatus; // PENDING, APPROVED, REJECTED

    // Tổng số maintenances
    private Long totalMaintenances;
    private Map<String, Long> maintenancesByStatus; // PENDING, APPROVED, IN_PROGRESS, COMPLETED

    // Tổng số contracts
    private Long totalContracts;
    private Map<String, Long> contractsByStatus; // PENDING, APPROVED, REJECTED

    // Tổng số documents pending
    private Long pendingDocuments;

    // Tổng số expenses
    private Long totalExpenses;
    private BigDecimal totalExpenseAmount;

    // Payments statistics - using nested DTO for better structure
    private PaymentStatistics payments;

    // Legacy fields for backward compatibility
    private Long totalPayments;
    private BigDecimal totalPaymentAmount;
    private Map<String, Long> paymentsByStatus; // PENDING, COMPLETED, FAILED, REFUNDED

    // Tổng số funds
    private Long totalFunds;
    private BigDecimal totalFundBalance;

    // Revenue by period (DAY/WEEK/MONTH) - key format: "YYYY-MM-DD", "YYYY-WW", or "YYYY-MM"
    private Map<String, BigDecimal> revenueByPeriod;

    // Previous-period counters so FE can display trend (% vs last period)
    private BigDecimal previousTotalRevenue;
    private Long previousTotalBookings;
    private Long previousTotalGroups;
    private Long previousTotalMaintenances;
    private Long previousTotalDisputes;
}
