package com.group8.evcoownership.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
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

    // 404 - Không tìm thấy entity (ví dụ: Group/Fund không tồn tại)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<List<Map<String, String>>> handleEntityNotFound(EntityNotFoundException ex) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", "entity");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(404).body(List.of(error));
    }

    // 409 - Lỗi nghiệp vụ/xung đột trạng thái (ví dụ: "Nhóm này đã có quỹ rồi!")
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<List<Map<String, String>>> handleIllegalState(IllegalStateException ex) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", "logic");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(409).body(List.of(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<List<Map<String, String>>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", "amount");    // hoặc "fundId"/"input"
        error.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(List.of(error));
    }

}
