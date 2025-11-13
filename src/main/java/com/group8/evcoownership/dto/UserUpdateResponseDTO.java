package com.group8.evcoownership.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserUpdateResponseDTO {
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
}
