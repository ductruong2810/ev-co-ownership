package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.RejectionCategory;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponseDTO {
    private Long id;
    private Long bookingId;
    private Long reportedById;
    private String reportedByName;
    private String description;
    private BigDecimal actualCost;
    private String imageUrls;
    private String status;
    private Long approvedById;
    private String approvedByName;
    private RejectionCategory rejectionCategory;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

