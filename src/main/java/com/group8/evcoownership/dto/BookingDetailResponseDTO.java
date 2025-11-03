package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
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

    @JsonRawValue
    String qrCode;
}

