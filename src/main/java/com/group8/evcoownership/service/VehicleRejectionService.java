package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.VehicleRejection;
import com.group8.evcoownership.entity.VehicleReport;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.enums.RejectionReason;
import com.group8.evcoownership.enums.RejectionStatus;
import com.group8.evcoownership.enums.ReportType;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRejectionRepository;
import com.group8.evcoownership.repository.VehicleReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleRejectionService {

    private final VehicleRejectionRepository vehicleRejectionRepository;
    private final VehicleReportRepository vehicleReportRepository;
    private final UsageBookingRepository usageBookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // User từ chối sử dụng xe
    public VehicleRejection rejectVehicle(Long bookingId, RejectionReason rejectionReason,
                                          String detailedReason, List<String> photos) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        VehicleRejection rejection = VehicleRejection.builder()
                .vehicle(booking.getVehicle())
                .booking(booking)
                .rejectedBy(booking.getUser())
                .rejectionReason(rejectionReason)
                .detailedReason(detailedReason)
                .photos(String.join(",", photos))
                .status(RejectionStatus.PENDING)
                .rejectedAt(LocalDateTime.now())
                .build();

        // Cancel booking
        booking.setStatus(BookingStatus.Cancelled);
        usageBookingRepository.save(booking);

        // Send notification to technicians
        notificationService.sendNotificationToTechnicians(
                "Vehicle Rejection",
                "User rejected vehicle " + booking.getVehicle().getLicensePlate() +
                        " due to: " + rejectionReason + ". Details: " + detailedReason
        );

        return vehicleRejectionRepository.save(rejection);
    }

    // Technician giải quyết từ chối
    public VehicleReport resolveRejection(Long rejectionId, String resolutionNotes) {
        VehicleRejection rejection = vehicleRejectionRepository.findById(rejectionId)
                .orElseThrow(() -> new EntityNotFoundException("Rejection not found"));

        // Create resolution report
        VehicleReport report = VehicleReport.builder()
                .vehicle(rejection.getVehicle())
                .booking(rejection.getBooking())
                .reportedBy(getCurrentTechnician())
                .reportType(ReportType.REJECTION_RESOLUTION)
                .cleanliness(com.group8.evcoownership.enums.Cleanliness.Good)
                .damages("None")
                .notes(resolutionNotes)
                .rejectionReason(rejection.getRejectionReason().name())
                .resolutionNotes(resolutionNotes)
                .createdAt(LocalDateTime.now())
                .build();

        // Update rejection status
        rejection.setStatus(RejectionStatus.RESOLVED);
        rejection.setResolvedAt(LocalDateTime.now());
        vehicleRejectionRepository.save(rejection);

        // Send notification to user
        notificationService.sendNotification(
                rejection.getRejectedBy(),
                "Vehicle Issue Resolved",
                "The issue with vehicle " + rejection.getVehicle().getLicensePlate() +
                        " has been resolved. You can now book again.",
                com.group8.evcoownership.enums.NotificationType.maintenance
        );

        return vehicleReportRepository.save(report);
    }

    // Lấy danh sách từ chối đang chờ xử lý
    public List<VehicleRejection> getPendingRejections() {
        return vehicleRejectionRepository.findByStatus(RejectionStatus.PENDING);
    }

    // Lấy lịch sử từ chối của xe
    public List<VehicleRejection> getVehicleRejectionHistory(Long vehicleId) {
        return vehicleRejectionRepository.findByVehicleIdOrderByRejectedAtDesc(vehicleId);
    }

    // Helper method - TODO: Implement properly
    private com.group8.evcoownership.entity.User getCurrentTechnician() {
        // This should return the current logged-in technician
        // For now, return a dummy user
        return userRepository.findById(1L).orElse(null);
    }
}
