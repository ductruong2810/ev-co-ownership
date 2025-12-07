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
public class SmartSuggestionResponseDTO {
    private UsageAnalyticsDTO analytics;
    private List<BookingSuggestionDTO> suggestions;
    private List<String> aiInsights;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BookingSuggestionDTO {
        private String date;
        private String dayOfWeek;
        private String timeRange;
        private Double score;
        private String reason;
        private String suitability; // PRIME, BALANCED, OFF_PEAK
        private String recommendationLevel; // HIGH, MEDIUM, LOW
        private Boolean overnight;
    }
}

