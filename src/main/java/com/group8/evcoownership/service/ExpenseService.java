package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Expense;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.repository.ExpenseRepository;
import com.group8.evcoownership.repository.IncidentRepository;
import com.group8.evcoownership.repository.MaintenanceRepository;
import com.group8.evcoownership.repository.VehicleReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final VehicleReportRepository vehicleReportRepository;
    private final IncidentRepository incidentRepository;

    // Tạo expense cho Maintenance
    public Expense createMaintenanceExpense(Long maintenanceId, String description, BigDecimal amount) {
        // Verify maintenance exists
        maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        Expense expense = Expense.builder()
                .fund(null) // TODO: Implement SharedFund logic later
                .sourceType("MAINTENANCE")
                .sourceId(maintenanceId)
                .description(description)
                .amount(amount)
                .expenseDate(LocalDateTime.now())
                .build();

        return expenseRepository.save(expense);
    }

    // Tạo expense cho VehicleReport
    public Expense createVehicleReportExpense(Long vehicleReportId, String description, BigDecimal amount) {
        // Verify vehicle report exists
        vehicleReportRepository.findById(vehicleReportId)
                .orElseThrow(() -> new EntityNotFoundException("VehicleReport not found"));

        Expense expense = Expense.builder()
                .fund(null) // TODO: Implement SharedFund logic later
                .sourceType("VEHICLE_REPORT")
                .sourceId(vehicleReportId)
                .description(description)
                .amount(amount)
                .expenseDate(LocalDateTime.now())
                .build();

        return expenseRepository.save(expense);
    }

    // Tạo expense cho Incident
    public Expense createIncidentExpense(Long incidentId, String description, BigDecimal amount) {
        // Verify incident exists
        incidentRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found"));

        Expense expense = Expense.builder()
                .fund(null) // TODO: Implement SharedFund logic later
                .sourceType("INCIDENT")
                .sourceId(incidentId)
                .description(description)
                .amount(amount)
                .expenseDate(LocalDateTime.now())
                .build();

        return expenseRepository.save(expense);
    }

    // Lấy tổng chi phí của Maintenance
    public Map<String, Object> getMaintenanceExpenseSummary(Long maintenanceId) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        BigDecimal totalExpenses = expenseRepository.findBySourceTypeAndSourceId("MAINTENANCE", maintenanceId)
                .stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "maintenanceId", maintenanceId,
                "estimatedCost", maintenance.getEstimatedCost(),
                "actualCost", maintenance.getActualCost(),
                "totalExpenses", totalExpenses,
                "variance", maintenance.getActualCost() != null ?
                        maintenance.getActualCost().subtract(totalExpenses) : null
        );
    }
}
