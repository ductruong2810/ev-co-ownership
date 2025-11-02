package com.group8.evcoownership.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.VehicleCheckRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    // User tạo pre-use check với validation
    public VehicleCheck createPreUseCheck(Long bookingId, Long userId, Integer odometer,
                                          BigDecimal batteryLevel, String cleanliness,
                                          String notes, String issues) {
        // Validate inputs
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        // VALIDATION: Kiểm tra booking có thuộc về user không
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new SecurityException("You can only check-in your own bookings");
        }

        // VALIDATION: Kiểm tra booking status
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be checked-in. Current status: " + booking.getStatus());
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
        // Validate inputs
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        // VALIDATION: Kiểm tra booking có thuộc về user không
        if (!booking.getUser().getUserId().equals(userId)) {
            throw new SecurityException("You can only check-out your own bookings");
        }

        // VALIDATION: Kiểm tra booking status
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed bookings can be checked-out. Current status: " + booking.getStatus());
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
            BookingQrData qrData = parseBookingQr(qrCode);
            if (qrData == null) {
                result.put("success", false);
                result.put("message", "Invalid QR code format");
                return result;
            }

            UsageBooking booking = usageBookingRepository.findById(qrData.bookingId())
                    .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                result.put("success", false);
                result.put("message", "Booking is not confirmed");
                return result;
            }

            if (!booking.getVehicle().getId().equals(qrData.vehicleId())) {
                result.put("success", false);
                result.put("message", "QR code does not match vehicle");
                return result;
            }

            if (!booking.getUser().getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "QR code does not belong to current user");
                return result;
            }

            Vehicle vehicle = booking.getVehicle();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime earliestCheckIn = booking.getStartDateTime().minusMinutes(15);
            boolean canCheckIn = !now.isBefore(earliestCheckIn) && !now.isAfter(booking.getEndDateTime());


            if (!canCheckIn) {
                result.put("success", false);
                result.put("message", "Booking time is not valid for check-in");
            } else {
                result.put("success", true);
                result.put("message", "Ready for check-in");
            }

            result.put("bookingId", booking.getId());
            result.put("canCheckIn", canCheckIn);
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
            result.put("hasPreUseCheck", hasCheck(booking.getId(), "PRE_USE"));
            result.put("hasPostUseCheck", hasCheck(booking.getId(), "POST_USE"));
            result.put("qrUserId", qrData.userId());

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error processing QR code: " + e.getMessage());
        }

        return result;
    }


    private BookingQrData parseBookingQr(String qrCode) throws IOException {
        if (qrCode == null || qrCode.trim().isEmpty()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(qrCode);

        if (root == null) {
            return null;
        }

        JsonNode bookingNode = root.get("bookingId");
        JsonNode vehicleNode = root.get("vehicleId");

        if (bookingNode == null || vehicleNode == null || !bookingNode.canConvertToLong() || !vehicleNode.canConvertToLong()) {
            return null;
        }

        Long bookingId = bookingNode.asLong();
        Long vehicleId = vehicleNode.asLong();

        Long qrUserId = root.hasNonNull("userId") ? root.get("userId").asLong() : null;
        LocalDateTime startTime = parseLocalDateTime(root.path("startTime"));
        LocalDateTime endTime = parseLocalDateTime(root.path("endTime"));

        return new BookingQrData(bookingId, vehicleId, qrUserId, startTime, endTime);
    }

    private LocalDateTime parseLocalDateTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(value);
    }

    private record BookingQrData(Long bookingId,
                                 Long vehicleId,
                                 Long userId,
                                 LocalDateTime startTime,
                                 LocalDateTime endTime) {
    }
}
