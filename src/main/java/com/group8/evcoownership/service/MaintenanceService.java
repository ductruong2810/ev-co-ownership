package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.MaintenanceBookingRequestDTO;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.NotificationType;
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
    private final NotificationOrchestrator notificationOrchestrator;

    // Technician tạo maintenance request (có thể được gọi từ VehicleReportService)
    public Maintenance createMaintenanceRequest(Long vehicleId, Long technicianId, String description, BigDecimal estimatedCost) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new EntityNotFoundException("Technician not found"));

        Maintenance maintenance = Maintenance.builder()
                .vehicle(vehicle)
                .requestedBy(technician)
                .createdAt(LocalDateTime.now())
                .description(description)
                .actualCost(estimatedCost)
                .status("PENDING")
                .build();

        Maintenance savedMaintenance = maintenanceRepository.save(maintenance);

        // Send maintenance request notification to group members (include rich data for email)
        java.util.Map<String, Object> emailData = new java.util.HashMap<>();
        emailData.put("groupId", vehicle.getOwnershipGroup().getGroupId());
        emailData.put("vehicleName", vehicle.getBrand() + " " + vehicle.getModel());
        emailData.put("description", description);
        emailData.put("estimatedCost", estimatedCost);
        emailData.put("status", "PENDING");

        notificationOrchestrator.sendGroupNotification(
                vehicle.getOwnershipGroup().getGroupId(),
                NotificationType.MAINTENANCE_REQUESTED,
                "Maintenance Requested",
                String.format("Maintenance has been requested for vehicle %s: %s",
                        vehicle.getLicensePlate(), description),
                emailData
        );

        return savedMaintenance;
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
        maintenance.setStatus("APPROVED");
        maintenanceRepository.save(maintenance);

        // Tạo maintenance booking để block thời gian
        var maintenanceBookingRequest = createMaintenanceBookingRequest(maintenance, startDateTime, endDateTime);

        // Cancel bookings và gửi notifications
        Map<String, Object> result = usageBookingService.createMaintenanceBooking(maintenanceBookingRequest);

        // Gửi notification cho tất cả users trong group về maintenance
        List<User> groupUsers = getUsersInOwnershipGroup();
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
    private List<User> getUsersInOwnershipGroup() {
        // TODO: Implement logic to get all users in the vehicle's ownership group
        // This would require joining Vehicle -> OwnershipGroup -> OwnershipShare -> User
        return List.of(); // Placeholder
    }

    private MaintenanceBookingRequestDTO createMaintenanceBookingRequest(
            Maintenance maintenance,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        var maintenanceBookingRequest = new com.group8.evcoownership.dto.MaintenanceBookingRequestDTO();
        maintenanceBookingRequest.setVehicleId(maintenance.getVehicle().getId());
        maintenanceBookingRequest.setStartDateTime(startDateTime);
        maintenanceBookingRequest.setEndDateTime(endDateTime);
        maintenanceBookingRequest.setReason("Maintenance approved: " + maintenance.getDescription());
        maintenanceBookingRequest.setCancelAffectedBookings(true);
        maintenanceBookingRequest.setNotifyUsers(true);
        return maintenanceBookingRequest;
    }
}
