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
public class VerifyOtpResponseDTO {

    private String email;
    private String message;
    private OtpType type;

    // For REGISTRATION
    private String accessToken;
    private String refreshToken;
    private RegisterResponseDTO.UserInfoDTO user;

    // For PASSWORD_RESET
    private String resetToken;
}
