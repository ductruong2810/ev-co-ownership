package com.group8.evcoownership.dto;

import com.group8.evcoownership.validation.ValidCreateGroupWithVehicleDynamic;
import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@ValidCreateGroupWithVehicleDynamic
public record CreateGroupWithVehicleRequestDTO(
        @NotBlank(message = "Group name is required")
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
        String licensePlate,


        // @NotBlank(message = "Chassis number is required") // Removed - OCR can fill
        // Validation moved to dynamic validator
        String chassisNumber,


        @NotNull(message = "Vehicle images are required")
        @Size(min = 1, max = 10, message = "Must upload 1-10 images")
        MultipartFile[] vehicleImages,

        @NotNull(message = "Image types are required")
        @Size(min = 1, max = 10, message = "Must have 1-10 image types")
        String[] imageTypes,

        // Optional fields for auto-fill from OCR
        String brand,
        String model,

        // Flag to enable OCR processing (default true)
        Boolean enableAutoFill,

        // Optional vehicle type - auto-detect if not provided
        String vehicleType
) {
}