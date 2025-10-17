package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.OtpType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponseDTO {

    private String email;
    private String message;
    private OtpType type;
    private Integer expiresIn; // seconds
}
