package com.group8.evcoownership.exception;

import com.group8.evcoownership.dto.ErrorResponseDTO;
import com.group8.evcoownership.dto.ValidationErrorResponseDTO;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ========== VALIDATION ERRORS (ĐÃ SỬA) ==========
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<ValidationErrorResponseDTO.FieldError> errors = new ArrayList<>();

        // Field-level errors (email, password, fullName...)
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String defaultMessage = error.getDefaultMessage();
            errors.add(ValidationErrorResponseDTO.FieldError.builder()
                    .field(error.getField())
                    .message(defaultMessage != null ? defaultMessage : "Invalid value")
                    .build());
        });

        // ========== ĐÃ SỬA: Class-level errors ==========
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            // Detect loại validation để set field name phù hợp
            String fieldName = "general";
            String errorCode = error.getCode();
            String defaultMessage = error.getDefaultMessage();
            String loweredMessage = defaultMessage != null ? defaultMessage.toLowerCase() : "";

            if (errorCode != null) {
                if (errorCode.contains("PasswordMatches") ||
                        loweredMessage.contains("password")) {
                    fieldName = "password";
                } else if (errorCode.contains("EmailMatches") ||
                        loweredMessage.contains("email")) {
                    fieldName = "email";
                }
            }

            errors.add(ValidationErrorResponseDTO.FieldError.builder()
                    .field(fieldName)
                    .message(defaultMessage != null ? defaultMessage : "Invalid request")
                    .build());
        });

        ValidationErrorResponseDTO response = ValidationErrorResponseDTO.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid data")
                .path(request.getDescription(false).replace("uri=", ""))
                .errors(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // ========== CONSTRAINT VIOLATIONS ==========
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {

        List<ValidationErrorResponseDTO.FieldError> errors = new ArrayList<>();

        ex.getConstraintViolations().forEach(violation -> errors.add(ValidationErrorResponseDTO.FieldError.builder()
                .field(violation.getPropertyPath().toString())
                .message(violation.getMessage())
                .build()));

        ValidationErrorResponseDTO response = ValidationErrorResponseDTO.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid data")
                .path(request.getDescription(false).replace("uri=", ""))
                .errors(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // ========== 401 - UNAUTHORIZED ==========
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request) {

        logger.warn("Invalid credentials attempt from: {}", request.getDescription(false));

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // ========== 404 - NOT FOUND ==========
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    // ========== 409 - CONFLICT ==========
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    // ========== 400 - BAD REQUEST ==========
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.badRequest().body(response);
    }

    // ========== 400 - IMAGE VALIDATION ==========
    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleImageValidation(
            ImageValidationException ex, WebRequest request) {

        logger.warn("Image validation failed: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Image Validation Failed",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== 500 - INTERNAL SERVER ERROR (FALLBACK) ==========
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleAll(Exception ex, WebRequest request) {
        logger.error("An unexpected error occurred: ", ex);

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.internalServerError().body(response);
    }
}
