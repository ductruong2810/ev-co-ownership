package com.group8.evcoownership.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoSignOutcomeResponseDTO {
    private Boolean success;
    private String message;
    private Long contractId;
    private String contractNumber;
    private String status;
    private java.time.LocalDateTime signedAt;
    private AutoSignConditionsResponseDTO conditions;
}

