package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlexibleBookingResponseDTO {
    private Long bookingId;
    private String status;
    private String message;
    private Long totalHours;
    private boolean overnightBooking;

    @JsonRawValue
    private String qrCode;//thinh moi them vo nha
}
