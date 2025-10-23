package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlexibleBookingResponse {
    private Long bookingId;
    private String status;
    private String message;
    private Long totalHours;
    private boolean overnightBooking;
}
