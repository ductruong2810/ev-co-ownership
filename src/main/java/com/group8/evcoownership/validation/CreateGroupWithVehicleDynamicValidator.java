package com.group8.evcoownership.validation;

import com.group8.evcoownership.dto.CreateGroupWithVehicleRequest;
import com.group8.evcoownership.enums.VehicleType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Validator động cho CreateGroupWithVehicleRequest
 * Tự động phát hiện loại xe và áp dụng validation tương ứng
 */
@Slf4j
public class CreateGroupWithVehicleDynamicValidator implements ConstraintValidator<ValidCreateGroupWithVehicleDynamic, CreateGroupWithVehicleRequest> {

    // Tất cả image types hợp lệ (linh hoạt cho cả xe máy và xe ô tô)
    private static final String[] ALL_VALID_IMAGE_TYPES = {
            "VEHICLE", "FRONT", "BACK", "LEFT", "RIGHT",
            "INTERIOR", "ENGINE", "LICENSE", "REGISTRATION"
    };

    // Pattern cho biển số xe ô tô: 29A-123.45
    private static final Pattern CAR_LICENSE_PATTERN = Pattern.compile(
            "^[0-9]{2}[A-Z]-[0-9]{3}\\.[0-9]{2}$"
    );

    // Pattern cho biển số xe máy: 29A1-12345 hoặc 29A-12345
    private static final Pattern MOTORCYCLE_LICENSE_PATTERN = Pattern.compile(
            "^[0-9]{2}[A-Z][0-9]?-?[0-9]{3,5}$"
    );

    // Các hãng xe máy phổ biến
    private static final String[] MOTORCYCLE_BRANDS = {
            "yamaha", "honda", "suzuki", "kawasaki", "ducati", "ktm",
            "piaggio", "vespa", "sym", "kymco", "benelli", "cfmoto", "qj motor"
    };

    @Override
    public void initialize(ValidCreateGroupWithVehicleDynamic constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(CreateGroupWithVehicleRequest request, ConstraintValidatorContext context) {
        boolean isValid = true;

        try {
            // Auto-detect vehicle type nếu không có
            String vehicleType = request.vehicleType();
            if (vehicleType == null || vehicleType.isEmpty()) {
                vehicleType = detectVehicleType(request);
                log.info("Auto-detected vehicle type: {}", vehicleType);
            }

            // Validate license plate theo loại xe
            if (!validateLicensePlate(request.licensePlate(), vehicleType)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "License plate format is invalid for " + vehicleType + ". " +
                                        "Car format: 29A-123.45, Motorcycle format: 29A1-12345")
                        .addConstraintViolation();
                isValid = false;
            }

            // Validate chassis number theo loại xe
            if (!validateChassisNumber(request.chassisNumber(), vehicleType)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Chassis number format is invalid for " + vehicleType + ". " +
                                        "Car: 17 characters, Motorcycle: 10-12 characters")
                        .addConstraintViolation();
                isValid = false;
            }

            // Validate image types (linh hoạt cho cả xe máy và xe ô tô)
            if (!validateImageTypes(request.imageTypes(), context)) {
                isValid = false;
            }

            // Validate file sizes
            if (!validateFileSizes(request.vehicleImages(), context)) {
                isValid = false;
            }

            // Validate image count matches image types count
            if (request.vehicleImages().length != request.imageTypes().length) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Number of images must match number of image types")
                        .addConstraintViolation();
                isValid = false;
            }

        } catch (Exception e) {
            log.error("Error during validation", e);
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Validation error: " + e.getMessage())
                    .addConstraintViolation();
            isValid = false;
        }

        return isValid;
    }

    /**
     * Tự động phát hiện loại xe dựa trên brand và format biển số
     */
    private String detectVehicleType(CreateGroupWithVehicleRequest request) {
        // Bước 1: Kiểm tra brand
        String brand = request.brand();
        if (brand != null && !brand.trim().isEmpty()) {
            String lowerBrand = brand.toLowerCase().trim();

            for (String motorcycleBrand : MOTORCYCLE_BRANDS) {
                if (lowerBrand.contains(motorcycleBrand)) {
                    log.info("Detected motorcycle by brand: {}", motorcycleBrand);
                    return VehicleType.MOTORCYCLE.getValue();
                }
            }
        }

        // Bước 2: Kiểm tra format biển số
        String licensePlate = request.licensePlate();
        if (licensePlate != null && !licensePlate.trim().isEmpty()) {
            if (MOTORCYCLE_LICENSE_PATTERN.matcher(licensePlate).matches()) {
                log.info("Detected motorcycle by license plate format: {}", licensePlate);
                return VehicleType.MOTORCYCLE.getValue();
            }
            if (CAR_LICENSE_PATTERN.matcher(licensePlate).matches()) {
                log.info("Detected car by license plate format: {}", licensePlate);
                return VehicleType.CAR.getValue();
            }
        }

        // Default: xe ô tô
        log.info("Default to car type");
        return VehicleType.CAR.getValue();
    }

    /**
     * Validate biển số theo loại xe
     */
    private boolean validateLicensePlate(String licensePlate, String vehicleType) {
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            return false;
        }

        if (VehicleType.CAR.getValue().equals(vehicleType)) {
            return CAR_LICENSE_PATTERN.matcher(licensePlate).matches();
        } else if (VehicleType.MOTORCYCLE.getValue().equals(vehicleType)) {
            return MOTORCYCLE_LICENSE_PATTERN.matcher(licensePlate).matches();
        }

        return false;
    }

    /**
     * Validate số khung theo loại xe
     */
    private boolean validateChassisNumber(String chassisNumber, String vehicleType) {
        if (chassisNumber == null || chassisNumber.trim().isEmpty()) {
            return true; // Chassis number is optional (OCR can fill)
        }

        String trimmedChassis = chassisNumber.trim().toUpperCase();

        // Validate format: only uppercase letters and digits
        if (!trimmedChassis.matches("^[A-Z0-9]+$")) {
            return false;
        }

        // Validate length theo loại xe
        if (VehicleType.CAR.getValue().equals(vehicleType)) {
            return trimmedChassis.length() == 17;
        } else if (VehicleType.MOTORCYCLE.getValue().equals(vehicleType)) {
            return trimmedChassis.length() >= 10 && trimmedChassis.length() <= 12;
        }

        return false;
    }

    /**
     * Validate image types (linh hoạt cho cả xe máy và xe ô tô)
     */
    private boolean validateImageTypes(String[] imageTypes, ConstraintValidatorContext context) {
        for (String imageType : imageTypes) {
            if (!Arrays.asList(ALL_VALID_IMAGE_TYPES).contains(imageType)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Invalid image type: " + imageType +
                                        ". Valid types: " + Arrays.toString(ALL_VALID_IMAGE_TYPES))
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }

    /**
     * Validate file sizes
     */
    private boolean validateFileSizes(MultipartFile[] images, ConstraintValidatorContext context) {
        long maxFileSize = 10 * 1024 * 1024; // 10MB

        for (MultipartFile image : images) {
            if (image.getSize() > maxFileSize) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "File size exceeds 10MB limit: " + image.getOriginalFilename())
                        .addConstraintViolation();
                return false;
            }
            if (image.isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Empty file not allowed: " + image.getOriginalFilename())
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
