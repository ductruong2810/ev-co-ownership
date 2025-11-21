package com.group8.evcoownership.dto;

public record UserWithRejectedCheckDTO(
        Long userId,
        String fullName,
        String email
) {}