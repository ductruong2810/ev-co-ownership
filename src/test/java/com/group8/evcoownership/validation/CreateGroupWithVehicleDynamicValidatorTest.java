package com.group8.evcoownership.validation;

import com.group8.evcoownership.dto.CreateGroupWithVehicleRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cho CreateGroupWithVehicleDynamicValidator
 */
class CreateGroupWithVehicleDynamicValidatorTest {

    private Validator validator;
    private MultipartFile[] mockImages;
    private String[] carImageTypes;
    private String[] motorcycleImageTypes;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        // Mock images
        mockImages = new MultipartFile[]{
                new MockMultipartFile("test1.jpg", "test1.jpg", "image/jpeg", "test content".getBytes()),
                new MockMultipartFile("test2.jpg", "test2.jpg", "image/jpeg", "test content".getBytes())
        };

        carImageTypes = new String[]{"VEHICLE", "FRONT"};
        motorcycleImageTypes = new String[]{"VEHICLE", "FRONT"};
    }

    @Test
    void testValidCarRequest() {
        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "3",
                new BigDecimal("100000000"),
                "29A-123.45", // Car license plate format
                "ABC12345678901234", // Car chassis (16 chars)
                mockImages,
                carImageTypes,
                "Toyota", // Car brand
                "Camry",
                true,
                "CAR" // Explicit car type
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Car request should be valid");
    }

    @Test
    void testValidMotorcycleRequest() {
        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "2",
                new BigDecimal("50000000"),
                "29A1-12345", // Motorcycle license plate format
                "ABC123456789", // Motorcycle chassis (11 chars)
                mockImages,
                motorcycleImageTypes,
                "Honda", // Motorcycle brand
                "Wave",
                true,
                "MOTORCYCLE" // Explicit motorcycle type
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Motorcycle request should be valid");
    }

    @Test
    void testAutoDetectCarByBrand() {
        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "3",
                new BigDecimal("100000000"),
                "29A-123.45", // Car license plate format
                "ABC12345678901234", // Car chassis (16 chars)
                mockImages,
                carImageTypes,
                "Toyota", // Car brand
                "Camry",
                true,
                null // No explicit type - should auto-detect
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Should auto-detect car by brand");
    }

    @Test
    void testAutoDetectMotorcycleByBrand() {
        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "2",
                new BigDecimal("50000000"),
                "29A1-12345", // Motorcycle license plate format
                "ABC123456789", // Motorcycle chassis (11 chars)
                mockImages,
                motorcycleImageTypes,
                "Honda", // Motorcycle brand
                "Wave",
                true,
                null // No explicit type - should auto-detect
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Should auto-detect motorcycle by brand");
    }

    @Test
    void testInvalidCarLicensePlate() {
        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "3",
                new BigDecimal("100000000"),
                "29A1-12345", // Motorcycle format for car
                "ABC12345678901234", // Car chassis (16 chars)
                mockImages,
                carImageTypes,
                "Toyota",
                "Camry",
                true,
                "CAR"
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Should fail validation for wrong license plate format");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("License plate format is invalid")));
    }

    @Test
    void testInvalidMotorcycleLicensePlate() {
        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "2",
                new BigDecimal("50000000"),
                "29A-123.45", // Car format for motorcycle
                "ABC1234567890",
                mockImages,
                motorcycleImageTypes,
                "Honda",
                "Wave",
                true,
                "MOTORCYCLE"
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Should fail validation for wrong license plate format");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("License plate format is invalid")));
    }

    @Test
    void testInvalidImageTypeForCar() {
        String[] invalidImageTypes = new String[]{"VEHICLE", "INVALID_TYPE"};

        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "3",
                new BigDecimal("100000000"),
                "29A-123.45",
                "ABC12345678901234", // Car chassis (16 chars)
                mockImages,
                invalidImageTypes,
                "Toyota",
                "Camry",
                true,
                "CAR"
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty(), "Should fail validation for invalid image type");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Invalid image type")));
    }

    @Test
    void testValidImageTypesForBothVehicleTypes() {
        // Test với image types hợp lệ cho cả xe máy và xe ô tô
        String[] validImageTypes = new String[]{"VEHICLE", "FRONT"}; // Chỉ 2 types để match với 2 images

        CreateGroupWithVehicleRequest request = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "2",
                new BigDecimal("50000000"),
                "29A1-12345",
                "ABC123456789", // Motorcycle chassis (11 chars)
                mockImages,
                validImageTypes,
                "Honda",
                "Wave",
                true,
                "MOTORCYCLE"
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Should be valid with common image types");
    }

    @Test
    void testAutoDetectByLicensePlateFormat() {
        // Test car detection by license plate when no brand provided
        CreateGroupWithVehicleRequest carRequest = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "3",
                new BigDecimal("100000000"),
                "29A-123.45", // Car format
                "ABC12345678901234", // Car chassis (16 chars)
                mockImages,
                carImageTypes,
                null, // No brand
                null,
                true,
                null // No explicit type
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> carViolations = validator.validate(carRequest);
        assertTrue(carViolations.isEmpty(), "Should auto-detect car by license plate format");

        // Test motorcycle detection by license plate when no brand provided
        CreateGroupWithVehicleRequest motorcycleRequest = new CreateGroupWithVehicleRequest(
                "Test Group",
                "Test Description",
                "2",
                new BigDecimal("50000000"),
                "29A1-12345", // Motorcycle format
                "ABC123456789", // Motorcycle chassis (11 chars)
                mockImages,
                motorcycleImageTypes,
                null, // No brand
                null,
                true,
                null // No explicit type
        );

        Set<ConstraintViolation<CreateGroupWithVehicleRequest>> motorcycleViolations = validator.validate(motorcycleRequest);
        assertTrue(motorcycleViolations.isEmpty(), "Should auto-detect motorcycle by license plate format");
    }
}
