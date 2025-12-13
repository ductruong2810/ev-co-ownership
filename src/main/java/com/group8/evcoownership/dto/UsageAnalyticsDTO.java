package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageAnalyticsDTO {
    private Double ownershipPercentage;
    private Double actualHoursLast4Weeks;
    private Double expectedHoursLast4Weeks;
    private Double utilizationPercent;
    private Double usageGapHours;
    private Integer bookingsThisWeek;
    private Double hoursThisWeek;
    private Double weeklyAverageHours;
    private Integer totalQuotaSlots;
    private Integer usedQuotaSlots;
    private Integer remainingQuotaSlots;
    private String fairnessStatus; // UNDER_UTILIZED, OVER_UTILIZED, ON_TRACK
    private List<String> actionItems;
    private List<UsageLeaderboardEntryDTO> leaderboard;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UsageLeaderboardEntryDTO {
        private Long userId;
        private String userName;
        private Double totalHours;
        private Double ownershipPercentage;
        private Double usageToShareRatio;
    }
}





