package com.group8.evcoownership.dto;

public record UserWithRejectedCheckDTO(
        Long userId,
        String userName,
        Long vehicleId,
        String vehicleModel,
        String licensePlate
) {}