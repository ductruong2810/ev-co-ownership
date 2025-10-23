package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeSlotResponse {
    private String time;
    private String status; // AVAILABLE, BOOKED
    private String bookedBy;
    private boolean bookable;
}
