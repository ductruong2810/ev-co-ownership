package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.Cleanliness;
import com.group8.evcoownership.enums.ReportType;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleReportRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleReportService {

    private final VehicleReportRepository vehicleReportRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final UsageBookingRepository usageBookingRepository;
    private final NotificationService notificationService;

    // User tạo check-out report
    public VehicleReport createUserCheckoutReport(Long bookingId, Integer odometer, BigDecimal batteryLevel,
                                                  String damages, String cleanliness, String notes) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        VehicleReport report = VehicleReport.builder()
                .booking(booking)
                .reportedBy(booking.getUser())
                .reportType(ReportType.USER_CHECKOUT)
                .odometer(odometer)
                .batteryLevel(batteryLevel)
                .damages(damages)
                .cleanliness(Cleanliness.valueOf(cleanliness))
                .notes(notes)
                .build();

        return vehicleReportRepository.save(report);
    }

    // Technician xác nhận report và báo cáo thêm lỗi
    public VehicleReport createTechnicianVerification(Long userReportId, String technicianNotes,
                                                      String additionalDamages, String additionalNotes) {
        VehicleReport userReport = vehicleReportRepository.findById(userReportId)
                .orElseThrow(() -> new EntityNotFoundException("User report not found"));

        // Kết hợp lỗi từ user và technician
        String combinedDamages = userReport.getDamages();
        if (additionalDamages != null && !additionalDamages.trim().isEmpty()) {
            combinedDamages += " | Additional issues found by technician: " + additionalDamages;
        }

        String combinedNotes = technicianNotes;
        if (additionalNotes != null && !additionalNotes.trim().isEmpty()) {
            combinedNotes += " | Additional notes: " + additionalNotes;
        }

        VehicleReport verification = VehicleReport.builder()
                .booking(userReport.getBooking())
                .reportedBy(getCurrentTechnician())
                .reportType(ReportType.TECHNICIAN_VERIFICATION)
                .odometer(userReport.getOdometer())
                .batteryLevel(userReport.getBatteryLevel())
                .damages(combinedDamages)
                .cleanliness(userReport.getCleanliness())
                .notes(combinedNotes)
                .build();

        VehicleReport savedVerification = vehicleReportRepository.save(verification);

        // Gửi notification cho user về lỗi bổ sung
        if (additionalDamages != null && !additionalDamages.trim().isEmpty()) {
            notificationService.sendNotification(
                    userReport.getBooking().getUser(),
                    "Additional Issues Found",
                    "Technician found additional issues with your vehicle: " + additionalDamages,
                    com.group8.evcoownership.enums.NotificationType.maintenance
            );
        }

        return savedVerification;
    }

    // Lấy thông tin xe cho user check-in
    public Optional<VehicleReport> getVehicleInfoForCheckin(Long vehicleId) {
        return vehicleReportRepository.findTop1ByVehicleIdAndReportTypeOrderByCreatedAtDesc(
                vehicleId, ReportType.TECHNICIAN_VERIFICATION
        );
    }

    // Kiểm tra pin có đủ cho user tiếp theo không
    public Boolean isBatterySufficient(Long vehicleId, Integer requiredBattery) {
        Optional<VehicleReport> latestReport = getVehicleInfoForCheckin(vehicleId);
        if (latestReport.isPresent()) {
            BigDecimal currentBattery = latestReport.get().getBatteryLevel();
            return currentBattery.compareTo(new BigDecimal(requiredBattery)) >= 0;
        }
        return false;
    }

    // Technician tạo vehicle report (legacy method)
    public Map<String, Object> createVehicleReport(Long vehicleId, Long technicianId, ReportType reportType,
                                                   Integer mileage, BigDecimal chargeLevel, String damages,
                                                   String cleanliness, String notes) {

        // Verify vehicle exists
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new EntityNotFoundException("Technician not found"));

        VehicleReport report = VehicleReport.builder()
                .reportedBy(technician)
                .reportType(reportType)
                .odometer(mileage)
                .batteryLevel(chargeLevel)
                .damages(damages)
                .cleanliness(Cleanliness.valueOf(cleanliness))
                .notes(notes)
                .build();

        VehicleReport savedReport = vehicleReportRepository.save(report);

        // Note: Technician sẽ tự quyết định có tạo Maintenance request hay không
        // Không tự động tạo để tránh sai sót trong phân loại

        return Map.of(
                "reportId", savedReport.getId(),
                "reportType", reportType.name(),
                "vehicleLicensePlate", vehicle.getLicensePlate(),
                "message", "Vehicle report created successfully"
        );
    }

    // Technician phát hiện lỗi nghiêm trọng và tạo Maintenance request
    public Map<String, Object> createMaintenanceFromTechnicianFindings(Long userReportId, String criticalIssues,
                                                                       String maintenanceDescription) {
        VehicleReport userReport = vehicleReportRepository.findById(userReportId)
                .orElseThrow(() -> new EntityNotFoundException("User report not found"));

        // Tạo Maintenance request
        MaintenanceService maintenanceService = new MaintenanceService(null, null, null, null, null); // TODO: Inject properly
        Maintenance maintenance = maintenanceService.createMaintenanceRequest(
                userReport.getBooking().getVehicle().getId(),
                getCurrentTechnician().getUserId(),
                "Critical issues found by technician: " + criticalIssues + " | " + maintenanceDescription,
                java.math.BigDecimal.ZERO
        );

        // Gửi notification cho tất cả users trong group
        notificationService.sendNotificationToGroup(
                getUsersInOwnershipGroup(),
                "Critical Vehicle Issues Found",
                "Technician found critical issues with vehicle " + userReport.getBooking().getVehicle().getLicensePlate() +
                        ": " + criticalIssues + ". Maintenance request created.",
                com.group8.evcoownership.enums.NotificationType.maintenance
        );

        return Map.of(
                "maintenanceId", maintenance.getId(),
                "message", "Maintenance request created due to critical issues found by technician",
                "criticalIssues", criticalIssues
        );
    }

    // Helper method - TODO: Implement properly
    private User getCurrentTechnician() {
        // This should return the current logged-in technician
        return userRepository.findById(1L).orElse(null);
    }

    // Helper method - TODO: Implement properly
    private java.util.List<User> getUsersInOwnershipGroup() {
        // This should return all users in the vehicle's ownership group
        // For now, return an empty list
        return java.util.List.of();
    }

}
