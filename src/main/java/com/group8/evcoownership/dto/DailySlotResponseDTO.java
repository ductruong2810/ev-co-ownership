package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DailySlotResponseDTO {
    private LocalDate date;
    private String dayOfWeek;
    private List<TimeSlotResponseDTO> slots;
}
