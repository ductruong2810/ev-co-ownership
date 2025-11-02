package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class BookingDetailResponseDTO {
    Long bookingId;
    Long userId;
    String userFullName;
    Long vehicleId;
    String licensePlate;
    String brand;
    String model;
    LocalDateTime startDateTime;
    LocalDateTime endDateTime;
    String status;
    String qrCode;
}

