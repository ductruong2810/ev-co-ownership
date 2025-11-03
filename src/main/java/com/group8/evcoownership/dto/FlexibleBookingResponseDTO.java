package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FlexibleBookingResponseDTO {
    private Long bookingId;
    private String status;
    private String message;
    private Long totalHours;
    private boolean overnightBooking;
//    private String qrCode;//thinh moi them vo nha
    private String qrCodeCheckin;
    private String qrCodeCheckout;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private LocalDateTime createdAt;
}
