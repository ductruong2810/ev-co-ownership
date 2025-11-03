package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
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
    @JsonRawValue
    private String qrCodeCheckin;

    @JsonRawValue
    private String qrCodeCheckout;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private LocalDateTime createdAt;
}
