package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.entity.VehicleCheck;
import com.group8.evcoownership.repository.UsageBookingRepository;
import com.group8.evcoownership.repository.VehicleCheckRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleCheckService {

    private final VehicleCheckRepository vehicleCheckRepository;
    private final UsageBookingRepository usageBookingRepository;

    // User tạo pre-use check
    public VehicleCheck createPreUseCheck(Long bookingId, Long userId, Integer odometer,
                                          BigDecimal batteryLevel, String cleanliness,
                                          String notes, String issues) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

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

    // User tạo post-use check
    public VehicleCheck createPostUseCheck(Long bookingId, Long userId, Integer odometer,
                                           BigDecimal batteryLevel, String cleanliness,
                                           String notes, String issues) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

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

    // User từ chối xe
    public VehicleCheck rejectVehicle(Long bookingId, Long userId, String issues, String notes) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        VehicleCheck check = VehicleCheck.builder()
                .booking(booking)
                .checkType("REJECTION")
                .issues(issues)
                .notes(notes)
                .status("REJECTED")
                .build();

        // Cancel booking
        booking.setStatus(com.group8.evcoownership.enums.BookingStatus.Cancelled);
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
}
