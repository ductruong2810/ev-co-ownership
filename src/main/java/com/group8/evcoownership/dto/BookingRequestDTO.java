package com.group8.evcoownership.dto;

import lombok.Data;

@Data
public class BookingRequestDTO {
    private Long userId;
    private Long vehicleId;
    private String start;
    private String end;
}
