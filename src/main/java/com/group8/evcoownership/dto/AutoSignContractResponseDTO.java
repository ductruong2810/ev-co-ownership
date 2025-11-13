package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoSignContractResponseDTO {
    private Boolean success;
    private Long contractId;
    private String contractNumber;
    private String status;
    private LocalDateTime signedAt;
    private String message;
}

