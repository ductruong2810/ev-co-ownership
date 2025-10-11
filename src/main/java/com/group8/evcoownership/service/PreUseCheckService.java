package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.PreUseCheck;
import com.group8.evcoownership.entity.UsageBooking;
import com.group8.evcoownership.repository.PreUseCheckRepository;
import com.group8.evcoownership.repository.UsageBookingRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PreUseCheckService {

    private final PreUseCheckRepository preUseCheckRepository;
    private final UsageBookingRepository usageBookingRepository;

    // User tạo pre-use check
    public PreUseCheck createPreUseCheck(Long bookingId, Boolean exteriorDamage,
                                         Boolean interiorClean, Boolean warningLights,
                                         Boolean tireCondition, String userNotes) {
        UsageBooking booking = usageBookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        PreUseCheck check = PreUseCheck.builder()
                .booking(booking)
                .exteriorDamage(exteriorDamage)
                .interiorClean(interiorClean)
                .warningLights(warningLights)
                .tireCondition(tireCondition)
                .userNotes(userNotes)
                .checkTime(java.time.LocalDateTime.now())
                .build();

        return preUseCheckRepository.save(check);
    }

    // Lấy pre-use check của booking
    public Optional<PreUseCheck> getPreUseCheck(Long bookingId) {
        return preUseCheckRepository.findByBookingId(bookingId);
    }

    // Kiểm tra user đã làm pre-use check chưa
    public Boolean hasPreUseCheck(Long bookingId) {
        return preUseCheckRepository.findByBookingId(bookingId).isPresent();
    }
}
