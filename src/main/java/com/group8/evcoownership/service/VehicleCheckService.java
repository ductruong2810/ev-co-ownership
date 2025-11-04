package com.group8.evcoownership.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group8.evcoownership.dto.QrCheckOutRequestDTO;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.VehicleCheckRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VehicleCheckService {

    private final VehicleCheckRepository vehicleCheckRepository;
    private final UsageBookingRepository usageBookingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${booking.checkin.earliest-offset-minutes:10}")
    private long checkInEarliestOffsetMinutes;

    @Value("${booking.checkin.lock-offset-minutes:20}")
    private long checkInLockOffsetMinutes;

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
            BookingContext context = validateBookingQr(qrCode, userId, result);
            if (context == null) {
                return result;
            }

            UsageBooking booking = context.booking();
            BookingQrData qrData = context.qrData();

            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                result.put("success", false);
                result.put("message", "Booking is not confirmed");
                return result;
            }

            Vehicle vehicle = booking.getVehicle();
            Long groupId = null;
            if (vehicle != null && vehicle.getOwnershipGroup() != null) {
                groupId = vehicle.getOwnershipGroup().getGroupId();
            }
            result.put("groupId", groupId);

            VehicleCheck latestTechnicianCheck = findLatestTechnicianCheck(vehicle);
            if (latestTechnicianCheck != null) {
                result.put("latestVehicleCheck", buildVehicleCheckSummary(latestTechnicianCheck));
            }

            LocalDateTime now = LocalDateTime.now();
            boolean withinCheckInWindow = isWithinCheckInWindow(booking, now);


            result.put("success", true);
            result.put("message", "Ready for check-in");

            // SINH QR CHECKOUT và lưu vào field qrCodeCheckout
            String checkoutQr = generateCheckOutQrPayload(booking);
            booking.setQrCodeCheckout(checkoutQr);  // LƯU VÀO qrCodeCheckout
            usageBookingRepository.save(booking);

            result.put("qrUpdatedForCheckout", true);
            result.put("checkoutQrCode", checkoutQr);


            result.put("bookingId", booking.getId());
            result.put("canCheckIn", withinCheckInWindow);
            Map<String, Object> vehicleInfo = new HashMap<>();
            if (vehicle != null) {
                vehicleInfo.put("vehicleId", vehicle.getId());
                vehicleInfo.put("brand", vehicle.getBrand());
                vehicleInfo.put("model", vehicle.getModel());
                vehicleInfo.put("licensePlate", vehicle.getLicensePlate());
            }
            result.put("vehicleInfo", vehicleInfo);
            result.put("bookingInfo", Map.of(
                    "startTime", booking.getStartDateTime(),
                    "endTime", booking.getEndDateTime(),
                    "status", booking.getStatus().toString()
            ));
            result.put("hasPreUseCheck", hasCheck(booking.getId(), "PRE_USE"));
            result.put("hasPostUseCheck", hasCheck(booking.getId(), "POST_USE"));
            result.put("qrUserId", qrData.userId());

        } catch (JsonProcessingException e) {
            log.warn("Invalid QR code payload for check-in: {}", e.getOriginalMessage());
            result.put("success", false);
            result.put("message", "Invalid QR code format");
        } catch (IOException e) {
            log.error("I/O error while processing QR check-in", e);
            result.put("success", false);
            result.put("message", "Unable to process QR code");
        } catch (EntityNotFoundException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            log.warn("QR check-in validation failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "Unable to process QR code");
        } catch (Exception e) {
            log.error("Unexpected error processing QR check-in", e);
            result.put("success", false);
            result.put("message", "Unable to process QR code");
        }

        return result;
    }

    private boolean isWithinCheckInWindow(UsageBooking booking, LocalDateTime now) {
        LocalDateTime startTime = booking.getStartDateTime();
        LocalDateTime earliestCheckIn = startTime != null
                ? (checkInEarliestOffsetMinutes < 0
                    ? LocalDateTime.MIN
                    : startTime.minusMinutes(checkInEarliestOffsetMinutes))
                : LocalDateTime.MIN;
        LocalDateTime lockTime = startTime != null
                ? startTime.plusMinutes(checkInLockOffsetMinutes)
                : now.plusYears(1);
        return !now.isBefore(earliestCheckIn) && now.isBefore(lockTime);
    }


    public Map<String, Object> processQrCheckOut(QrCheckOutRequestDTO request, Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            BookingContext context = validateBookingQr(request.qrCode(), userId, result);
            if (context == null) {
                return result;
            }

            UsageBooking booking = context.booking();

            if (booking.getStatus() == BookingStatus.COMPLETED) {
                result.put("success", true);
                result.put("alreadyCompleted", true);
                result.put("message", "Booking already completed");
                result.put("bookingId", booking.getId());
                result.put("bookingStatus", booking.getStatus().name());
                return result;
            }

            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                result.put("success", false);
                result.put("message", "Booking is not in a checkout-ready state");
                return result;
            }

            LocalDateTime now = LocalDateTime.now();
            if (booking.getStartDateTime() != null && now.isBefore(booking.getStartDateTime())) {
                result.put("success", false);
                result.put("message", "Cannot checkout before booking start time");
                return result;
            }

            if (booking.getEndDateTime() != null && now.isAfter(booking.getEndDateTime().plusHours(4))) {
                result.put("success", false);
                result.put("message", "Checkout window has expired");
                return result;
            }

            if (!hasCheck(booking.getId(), "PRE_USE")) {
                result.put("success", false);
                result.put("message", "Must complete pre-use check before checkout");
                return result;
            }

            VehicleCheck postUseCheck;
            boolean alreadyPostUse = hasCheck(booking.getId(), "POST_USE");
            if (alreadyPostUse) {
                postUseCheck = vehicleCheckRepository.findByBookingId(booking.getId())
                        .stream()
                        .filter(check -> "POST_USE".equals(check.getCheckType()))
                        .findFirst()
                        .orElse(null);
            } else {
                postUseCheck = createPostUseCheck(
                        booking.getId(),
                        userId,
                        request.odometer(),
                        request.batteryLevel(),
                        request.cleanliness(),
                        request.notes(),
                        request.issues()
                );
            }

            booking.setStatus(BookingStatus.COMPLETED);
            booking.setQrCodeCheckout(generateCompletedQrPayload(booking));
            usageBookingRepository.save(booking);

            result.put("success", true);
            result.put("message", "Checkout recorded successfully");
            result.put("bookingId", booking.getId());
            result.put("bookingStatus", booking.getStatus().name());
            result.put("postUseCheckId", postUseCheck != null ? postUseCheck.getId() : null);
            result.put("postUseCheck", postUseCheck != null ? buildVehicleCheckSummary(postUseCheck) : null);
            result.put("hasPostUseCheck", true);
            result.put("maintenanceSuggested", hasReportedIssues(request));

        } catch (JsonProcessingException e) {
            log.warn("Invalid QR code payload for checkout: {}", e.getOriginalMessage());
            result.put("success", false);
            result.put("message", "Invalid QR code format");
        } catch (IOException e) {
            log.error("I/O error while processing QR checkout", e);
            result.put("success", false);
            result.put("message", "Unable to process QR checkout");
        } catch (EntityNotFoundException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            log.warn("QR checkout validation failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "Unable to process QR checkout");
        } catch (Exception e) {
            log.error("Unexpected error processing QR checkout", e);
            result.put("success", false);
            result.put("message", "Unable to process QR checkout");
        }

        return result;
    }


    private VehicleCheck findLatestTechnicianCheck(Vehicle vehicle) {
        if (vehicle == null || vehicle.getOwnershipGroup() == null || vehicle.getOwnershipGroup().getGroupId() == null) {
            return null;
        }

        List<VehicleCheck> checks = vehicleCheckRepository.findLatestPostUseCheckByVehicleAndGroup(
                vehicle.getId(),
                vehicle.getOwnershipGroup().getGroupId(),
                PageRequest.of(0, 1)
        );
        if (!checks.isEmpty()) {
            return checks.get(0);
        }

        return vehicleCheckRepository.findByVehicleId(vehicle.getId()).stream()
                .filter(check -> "POST_USE".equals(check.getCheckType()))
                .max(Comparator.comparing(VehicleCheck::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }


    private Map<String, Object> buildVehicleCheckSummary(VehicleCheck check) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("checkId", check.getId());
        summary.put("checkType", check.getCheckType());
        summary.put("status", check.getStatus());
        summary.put("odometer", check.getOdometer());
        summary.put("batteryLevel", check.getBatteryLevel());
        summary.put("cleanliness", check.getCleanliness());
        summary.put("notes", check.getNotes());
        summary.put("issues", check.getIssues());
        summary.put("createdAt", check.getCreatedAt());
        summary.put("bookingId", check.getBooking() != null ? check.getBooking().getId() : null);
        return summary;
    }


    private String generateCheckOutQrPayload(UsageBooking booking) {
        String startTime = booking.getStartDateTime() != null ? "\"" + booking.getStartDateTime() + "\"" : "null";
        String endTime = booking.getEndDateTime() != null ? "\"" + booking.getEndDateTime() + "\"" : "null";

        return String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"vehicleId\":%d,\"phase\":\"CHECKOUT\",\"startTime\":%s,\"endTime\":%s,\"nonce\":\"%s\",\"timestamp\":\"%s\"}",
                booking.getId(),
                booking.getUser() != null ? booking.getUser().getUserId() : null,
                booking.getVehicle() != null ? booking.getVehicle().getId() : null,
                startTime,
                endTime,
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }


    private String generateCompletedQrPayload(UsageBooking booking) {
        String startTime = booking.getStartDateTime() != null ? "\"" + booking.getStartDateTime() + "\"" : "null";
        String endTime = booking.getEndDateTime() != null ? "\"" + booking.getEndDateTime() + "\"" : "null";

        return String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"vehicleId\":%d,\"phase\":\"COMPLETED\",\"status\":\"COMPLETED\",\"startTime\":%s,\"endTime\":%s,\"nonce\":\"%s\",\"timestamp\":\"%s\"}",
                booking.getId(),
                booking.getUser() != null ? booking.getUser().getUserId() : null,
                booking.getVehicle() != null ? booking.getVehicle().getId() : null,
                startTime,
                endTime,
                UUID.randomUUID(),
                LocalDateTime.now()
        );
    }


    private boolean hasReportedIssues(QrCheckOutRequestDTO request) {
        if (request == null) {
            return false;
        }

        boolean hasIssuesText = request.issues() != null && !request.issues().isBlank();
        boolean cleanlinessProblem = request.cleanliness() != null && !"CLEAN".equalsIgnoreCase(request.cleanliness());

        return hasIssuesText || cleanlinessProblem;
    }


    private BookingContext validateBookingQr(String rawQr,
                                             Long userId,
                                             Map<String, Object> result) throws IOException {
        String normalizedQr = rawQr != null ? rawQr.trim() : null;
        BookingQrData qrData = parseBookingQr(normalizedQr);
        if (qrData == null) {
            result.put("success", false);
            result.put("message", "Invalid QR code format");
            return null;
        }

        UsageBooking booking = usageBookingRepository.findById(qrData.bookingId())
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        // Xác định loại QR và validate từ field tương ứng
        JsonNode root = objectMapper.readTree(normalizedQr);
        String phase = root.has("phase") ? root.get("phase").asText() : null;

        boolean isValidQr = false;
        if ("CHECKIN".equals(phase)) {
            // So sánh với qrCodeCheckin
            isValidQr = Objects.equals(booking.getQrCodeCheckin(), normalizedQr);
        } else if ("CHECKOUT".equals(phase)) {
            // So sánh với qrCodeCheckout
            isValidQr = Objects.equals(booking.getQrCodeCheckout(), normalizedQr);
        }

        if (!isValidQr) {
            result.put("success", false);
            result.put("message", "QR code is outdated. Please refresh and try again");
            result.put("requiresUpdatedQr", true);
            return null;
        }

        if (!booking.getVehicle().getId().equals(qrData.vehicleId())) {
            result.put("success", false);
            result.put("message", "QR code does not match vehicle");
            return null;
        }

        if (!booking.getUser().getUserId().equals(userId)) {
            result.put("success", false);
            result.put("message", "QR code does not belong to current user");
            return null;
        }

        return new BookingContext(booking, qrData);
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


    private record BookingContext(UsageBooking booking, BookingQrData qrData) {
    }
}
