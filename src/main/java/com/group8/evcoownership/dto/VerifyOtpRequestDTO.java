package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequestDTO {
    @NotBlank(message = "OTP không được để trống")
    private String otp;
}
