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

    // ========== FILE UPLOAD - SIZE EXCEEDED ==========
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {

        logger.warn("File size exceeded: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "File Too Large",
                "File size exceeds the allowed limit (maximum 10MB)",
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
                "File upload error. Please check the file and try again",
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
                "Invalid email or password",
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
                "You do not have permission to access this resource",
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
                "Endpoint does not exist. Please check the URL",
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
                "Endpoint does not exist. Please check the URL",
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
        var supportedMethodsSet = ex.getSupportedHttpMethods();
        if (supportedMethodsSet != null && !supportedMethodsSet.isEmpty()) {
            supportedMethods = supportedMethodsSet.stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("N/A");
        }

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Method Not Allowed",
                String.format("Method %s is not supported. Supported methods: %s",
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
                "File storage error. Please try again later",
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

        logger.warn("Illegal state: {}", ex.getMessage());

        // Xác định loại lỗi dựa trên message
        String errorType = "Conflict";
        HttpStatus status = HttpStatus.CONFLICT;

        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Contract must be signed")) {
                errorType = "Contract Not Signed";
                status = HttpStatus.BAD_REQUEST;
            } else if (message.contains("already paid")) {
                errorType = "Payment Already Completed";
            } else if (message.contains("not in PENDING status")) {
                errorType = "Invalid Payment Status";
            }
        }

        ErrorResponseDTO errorResponse = new ErrorResponseDTO(
                status.value(),
                errorType,
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(status).body(errorResponse);
    }

    // ========== 400 - BAD REQUEST ==========
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        logger.warn("Illegal argument: {}", ex.getMessage());

        // Xác định loại lỗi dựa trên message
        String errorType = "Bad Request";
        HttpStatus status = HttpStatus.BAD_REQUEST;

        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Deposit amount must be exactly")) {
                errorType = "Invalid Deposit Amount";
            } else if (message.contains("Amount must be > 0")) {
                errorType = "Invalid Amount";
            } else if (message.contains("transactionCode is required")) {
                errorType = "Missing Transaction Code";
            }
        }

        ErrorResponseDTO response = new ErrorResponseDTO(
                status.value(),
                errorType,
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(status).body(response);
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

    // ========== 400 - INSUFFICIENT DOCUMENTS ==========
    @ExceptionHandler(InsufficientDocumentsException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientDocuments(
            InsufficientDocumentsException ex, WebRequest request) {

        logger.warn("Insufficient documents: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== 400 - DEPOSIT PAYMENT ERRORS ==========
    @ExceptionHandler(com.group8.evcoownership.exception.DepositPaymentException.class)
    public ResponseEntity<ErrorResponseDTO> handleDepositPaymentException(
            com.group8.evcoownership.exception.DepositPaymentException ex, WebRequest request) {

        logger.warn("Deposit payment error: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Deposit Payment Error",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== 409 - PAYMENT CONFLICT ==========
    @ExceptionHandler(com.group8.evcoownership.exception.PaymentConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handlePaymentConflictException(
            com.group8.evcoownership.exception.PaymentConflictException ex, WebRequest request) {

        logger.warn("Payment conflict: {}", ex.getMessage());

        ErrorResponseDTO response = new ErrorResponseDTO(
                HttpStatus.CONFLICT.value(),
                "Payment Conflict",
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
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
