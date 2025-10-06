package com.group8.evcoownership.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //Xử lý lỗi validation từ @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();

        // Field-level errors
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            Map<String, String> err = new HashMap<>();
            err.put("field", error.getField());
            err.put("message", error.getDefaultMessage());
            errors.add(err);
        });

        // Class-level errors (như @PasswordMatches)
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            Map<String, String> err = new HashMap<>();
            err.put("field", "global");
            err.put("message", error.getDefaultMessage());
            errors.add(err);
        });

        return ResponseEntity.badRequest().body(errors);
    }

    // Xử lý các constraint khác
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

    // Fallback cho các lỗi khác
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAll(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("field", "system");
        error.put("message", ex.getMessage());
        return ResponseEntity.internalServerError().body(Collections.singletonList(error));
    }
}
