package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleCheckResponseDTO {

    private Long id;

    // chỉ trả bookingId, không trả object booking
    private Long bookingId;

    private String checkType;    // PRE_USE, POST_USE, REJECTION
    private Integer odometer;
    private BigDecimal batteryLevel;
    private String cleanliness;
    private String notes;
    private String issues;
    private String status;
    private LocalDateTime createdAt;
}
