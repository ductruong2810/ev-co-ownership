package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyResetOtpRequestDTO {

//    @NotBlank(message = "Email không được để trống")
//    @Email(message = "Email không hợp lệ")
//    private String email;
    //không cần thiết vì chỉ cần nhập OTP thôi

    @NotBlank(message = "OTP không được để trống")
    @Size(min = 6, max = 6, message = "OTP phải có 6 ký tự")
    private String otp;
}
