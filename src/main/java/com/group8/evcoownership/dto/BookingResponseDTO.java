package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingResponseDTO {
    private Long bookingId;
    private Long userId;
    private Long vehicleId;
    private String startDateTime;
    private String endDateTime;
    private String status;
}
