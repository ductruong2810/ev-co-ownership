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

    @NotBlank(message = "OTP cannot be blank!")
    @Size(min = 6, max = 6, message = "OTP must have 6 characters!")
    private String otp;
}
