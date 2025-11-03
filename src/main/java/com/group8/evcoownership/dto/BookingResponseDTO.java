package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingResponseDTO {
    private Long bookingId;
    private String licensePlate;
    private String brand;
    private String model;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String status;
    @JsonRawValue
    private String qrCodeCheckin;      // Thêm
    @JsonRawValue
    private String qrCodeCheckout;     // Thêm
    private LocalDateTime createdAt;
}
