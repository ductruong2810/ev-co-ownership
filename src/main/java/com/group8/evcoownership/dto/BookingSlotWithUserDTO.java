package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingSlotWithUserDTO {
    private Long bookingId;
    private String userFullName;
    private String userEmail;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String status;
}
