package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentUpdateRequestDTO {
    private String description;
    private BigDecimal actualCost;
    private String imageUrls;
}
