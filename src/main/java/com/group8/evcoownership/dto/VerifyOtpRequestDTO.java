package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequestDTO {

    @NotBlank(message = "OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP phải là 6 chữ số")
    private String otp;
    // ← KHÔNG CÓ TYPE, backend tự detect
}
