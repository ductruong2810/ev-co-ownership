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

    @NotBlank(message = "OTP cannot be blank!")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits!")
    private String otp;

    @NotNull(message = "Type is required")
    private OtpType type;  // REGISTRATION hoac PASSWORD_RESET
}
