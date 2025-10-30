package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserQuotaResponseDTO {
    private Long totalHours;
    private Long usedHours;
    private Long remainingHours;
}
