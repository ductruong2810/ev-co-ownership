package com.group8.evcoownership.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingRequestDTO {
    private Long userId;
    private Long vehicleId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
}
