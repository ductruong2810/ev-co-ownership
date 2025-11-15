package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class WeeklyCalendarDashboardDTO {
    // Vehicle
    private Long groupId;
    private Long vehicleId;
    private String brand;
    private String model;
    private String licensePlate;
    private BigDecimal vehicleValue;

    // Vehicle status
    private String vehicleStatus;         // Good, Under Maintenance, etc
    private Integer batteryPercent;       // Battery percentage
    private Integer odometer;             // Kilometers

    // Maintenance
    private LocalDate lastMaintenanceDate;
    private LocalDate nextMaintenanceDate;
    private String maintenanceStatus; // "NO_ISSUE"/"NEEDS_MAINTENANCE"/null

    // Booking statistics for the week
    private Integer totalBookings;        // Total bookings this week
    private Integer userBookings;         // User's bookings this week
    private Double ownershipPercent;         // User's ownership percentage (tỷ lệ sở hữu), not booking ratio
}
