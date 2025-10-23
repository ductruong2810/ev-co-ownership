package com.group8.evcoownership.dto;

import com.group8.evcoownership.validation.ValidCreateGroupWithVehicle;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@ValidCreateGroupWithVehicle
public record CreateGroupWithVehicleRequest(
        @NotBlank(message = "Group name is required")
        @Pattern(
                regexp = "^[a-zA-Z0-9\\s]+$",
                message = "Group name must not contain special characters"
        )
                @Size(max = 100, message = "Group name must not exceed 100 characters")
        String groupName,

        @Size(max = 4000, message = "Description must not exceed 4000 characters")
        String description,

            @NotBlank
//        @NotNull(message = "Member capacity is required")
//        @Min(value = 1, message = "Member capacity must be at least 1")
//        @Max(value = 50, message = "Member capacity cannot exceed 50")
        @Pattern(regexp = "^[2-5]+$", message = "Member capacity must be a number(2-5)")
        String memberCapacity,

        @NotNull(message = "Vehicle value is required")
        @DecimalMin(value = "0.01", message = "Vehicle value must be positive")
        @DecimalMax(value = "100000000000", message = "Vehicle value cannot exceed 100 billion VND")
        BigDecimal vehicleValue,

        @NotBlank(message = "License plate is required")
        @Size(max = 20, message = "License plate must not exceed 20 characters")
        @Pattern(
                regexp = "^[0-9]{2}[A-Z]-[0-9]{3}\\.[0-9]{2}$",
                message = "License plate format must be like '29A-123.45'"
        )
        String licensePlate,


        @NotBlank(message = "Chassis number is required")
        @Size(min = 17, max = 17, message = "Chassis number must be exactly 17 characters")
        @Pattern(
                regexp = "^[A-Z0-9]{17}$",
                message = "Chassis number must contain exactly 17 uppercase letters or digits"
        )
        String chassisNumber,


        @NotNull(message = "Vehicle images are required")
        @Size(min = 1, max = 10, message = "Must upload 1-10 images")
        MultipartFile[] vehicleImages,

        @NotNull(message = "Image types are required")
        @Size(min = 1, max = 10, message = "Must have 1-10 image types")
        String[] imageTypes
) {
}
