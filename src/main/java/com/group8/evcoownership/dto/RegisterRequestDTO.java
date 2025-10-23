package com.group8.evcoownership.dto;

import com.group8.evcoownership.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@PasswordMatches
public class RegisterRequestDTO {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    // Cho phép chữ cái (kể cả tiếng Việt) và khoảng trắng, không cho ký tự đặc biệt hay số
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Full name must not contain special characters or numbers")
    private String fullName;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number cannot be empty")
    // Bắt đầu bằng 0, tổng cộng 10 chữ số
    @Pattern(regexp = "^0\\d{9}$", message = "Invalid phone number format (must start with 0 and have 10 digits)")
    private String phone;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
    // Ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt
    @Pattern(
            regexp = "^(?!.*[<>])(?!.*(?i)script)(?!.*(?i)javascript)(?!.*(?i)onerror)(?!.*(?i)onload)[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,./?~`]+$",
            message = "Password must have 5-50 characters, at least 1 uppercase letter and 1 special character!"
    )
    private String password;

    @NotBlank(message = "Confirm password cannot be empty")
    private String confirmPassword;
}
//10/6/2025 Thinh Nguyen
