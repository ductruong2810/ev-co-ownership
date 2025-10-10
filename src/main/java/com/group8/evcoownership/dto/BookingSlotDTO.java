package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingSlotDTO {
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
}

