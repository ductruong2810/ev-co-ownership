package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class WeeklyCalendarResponseDTO {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private UserQuotaResponseDTO userQuota;
    private List<DailySlotResponseDTO> dailySlots;
}