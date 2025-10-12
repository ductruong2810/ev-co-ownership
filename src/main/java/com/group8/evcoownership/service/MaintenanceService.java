package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.MaintenanceRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final UsageBookingService usageBookingService;
    private final NotificationService notificationService;

    // Technician tạo maintenance request (có thể được gọi từ VehicleReportService)
    public Maintenance createMaintenanceRequest(Long vehicleId, Long technicianId, String description, BigDecimal estimatedCost) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new EntityNotFoundException("Technician not found"));

        Maintenance maintenance = Maintenance.builder()
                .vehicle(vehicle)
                .requestedBy(technician.getUserId())
                .requestDate(LocalDateTime.now())
                .description(description)
                .estimatedCost(estimatedCost)
                .maintenanceStatus("PENDING")
                .build();

        return maintenanceRepository.save(maintenance);
    }

    // Staff/Admin approve maintenance và tự động cancel bookings
    public Map<String, Object> approveMaintenanceAndCancelBookings(Long maintenanceId, Long approvedByUserId,
                                                                   LocalDateTime startDateTime, LocalDateTime endDateTime) {
        Maintenance maintenance = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance request not found"));

        User approver = userRepository.findById(approvedByUserId)
                .orElseThrow(() -> new EntityNotFoundException("Approver not found"));

        // Update maintenance status
        maintenance.setApprovedBy(approver);
        maintenance.setApprovalDate(LocalDateTime.now());
        maintenance.setMaintenanceStatus("APPROVED");
        maintenanceRepository.save(maintenance);

        // Tạo maintenance booking để block thời gian
        var maintenanceBookingRequest = new com.group8.evcoownership.dto.MaintenanceBookingRequestDTO();
        maintenanceBookingRequest.setVehicleId(maintenance.getVehicle().getId());
        maintenanceBookingRequest.setStartDateTime(startDateTime);
        maintenanceBookingRequest.setEndDateTime(endDateTime);
        maintenanceBookingRequest.setReason("Maintenance approved: " + maintenance.getDescription());
        maintenanceBookingRequest.setCancelAffectedBookings(true);
        maintenanceBookingRequest.setNotifyUsers(true);

        // Cancel bookings và gửi notifications
        Map<String, Object> result = usageBookingService.createMaintenanceBooking(maintenanceBookingRequest);

        // Gửi notification cho tất cả users trong group về maintenance
        List<User> groupUsers = getUsersInOwnershipGroup(maintenance.getVehicle().getId());
        String title = "Vehicle Maintenance Scheduled";
        String message = String.format("Vehicle %s will be under maintenance from %s to %s. Reason: %s",
                maintenance.getVehicle().getLicensePlate(),
                startDateTime,
                endDateTime,
                maintenance.getDescription());

        notificationService.sendNotificationToGroup(groupUsers, title, message, "MAINTENANCE");

        result.put("maintenanceId", maintenanceId);
        result.put("maintenanceStatus", "Approved");
        result.put("groupNotificationsSent", groupUsers.size());

        return result;
    }

    // Lấy tất cả users trong ownership group của vehicle
    private List<User> getUsersInOwnershipGroup(Long vehicleId) {
        // TODO: Implement logic to get all users in the vehicle's ownership group
        // This would require joining Vehicle -> OwnershipGroup -> OwnershipShare -> User
        return List.of(); // Placeholder
    }
}
