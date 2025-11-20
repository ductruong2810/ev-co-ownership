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

    // For Registration (type = REGISTRATION)
    private String accessToken;
    //Bỏ refresh token
//    private String refreshToken;
    private UserProfileResponseDTO user;  // ← THAY ĐỔI: từ UserInfoDTO → UserProfileResponseDTO

    // For Password Reset (type = PASSWORD_RESET)
    private String resetToken;
}
