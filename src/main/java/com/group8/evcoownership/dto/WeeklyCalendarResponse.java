package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class WeeklyCalendarResponse {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private UserQuotaResponse userQuota;
    private List<DailySlotResponse> dailySlots;
}