package com.group8.evcoownership.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentCreateRequestDTO {
    private Long bookingId;            // Bắt buộc
    private String description;        // Mô tả sự cố
    private BigDecimal actualCost;     // Chi phí thực tế user trả
    private String imageUrls;          // Ảnh xe hư + hóa đơn
}
