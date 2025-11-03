package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.RejectionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentRejectRequestDTO {

    @NotNull(message = "Rejection category is required")
    private RejectionCategory rejectionCategory; // bắt buộc chọn

    private String rejectionReason;
}

