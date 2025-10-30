package com.group8.evcoownership.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageCountsDTO {
    private long pending;
    private long approved;
    private long rejected;
}


