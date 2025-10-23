package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class FlexibleBookingRequest {
    private Long userId;
    private Long vehicleId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String reason; // Optional: lý do sử dụng qua đêm
}
