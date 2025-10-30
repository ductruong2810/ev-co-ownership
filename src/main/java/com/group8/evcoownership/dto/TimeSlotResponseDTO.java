package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeSlotResponseDTO {
    private String time;
    private String status; // AVAILABLE, BOOKED
    private String bookedBy;
    private boolean bookable;
    // Detailed type for FE rendering: AVAILABLE | BOOKED_SELF | BOOKED_OTHER | MAINTENANCE | LOCKED
    private String type;
}
