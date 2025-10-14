package com.group8.evcoownership.exception;

import com.group8.evcoownership.dto.ErrorResponseDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ========== 401 - Unauthorized (MỨC ƯU TIÊN CAO) ==========
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

    // ========== 404 - Not Found ==========
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

    // ========== 409 - Conflict ==========
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

    // ========== 400 - Bad Request ==========
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

    // ========== Validation Errors ==========
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<Map<String, String>> errors = new ArrayList<>();

        // Field-level errors
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            Map<String, String> err = new HashMap<>();
            err.put("field", error.getField());
            err.put("message", error.getDefaultMessage());
            errors.add(err);
        });

        // Class-level errors
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            Map<String, String> err = new HashMap<>();
            err.put("field", "global");
            err.put("message", error.getDefaultMessage());
            errors.add(err);
        });

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                errors.toString(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== Constraint Violations ==========
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = new ArrayList<>();

        ex.getConstraintViolations().forEach(violation -> {
            Map<String, String> err = new HashMap<>();
            err.put("field", violation.getPropertyPath().toString());
            err.put("message", violation.getMessage());
            errors.add(err);
        });

        return ResponseEntity.badRequest().body(errors);
    }

    // ========== 500 - Fallback (ĐẶT CUỐI CÙNG) ==========
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleAll(Exception ex, WebRequest request) {
        logger.error("An unexpected error occurred: ", ex);

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected internal server error occurred.",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.internalServerError().body(response);
    }
}
