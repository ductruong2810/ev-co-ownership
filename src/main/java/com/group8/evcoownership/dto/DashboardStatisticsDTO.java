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
    
    // Tổng số payments
    private Long totalPayments;
    private BigDecimal totalPaymentAmount;
    
    // Tổng số funds
    private Long totalFunds;
    private BigDecimal totalFundBalance;
}

