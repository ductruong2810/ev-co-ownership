package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceCreateRequest {
    private Long vehicleId;          // xe cần bảo trì
    private Long requestedBy;        // kỹ thuật viên tạo
    private String description;      // mô tả vấn đề
    private BigDecimal estimatedCost; // chi phí ước tính ban đầu
}
