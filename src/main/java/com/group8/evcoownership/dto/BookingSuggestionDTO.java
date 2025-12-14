package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSuggestionDTO {
    private String date;
    private String dayOfWeek;
    private String timeRange;
    private Double score;
    private String reason;
}

