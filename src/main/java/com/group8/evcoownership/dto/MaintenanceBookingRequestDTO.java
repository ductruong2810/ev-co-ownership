package com.group8.evcoownership.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceBookingRequestDTO {
    private Long vehicleId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String reason;
    private boolean cancelAffectedBookings;
    private boolean notifyUsers;
}
