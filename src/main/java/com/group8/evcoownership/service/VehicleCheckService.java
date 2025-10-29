package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.VehicleCheckRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleCheckService {

    private final VehicleCheckRepository vehicleCheckRepository;
    private final UsageBookingRepository usageBookingRepository;
    private final VehicleRepository vehicleRepository;

    // User tạo pre-use check với validation
    public VehicleCheck createPreUseCheck(Long bookingId, Long userId, Integer odometer,
                                          BigDecimal batteryLevel, String cleanliness,
                                          String notes, String issues) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        // VALIDATION: Kiểm tra booking có thuộc về user không
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new SecurityException("You can only check-in your own bookings");
        }

        // VALIDATION: Kiểm tra booking status
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be checked-in");
        }

        // VALIDATION: Kiểm tra thời gian check-in
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(booking.getStartDateTime().minusMinutes(15))) {
            throw new IllegalStateException("Cannot check-in more than 15 minutes before booking time");
        }
        if (now.isAfter(booking.getEndDateTime())) {
            throw new IllegalStateException("Booking time has expired");
        }

        // VALIDATION: Kiểm tra đã làm pre-use check chưa
        if (hasCheck(bookingId, "PRE_USE")) {
            throw new IllegalStateException("Pre-use check already completed for this booking");
        }

        VehicleCheck check = VehicleCheck.builder()
                .booking(booking)
                .checkType("PRE_USE")
                .odometer(odometer)
                .batteryLevel(batteryLevel)
                .cleanliness(cleanliness)
                .notes(notes)
                .issues(issues)
                .status("PENDING")
                .build();

        return vehicleCheckRepository.save(check);
    }

    // User tạo post-use check với validation
    public VehicleCheck createPostUseCheck(Long bookingId, Long userId, Integer odometer,
                                           BigDecimal batteryLevel, String cleanliness,
                                           String notes, String issues) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        // VALIDATION: Kiểm tra booking có thuộc về user không
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new SecurityException("You can only check-in your own bookings");
        }

        // VALIDATION: Kiểm tra booking status
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be checked-out");
        }

        // VALIDATION: Kiểm tra đã làm pre-use check chưa
        if (!hasCheck(bookingId, "PRE_USE")) {
            throw new IllegalStateException("Must complete pre-use check before post-use check");
        }

        // VALIDATION: Kiểm tra đã làm post-use check chưa
        if (hasCheck(bookingId, "POST_USE")) {
            throw new IllegalStateException("Post-use check already completed for this booking");
        }

        VehicleCheck check = VehicleCheck.builder()
                .booking(booking)
                .checkType("POST_USE")
                .odometer(odometer)
                .batteryLevel(batteryLevel)
                .cleanliness(cleanliness)
                .notes(notes)
                .issues(issues)
                .status("PENDING")
                .build();

        return vehicleCheckRepository.save(check);
    }

    // User từ chối xe với validation
    public VehicleCheck rejectVehicle(Long bookingId, Long userId, String issues, String notes) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        // VALIDATION: Kiểm tra booking có thuộc về user không
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new SecurityException("You can only reject your own bookings");
        }

        // VALIDATION: Kiểm tra booking status
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be rejected");
        }

        VehicleCheck check = VehicleCheck.builder()
                .booking(booking)
                .checkType("REJECTION")
                .issues(issues)
                .notes(notes)
                .status("REJECTED")
                .build();

        // Cancel booking
        booking.setStatus(BookingStatus.CANCELLED);
        usageBookingRepository.save(booking);

        return vehicleCheckRepository.save(check);
    }

    // Technician approve/reject check
    public VehicleCheck updateCheckStatus(Long checkId, String status, String notes) {
        VehicleCheck check = vehicleCheckRepository.findById(checkId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle check not found"));

        check.setStatus(status);
        if (notes != null) {
            check.setNotes(check.getNotes() + " | Technician notes: " + notes);
        }

        return vehicleCheckRepository.save(check);
    }

    // Lấy checks của booking
    public List<VehicleCheck> getChecksByBookingId(Long bookingId) {
        return vehicleCheckRepository.findByBookingId(bookingId);
    }

    // Lấy checks của vehicle
    public List<VehicleCheck> getChecksByVehicleId(Long vehicleId) {
        return vehicleCheckRepository.findByVehicleId(vehicleId);
    }

    // Lấy checks theo status
    public List<VehicleCheck> getChecksByStatus(String status) {
        return vehicleCheckRepository.findByStatus(status);
    }

    // Kiểm tra user đã làm check chưa
    public Boolean hasCheck(Long bookingId, String checkType) {
        return vehicleCheckRepository.findByBookingId(bookingId)
                .stream()
                .anyMatch(check -> checkType.equals(check.getCheckType()));
    }

    /**
     * Xử lý QR code check-in - Tìm booking active của user cho vehicle
     */
    public Map<String, Object> processQrCheckIn(String qrCode, Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Parse QR code để lấy vehicle/group info
            Long vehicleId = parseQrCode(qrCode);
            if (vehicleId == null) {
                result.put("success", false);
                result.put("message", "Invalid QR code format");
                return result;
            }

            // Tìm vehicle
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

            // Tìm booking active của user cho vehicle này
            List<UsageBooking> activeBookings = usageBookingRepository.findActiveBookingsByUserAndVehicle(userId, vehicleId);

            if (activeBookings.isEmpty()) {
                result.put("success", false);
                result.put("message", "No active booking found for this vehicle");
                result.put("vehicleInfo", Map.of(
                        "vehicleId", vehicle.getId(),
                        "brand", vehicle.getBrand(),
                        "model", vehicle.getModel(),
                        "licensePlate", vehicle.getLicensePlate()
                ));
                return result;
            }

            // Lấy booking gần nhất
            UsageBooking booking = activeBookings.get(0);

            // Kiểm tra thời gian
            LocalDateTime now = LocalDateTime.now();
            boolean canCheckIn = now.isAfter(booking.getStartDateTime().minusMinutes(15))
                    && now.isBefore(booking.getEndDateTime());

            result.put("success", true);
            result.put("bookingId", booking.getId());
            result.put("vehicleInfo", Map.of(
                    "vehicleId", vehicle.getId(),
                    "brand", vehicle.getBrand(),
                    "model", vehicle.getModel(),
                    "licensePlate", vehicle.getLicensePlate()
            ));
            result.put("bookingInfo", Map.of(
                    "startTime", booking.getStartDateTime(),
                    "endTime", booking.getEndDateTime(),
                    "status", booking.getStatus().toString()
            ));
            result.put("canCheckIn", canCheckIn);
            result.put("hasPreUseCheck", hasCheck(booking.getId(), "PRE_USE"));
            result.put("hasPostUseCheck", hasCheck(booking.getId(), "POST_USE"));

            if (!canCheckIn) {
                result.put("message", "Booking time is not valid for check-in");
            } else {
                result.put("message", "Ready for check-in");
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error processing QR code: " + e.getMessage());
        }

        return result;
    }

    /**
     * Parse QR code để lấy vehicle ID
     * QR format: "GROUP:groupId" hoặc "VEHICLE:vehicleId"
     */
    private Long parseQrCode(String qrCode) {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            return null;
        }

        try {
            if (qrCode.startsWith("GROUP:")) {
                Long groupId = Long.parseLong(qrCode.substring(6));
                // Tìm vehicle theo group ID
                return vehicleRepository.findByOwnershipGroup_GroupId(groupId)
                        .map(Vehicle::getId)
                        .orElse(null);
            } else if (qrCode.startsWith("VEHICLE:")) {
                return Long.parseLong(qrCode.substring(8));
            }
        } catch (NumberFormatException e) {
            // Invalid format
        }

        return null;
    }
}
