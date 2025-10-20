package com.group8.evcoownership.dto;

import com.group8.evcoownership.enums.OtpType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOtpRequestDTO {

    @NotBlank(message = "OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP phải là 6 chữ số")
    private String otp;

    @NotNull(message = "Type is required")
    private OtpType type;  // REGISTRATION or PASSWORD_RESET
}
