package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FlexibleBookingRequestDTO {
    private Long vehicleId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
}
