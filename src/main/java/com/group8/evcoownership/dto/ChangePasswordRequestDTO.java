package com.group8.evcoownership.dto;

import com.group8.evcoownership.validation.PasswordConfirmation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@PasswordConfirmation(
        passwordField = "newPassword",
        confirmPasswordField = "confirmPassword",
        message = "Mật khẩu mới và xác nhận mật khẩu không khớp"
)
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Mật khẩu cũ không được để trống")
    private String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, max = 50, message = "Mật khẩu phải từ 8-50 ký tự")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[@#$%^&+=!])(?!.*[<>\\\\\"'/;`()]).{5,50}$",
            message = "Mật khẩu phải có 5-50 ký tự, ít nhất 1 chữ hoa và 1 ký tự đặc biệt"
    )
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    private String confirmPassword;
}
