package com.group8.evcoownership.exception;

import com.group8.evcoownership.dto.ErrorResponseDTO;
import com.group8.evcoownership.dto.ValidationErrorResponseDTO;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ========== VALIDATION ERRORS ==========
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        List<ValidationErrorResponseDTO.FieldError> errors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String defaultMessage = error.getDefaultMessage();
            errors.add(ValidationErrorResponseDTO.FieldError.builder()
                    .field(error.getField())
                    .message(defaultMessage != null ? defaultMessage : "Invalid value")
                    .build());
        });

        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            String fieldName = "general";
            String errorCode = error.getCode();
            String defaultMessage = error.getDefaultMessage();
            String loweredMessage = defaultMessage != null ? defaultMessage.toLowerCase() : "";

            if (errorCode != null) {
                if (errorCode.contains("PasswordMatches") || loweredMessage.contains("password")) {
                    fieldName = "password";
                } else if (errorCode.contains("EmailMatches") || loweredMessage.contains("email")) {
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

    // ========== FILE UPLOAD - SIZE EXCEEDED ==========
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {

        logger.warn("File size exceeded: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "File Too Large",
                "Kích thước file vượt quá giới hạn cho phép (tối đa 10MB)",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    // ========== FILE UPLOAD - MULTIPART ERROR ==========
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMultipartException(
            MultipartException ex, WebRequest request) {

        logger.warn("Multipart error: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Lỗi upload file. Vui lòng kiểm tra lại file và thử lại",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== AUTHENTICATION ERRORS ==========
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationException(
            Exception ex, WebRequest request) {

        logger.warn("Authentication failed: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Email hoặc mật khẩu không đúng",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // ========== ACCESS DENIED (403) ==========
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {

        logger.warn("Access denied: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Bạn không có quyền truy cập tài nguyên này",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // ========== 404 - NO RESOURCE FOUND (Spring 6.x - MỚI THÊM) ==========
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {

        logger.warn("No resource found: {}", ex.getResourcePath());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "Endpoint không tồn tại. Vui lòng kiểm tra lại URL",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ========== 404 - NO HANDLER FOUND (Spring 5.x) ==========
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoHandlerFound(
            NoHandlerFoundException ex, WebRequest request) {

        logger.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "Endpoint không tồn tại. Vui lòng kiểm tra lại URL",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ========== 405 - METHOD NOT ALLOWED ==========
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {

        logger.warn("Method not allowed: {} for {}", ex.getMethod(), request.getDescription(false));

        String supportedMethods = "N/A";
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            supportedMethods = ex.getSupportedHttpMethods().stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("N/A");
        }

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method Not Allowed",
                String.format("Phương thức %s không được hỗ trợ. Phương thức hỗ trợ: %s",
                        ex.getMethod(), supportedMethods),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    // ========== CUSTOM - RESOURCE NOT FOUND ==========
    @ExceptionHandler(com.group8.evcoownership.exception.ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(
            com.group8.evcoownership.exception.ResourceNotFoundException ex, WebRequest request) {

        logger.warn("Resource not found: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ========== CUSTOM - UNAUTHORIZED EXCEPTION ==========
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnauthorizedException(
            UnauthorizedException ex, WebRequest request) {

        logger.warn("Unauthorized action: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // ========== CUSTOM - FILE STORAGE EXCEPTION ==========
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponseDTO> handleFileStorageException(
            FileStorageException ex, WebRequest request) {

        logger.error("File storage error: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "File Storage Error",
                "Lỗi lưu trữ file. Vui lòng thử lại sau",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========== CUSTOM - INVALID CREDENTIALS ==========
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
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // ========== 404 - ENTITY NOT FOUND ==========
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
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
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
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
                "Đã xảy ra lỗi không xác định. Vui lòng thử lại sau",
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.internalServerError().body(response);
    }
}
