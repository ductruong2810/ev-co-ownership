package com.group8.evcoownership.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateCheckStatusRequestDTO {
    private String status; // APPROVED, REJECTED
    private String notes;
}
