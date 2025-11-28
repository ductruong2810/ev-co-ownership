package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDocumentRequestDTO {

    @NotBlank(message = "Action is required")
    private String action;  // "APPROVE" or "REJECT"

    private String reason;  // Optional reason for rejection
}
