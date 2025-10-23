package com.group8.evcoownership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequestDTO {

    @NotBlank(message = "Reset token cannot be empty!")
    private String resetToken; // Token nhận từ bước 2

    @NotBlank(message = "New password cannot be blank!")
    @Size(min = 8, max = 50, message = "Mật khẩu phải từ 8-50 ký tự")
    @Pattern(
            regexp = "^(?!.*[<>])(?!.*(?i)script)(?!.*(?i)javascript)(?!.*(?i)onerror)(?!.*(?i)onload)[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,./?~`]+$",
            message = "Mật khẩu phải có 5-50 ký tự, ít nhất 1 chữ hoa và 1 ký tự đặc biệt"
    )
    private String newPassword;

    @NotBlank(message = "Confirm password cannot be blank!")
    private String confirmPassword;
}
