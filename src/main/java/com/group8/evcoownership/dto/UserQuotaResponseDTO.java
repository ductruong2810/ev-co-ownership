package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserQuotaResponseDTO {
    private Integer totalSlots;
    private Integer usedSlots;
    private Integer remainingSlots;
}
