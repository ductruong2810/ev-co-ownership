package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DashboardChartDataDTO;
import com.group8.evcoownership.dto.DashboardStatisticsDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.*;
import com.group8.evcoownership.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final OwnershipGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final UsageBookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DisputeRepository disputeRepository;
    private final IncidentRepository incidentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final ContractRepository contractRepository;
    private final UserDocumentRepository documentRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final SharedFundRepository fundRepository;

    /**
     * Lấy thống kê tổng hợp cho Admin Dashboard
     */
    public DashboardStatisticsDTO getDashboardStatistics() {
        log.info("Fetching dashboard statistics for admin");

        DashboardStatisticsDTO.DashboardStatisticsDTOBuilder builder = DashboardStatisticsDTO.builder();

        // 1. Groups statistics
        List<OwnershipGroup> allGroups = groupRepository.findAll();
        builder.totalGroups((long) allGroups.size());
        builder.groupsByStatus(
                allGroups.stream()
                        .collect(Collectors.groupingBy(
                                g -> g.getStatus() != null ? g.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // 2. Users statistics
        List<User> allUsers = userRepository.findAll();
        builder.totalUsers((long) allUsers.size());
        builder.usersByStatus(
                allUsers.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getStatus() != null ? u.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );
        builder.usersByRole(
                allUsers.stream()
                        .filter(u -> u.getRole() != null)
                        .collect(Collectors.groupingBy(
                                u -> u.getRole().getRoleName().name(),
                                Collectors.counting()
                        ))
        );

        // 3. Bookings statistics
        List<UsageBooking> allBookings = bookingRepository.findAll();
        builder.totalBookings((long) allBookings.size());
        builder.bookingsByStatus(
                allBookings.stream()
                        .collect(Collectors.groupingBy(
                                b -> b.getStatus() != null ? b.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // 4. Vehicles statistics
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        builder.totalVehicles((long) allVehicles.size());

        // 5. Disputes statistics
        List<Dispute> allDisputes = disputeRepository.findAll();
        builder.totalDisputes((long) allDisputes.size());
        builder.disputesByStatus(
                allDisputes.stream()
                        .collect(Collectors.groupingBy(
                                d -> d.getStatus() != null ? d.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // 6. Incidents statistics
        List<Incident> allIncidents = incidentRepository.findAll();
        builder.totalIncidents((long) allIncidents.size());
        builder.incidentsByStatus(
                allIncidents.stream()
                        .collect(Collectors.groupingBy(
                                i -> i.getStatus() != null ? i.getStatus() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // 7. Maintenances statistics
        List<Maintenance> allMaintenances = maintenanceRepository.findAll();
        builder.totalMaintenances((long) allMaintenances.size());
        builder.maintenancesByStatus(
                allMaintenances.stream()
                        .collect(Collectors.groupingBy(
                                m -> m.getStatus() != null ? m.getStatus() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // 8. Contracts statistics
        List<Contract> allContracts = contractRepository.findAll();
        builder.totalContracts((long) allContracts.size());
        builder.contractsByStatus(
                allContracts.stream()
                        .collect(Collectors.groupingBy(
                                c -> c.getApprovalStatus() != null ? c.getApprovalStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // 9. Pending documents
        long pendingDocs = documentRepository.findAll().stream()
                .filter(doc -> "PENDING".equals(doc.getStatus()))
                .count();
        builder.pendingDocuments(pendingDocs);

        // 10. Expenses statistics
        List<Expense> allExpenses = expenseRepository.findAll();
        builder.totalExpenses((long) allExpenses.size());
        BigDecimal totalExpenseAmount = allExpenses.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.totalExpenseAmount(totalExpenseAmount);

        // 11. Payments statistics
        List<Payment> allPayments = paymentRepository.findAll();
        builder.totalPayments((long) allPayments.size());
        BigDecimal totalPaymentAmount = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.totalPaymentAmount(totalPaymentAmount);

        // 12. Funds statistics
        List<SharedFund> allFunds = fundRepository.findAll();
        builder.totalFunds((long) allFunds.size());
        BigDecimal totalFundBalance = allFunds.stream()
                .map(f -> f.getBalance() != null ? f.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.totalFundBalance(totalFundBalance);

        return builder.build();
    }

    /**
     * Lấy thống kê tổng hợp với date range filter
     */
    public DashboardStatisticsDTO getDashboardStatistics(LocalDateTime from, LocalDateTime to) {
        log.info("Fetching dashboard statistics for admin with date range: {} to {}", from, to);

        DashboardStatisticsDTO.DashboardStatisticsDTOBuilder builder = DashboardStatisticsDTO.builder();

        // Filter data by date range
        List<OwnershipGroup> allGroups = groupRepository.findAll();
        if (from != null || to != null) {
            allGroups = allGroups.stream()
                    .filter(g -> {
                        if (g.getCreatedAt() == null) return false;
                        if (from != null && g.getCreatedAt().isBefore(from)) return false;
                        if (to != null && g.getCreatedAt().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalGroups((long) allGroups.size());
        builder.groupsByStatus(
                allGroups.stream()
                        .collect(Collectors.groupingBy(
                                g -> g.getStatus() != null ? g.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // Users - filter by createdAt
        List<User> allUsers = userRepository.findAll();
        if (from != null || to != null) {
            allUsers = allUsers.stream()
                    .filter(u -> {
                        if (u.getCreatedAt() == null) return false;
                        if (from != null && u.getCreatedAt().isBefore(from)) return false;
                        if (to != null && u.getCreatedAt().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalUsers((long) allUsers.size());
        builder.usersByStatus(
                allUsers.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getStatus() != null ? u.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );
        builder.usersByRole(
                allUsers.stream()
                        .filter(u -> u.getRole() != null)
                        .collect(Collectors.groupingBy(
                                u -> u.getRole().getRoleName().name(),
                                Collectors.counting()
                        ))
        );

        // Bookings - filter by startDateTime
        List<UsageBooking> allBookings = bookingRepository.findAll();
        if (from != null || to != null) {
            allBookings = allBookings.stream()
                    .filter(b -> {
                        if (b.getStartDateTime() == null) return false;
                        if (from != null && b.getStartDateTime().isBefore(from)) return false;
                        if (to != null && b.getStartDateTime().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalBookings((long) allBookings.size());
        builder.bookingsByStatus(
                allBookings.stream()
                        .collect(Collectors.groupingBy(
                                b -> b.getStatus() != null ? b.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // Vehicles
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        builder.totalVehicles((long) allVehicles.size());

        // Disputes - filter by createdAt
        List<Dispute> allDisputes = disputeRepository.findAll();
        if (from != null || to != null) {
            allDisputes = allDisputes.stream()
                    .filter(d -> {
                        if (d.getCreatedAt() == null) return false;
                        if (from != null && d.getCreatedAt().isBefore(from)) return false;
                        if (to != null && d.getCreatedAt().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalDisputes((long) allDisputes.size());
        builder.disputesByStatus(
                allDisputes.stream()
                        .collect(Collectors.groupingBy(
                                d -> d.getStatus() != null ? d.getStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // Incidents - filter by createdAt
        List<Incident> allIncidents = incidentRepository.findAll();
        if (from != null || to != null) {
            allIncidents = allIncidents.stream()
                    .filter(i -> {
                        if (i.getCreatedAt() == null) return false;
                        if (from != null && i.getCreatedAt().isBefore(from)) return false;
                        if (to != null && i.getCreatedAt().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalIncidents((long) allIncidents.size());
        builder.incidentsByStatus(
                allIncidents.stream()
                        .collect(Collectors.groupingBy(
                                i -> i.getStatus() != null ? i.getStatus() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // Maintenances - filter by requestDate
        List<Maintenance> allMaintenances = maintenanceRepository.findAll();
        if (from != null || to != null) {
            allMaintenances = allMaintenances.stream()
                    .filter(m -> {
                        if (m.getRequestDate() == null) return false;
                        if (from != null && m.getRequestDate().isBefore(from)) return false;
                        if (to != null && m.getRequestDate().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalMaintenances((long) allMaintenances.size());
        builder.maintenancesByStatus(
                allMaintenances.stream()
                        .collect(Collectors.groupingBy(
                                m -> m.getStatus() != null ? m.getStatus() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // Contracts - filter by createdAt
        List<Contract> allContracts = contractRepository.findAll();
        if (from != null || to != null) {
            allContracts = allContracts.stream()
                    .filter(c -> {
                        if (c.getCreatedAt() == null) return false;
                        if (from != null && c.getCreatedAt().isBefore(from)) return false;
                        if (to != null && c.getCreatedAt().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalContracts((long) allContracts.size());
        builder.contractsByStatus(
                allContracts.stream()
                        .collect(Collectors.groupingBy(
                                c -> c.getApprovalStatus() != null ? c.getApprovalStatus().name() : "UNKNOWN",
                                Collectors.counting()
                        ))
        );

        // Pending documents (no date filter)
        long pendingDocs = documentRepository.findAll().stream()
                .filter(doc -> "PENDING".equals(doc.getStatus()))
                .count();
        builder.pendingDocuments(pendingDocs);

        // Expenses - filter by createdAt
        List<Expense> allExpenses = expenseRepository.findAll();
        if (from != null || to != null) {
            allExpenses = allExpenses.stream()
                    .filter(e -> {
                        if (e.getCreatedAt() == null) return false;
                        if (from != null && e.getCreatedAt().isBefore(from)) return false;
                        if (to != null && e.getCreatedAt().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalExpenses((long) allExpenses.size());
        BigDecimal totalExpenseAmount = allExpenses.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.totalExpenseAmount(totalExpenseAmount);

        // Payments - filter by paymentDate
        List<Payment> allPayments = paymentRepository.findAll();
        if (from != null || to != null) {
            allPayments = allPayments.stream()
                    .filter(p -> {
                        if (p.getPaymentDate() == null) return false;
                        if (from != null && p.getPaymentDate().isBefore(from)) return false;
                        if (to != null && p.getPaymentDate().isAfter(to)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        builder.totalPayments((long) allPayments.size());
        BigDecimal totalPaymentAmount = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.totalPaymentAmount(totalPaymentAmount);

        // Funds (no date filter - current balance)
        List<SharedFund> allFunds = fundRepository.findAll();
        builder.totalFunds((long) allFunds.size());
        BigDecimal totalFundBalance = allFunds.stream()
                .map(f -> f.getBalance() != null ? f.getBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        builder.totalFundBalance(totalFundBalance);

        return builder.build();
    }

    /**
     * Lấy dữ liệu cho biểu đồ (theo tháng)
     * @param months Số tháng cần lấy (mặc định 12 tháng gần nhất)
     */
    public DashboardChartDataDTO getChartData(Integer months) {
        log.info("Fetching chart data for admin dashboard - months: {}", months);
        
        if (months == null || months <= 0) {
            months = 12; // Default 12 months
        }

        LocalDateTime now = LocalDateTime.now();

        List<DashboardChartDataDTO.MonthlyFinancialData> monthlyFinancials = new ArrayList<>();
        List<DashboardChartDataDTO.MonthlyBookingData> monthlyBookings = new ArrayList<>();
        List<DashboardChartDataDTO.MonthlyGroupData> monthlyGroups = new ArrayList<>();
        List<DashboardChartDataDTO.MonthlyUserData> monthlyUsers = new ArrayList<>();

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // Generate months
        for (int i = months - 1; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);
            String monthKey = monthStart.format(monthFormatter);

            // Financial data (Payments and Expenses)
            List<Payment> monthPayments = paymentRepository.findAll().stream()
                    .filter(p -> p.getPaymentDate() != null
                            && p.getPaymentDate().isAfter(monthStart.minusSeconds(1))
                            && p.getPaymentDate().isBefore(monthEnd.plusSeconds(1))
                            && p.getStatus() == PaymentStatus.COMPLETED)
                    .collect(Collectors.toList());
            BigDecimal revenue = monthPayments.stream()
                    .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Expense> monthExpenses = expenseRepository.findAll().stream()
                    .filter(e -> e.getCreatedAt() != null
                            && e.getCreatedAt().isAfter(monthStart.minusSeconds(1))
                            && e.getCreatedAt().isBefore(monthEnd.plusSeconds(1)))
                    .collect(Collectors.toList());
            BigDecimal expense = monthExpenses.stream()
                    .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthlyFinancials.add(DashboardChartDataDTO.MonthlyFinancialData.builder()
                    .month(monthKey)
                    .revenue(revenue)
                    .expense(expense)
                    .netAmount(revenue.subtract(expense))
                    .build());

            // Booking data
            long totalBookings = bookingRepository.findAll().stream()
                    .filter(b -> b.getStartDateTime() != null
                            && b.getStartDateTime().isAfter(monthStart.minusSeconds(1))
                            && b.getStartDateTime().isBefore(monthEnd.plusSeconds(1)))
                    .count();
            long completedBookings = bookingRepository.findAll().stream()
                    .filter(b -> b.getStartDateTime() != null
                            && b.getStartDateTime().isAfter(monthStart.minusSeconds(1))
                            && b.getStartDateTime().isBefore(monthEnd.plusSeconds(1))
                            && "COMPLETED".equals(b.getStatus() != null ? b.getStatus().name() : null))
                    .count();
            long cancelledBookings = bookingRepository.findAll().stream()
                    .filter(b -> b.getStartDateTime() != null
                            && b.getStartDateTime().isAfter(monthStart.minusSeconds(1))
                            && b.getStartDateTime().isBefore(monthEnd.plusSeconds(1))
                            && "CANCELLED".equals(b.getStatus() != null ? b.getStatus().name() : null))
                    .count();

            monthlyBookings.add(DashboardChartDataDTO.MonthlyBookingData.builder()
                    .month(monthKey)
                    .totalBookings(totalBookings)
                    .completedBookings(completedBookings)
                    .cancelledBookings(cancelledBookings)
                    .build());

            // Group data
            long newGroups = groupRepository.findAll().stream()
                    .filter(g -> g.getCreatedAt() != null
                            && g.getCreatedAt().isAfter(monthStart.minusSeconds(1))
                            && g.getCreatedAt().isBefore(monthEnd.plusSeconds(1)))
                    .count();
            long activeGroups = groupRepository.findAll().stream()
                    .filter(g -> g.getStatus() == GroupStatus.ACTIVE
                            && (g.getCreatedAt() == null || g.getCreatedAt().isBefore(monthEnd.plusSeconds(1))))
                    .count();

            monthlyGroups.add(DashboardChartDataDTO.MonthlyGroupData.builder()
                    .month(monthKey)
                    .newGroups(newGroups)
                    .activeGroups(activeGroups)
                    .build());

            // User data
            long newUsers = userRepository.findAll().stream()
                    .filter(u -> u.getCreatedAt() != null
                            && u.getCreatedAt().isAfter(monthStart.minusSeconds(1))
                            && u.getCreatedAt().isBefore(monthEnd.plusSeconds(1)))
                    .count();
            long activeUsers = userRepository.findAll().stream()
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE
                            && (u.getCreatedAt() == null || u.getCreatedAt().isBefore(monthEnd.plusSeconds(1))))
                    .count();

            monthlyUsers.add(DashboardChartDataDTO.MonthlyUserData.builder()
                    .month(monthKey)
                    .newUsers(newUsers)
                    .activeUsers(activeUsers)
                    .build());
        }

        return DashboardChartDataDTO.builder()
                .monthlyFinancials(monthlyFinancials)
                .monthlyBookings(monthlyBookings)
                .monthlyGroups(monthlyGroups)
                .monthlyUsers(monthlyUsers)
                .build();
    }
}

