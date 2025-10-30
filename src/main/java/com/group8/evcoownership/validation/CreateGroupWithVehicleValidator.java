package com.group8.evcoownership.validation;

import com.group8.evcoownership.dto.CreateGroupWithVehicleRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

public class CreateGroupWithVehicleValidator implements ConstraintValidator<ValidCreateGroupWithVehicle, CreateGroupWithVehicleRequestDTO> {

    private static final String[] VALID_IMAGE_TYPES = {
            "VEHICLE", "FRONT", "BACK", "LEFT", "RIGHT",
            "INTERIOR", "ENGINE", "LICENSE", "REGISTRATION"
    };

    @Override
    public void initialize(ValidCreateGroupWithVehicle constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(CreateGroupWithVehicleRequestDTO request, ConstraintValidatorContext context) {
        boolean isValid = true;

        // Validate image types match
        if (request.vehicleImages().length != request.imageTypes().length) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Number of images must match number of image types")
                    .addConstraintViolation();
            isValid = false;
        }

        // Validate image types are valid
        for (String imageType : request.imageTypes()) {
            if (!Arrays.asList(VALID_IMAGE_TYPES).contains(imageType)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Invalid image type: " + imageType + ". Valid types: " + Arrays.toString(VALID_IMAGE_TYPES))
                        .addConstraintViolation();
                isValid = false;
                break;
            }
        }

        // Validate file sizes
        long maxFileSize = 10 * 1024 * 1024; // 10MB
        for (MultipartFile image : request.vehicleImages()) {
            if (image.getSize() > maxFileSize) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "File size exceeds 10MB limit: " + image.getOriginalFilename())
                        .addConstraintViolation();
                isValid = false;
                break;
            }
            if (image.isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Empty file not allowed: " + image.getOriginalFilename())
                        .addConstraintViolation();
                isValid = false;
                break;
            }
        }

        return isValid;
    }
}
