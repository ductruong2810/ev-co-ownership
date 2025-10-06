package com.group8.evcoownership.dto;

import lombok.Data;

@Data
public class RegisterRequestDTO {
    private String fullName;
    private String email;
    private String password;
    private String confirmPassword;
    private String phoneNumber;
    private String citizenId;
    private String driverLicense;
}
//10/6/2025 Thinh Nguyen
