package com.group8.evcoownership.exception;

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

    // ========== 400 - INVALID JSON FORMAT ==========
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleInvalidJSON(
            org.springframework.http.converter.HttpMessageNotReadableException ex, WebRequest request) {

        logger.warn("Invalid JSON format: {}", ex.getMessage());

        String field = "body";
        String message = "Invalid JSON format";

        String errorMsg = ex.getMessage();
        if (errorMsg != null) {
            if (errorMsg.contains("contractId")) {
                field = "contractId";
                message = "Contract ID must be a number";
            } else if (errorMsg.contains("action")) {
                field = "action";
                message = "Action must be a string";
            } else if (errorMsg.contains("reason")) {
                field = "reason";
                message = "Reason must be a string";
            }
        }

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Validation Failed", message, field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - INVALID CONTRACT ACTION ==========
    @ExceptionHandler(InvalidContractActionException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleInvalidContractAction(
            InvalidContractActionException ex, WebRequest request) {

        logger.warn("Invalid contract action: {}", ex.getMessage());

        String field = "action";
        if (ex.getMessage().contains("reason")) {
            field = "reason";
        } else if (ex.getMessage().contains("Contract ID")) {
            field = "contractId";
        }

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Validation Failed", ex.getMessage(), field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - INVALID BOOKING ==========
    //them vao de xu ly loi booking
    @ExceptionHandler(InvalidBookingException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleInvalidBooking(
            InvalidBookingException ex, WebRequest request) {

        logger.warn("Invalid booking: {}", ex.getMessage());

        String field = determineFieldFromBookingMessage(ex.getMessage());

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Invalid Booking", ex.getMessage(), field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - NULL POINTER (Missing required fields) ==========
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleNullPointer(
            NullPointerException ex, WebRequest request) {

        logger.warn("Null pointer exception: {}", ex.getMessage());

        String message = "Required field is missing or invalid";
        String field = "unknown";

        if (ex.getMessage() != null) {
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("userid")) {
                field = "userId";
                message = "User ID is required and must be a valid number";
            } else if (msg.contains("vehicleid")) {
                field = "vehicleId";
                message = "Vehicle ID is required and must be a valid number";
            } else if (msg.contains("startdatetime")) {
                field = "startDateTime";
                message = "Start date time is required";
            } else if (msg.contains("enddatetime")) {
                field = "endDateTime";
                message = "End date time is required";
            }
        }

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Bad Request", message, field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // Helper method cho booking errors
    private String determineFieldFromBookingMessage(String message) {
        if (message == null) return "unknown";

        String lower = message.toLowerCase();

        if (lower.contains("user id") || lower.contains("userid")) return "userId";
        if (lower.contains("vehicle id") || lower.contains("vehicleid")) return "vehicleId";
        if (lower.contains("start") && lower.contains("time")) return "startDateTime";
        if (lower.contains("end") && lower.contains("time")) return "endDateTime";
        if (lower.contains("quota")) return "quota";
        if (lower.contains("buffer") || lower.contains("overlap")) return "timeSlot";
        if (lower.contains("booking")) return "booking";

        return "general";
    }


    // ========== FILE UPLOAD - SIZE EXCEEDED ==========
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {

        logger.warn("File size exceeded: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                ValidationErrorResponseDTO.singleError(413, "File Too Large",
                        "File size exceeds the allowed limit (maximum 10MB)", "file",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== FILE UPLOAD - MULTIPART ERROR ==========
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleMultipartException(
            MultipartException ex, WebRequest request) {

        logger.warn("Multipart error: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Bad Request",
                        "File upload error. Please check the file and try again", "file",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== AUTHENTICATION ERRORS ==========
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ValidationErrorResponseDTO> handleAuthenticationException(
            Exception ex, WebRequest request) {

        logger.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ValidationErrorResponseDTO.singleError(401, "Unauthorized",
                        "Invalid email or password", "email,password",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== ACCESS DENIED (403) ==========
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {

        logger.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ValidationErrorResponseDTO.singleError(403, "Forbidden",
                        "You do not have permission to access this resource", "authorization",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 404 - NO RESOURCE FOUND ==========
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {

        logger.warn("No resource found: {}", ex.getResourcePath());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ValidationErrorResponseDTO.singleError(404, "Not Found",
                        "Endpoint does not exist. Please check the URL", "path",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 404 - NO HANDLER FOUND ==========
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleNoHandlerFound(
            NoHandlerFoundException ex, WebRequest request) {

        logger.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ValidationErrorResponseDTO.singleError(404, "Not Found",
                        "Endpoint does not exist. Please check the URL", "path",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 405 - METHOD NOT ALLOWED ==========
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleMethodNotAllowed(
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

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                ValidationErrorResponseDTO.singleError(405, "Method Not Allowed",
                        String.format("Method %s is not supported. Supported methods: %s", ex.getMethod(), supportedMethods),
                        "method", request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== CUSTOM - RESOURCE NOT FOUND ==========
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {

        logger.warn("Resource not found: {}", ex.getMessage());

        String field = determineFieldFromMessage(ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ValidationErrorResponseDTO.singleError(404, "Not Found", ex.getMessage(), field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== CUSTOM - UNAUTHORIZED EXCEPTION ==========
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleUnauthorizedException(
            UnauthorizedException ex, WebRequest request) {

        logger.warn("Unauthorized action: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ValidationErrorResponseDTO.singleError(403, "Forbidden", ex.getMessage(), "authorization",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== CUSTOM - FILE STORAGE EXCEPTION ==========
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleFileStorageException(
            FileStorageException ex, WebRequest request) {

        logger.error("File storage error: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ValidationErrorResponseDTO.singleError(500, "File Storage Error",
                        "File storage error. Please try again later", "file",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== CUSTOM - INVALID CREDENTIALS ==========
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request) {

        logger.warn("Invalid credentials attempt from: {}", request.getDescription(false));

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ValidationErrorResponseDTO.singleError(401, "Unauthorized", ex.getMessage(), "email,password",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 404 - ENTITY NOT FOUND ==========
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {

        String field = determineFieldFromMessage(ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ValidationErrorResponseDTO.singleError(404, "Not Found", ex.getMessage(), field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 409 - CONFLICT ==========
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        logger.warn("Illegal state: {}", ex.getMessage());

        String errorType = "Conflict";
        HttpStatus status = HttpStatus.CONFLICT;
        String field = determineFieldFromMessage(ex.getMessage());

        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Contract must be signed")) {
                errorType = "Contract Not Signed";
                status = HttpStatus.BAD_REQUEST;
                field = "contract";
            } else if (message.contains("already paid")) {
                errorType = "Payment Already Completed";
                field = "payment";
            } else if (message.contains("not in PENDING status")) {
                errorType = "Invalid Payment Status";
                field = "status";
            } else if (message.contains("not activated")) {
                field = "status";
            } else if (message.contains("login")) {
                field = "authentication";
            }
        }

        return ResponseEntity.status(status).body(
                ValidationErrorResponseDTO.singleError(status.value(), errorType, ex.getMessage(), field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - BAD REQUEST ==========
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        logger.warn("Illegal argument: {}", ex.getMessage());

        String errorType = "Bad Request";
        String field = determineFieldFromMessage(ex.getMessage());

        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Deposit amount must be exactly")) {
                errorType = "Invalid Deposit Amount";
                field = "amount";
            } else if (message.contains("Amount must be > 0")) {
                errorType = "Invalid Amount";
                field = "amount";
            } else if (message.contains("transactionCode is required")) {
                errorType = "Missing Transaction Code";
                field = "transactionCode";
            }
        }

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, errorType, ex.getMessage(), field,
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - IMAGE VALIDATION ==========
    @ExceptionHandler(ImageValidationException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleImageValidation(
            ImageValidationException ex, WebRequest request) {

        logger.warn("Image validation failed: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Image Validation Failed", ex.getMessage(), "image",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - INSUFFICIENT DOCUMENTS ==========
    @ExceptionHandler(InsufficientDocumentsException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleInsufficientDocuments(
            InsufficientDocumentsException ex, WebRequest request) {

        logger.warn("Insufficient documents: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Bad Request", ex.getMessage(), "documents",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 400 - DEPOSIT PAYMENT ERRORS ==========
    @ExceptionHandler(DepositPaymentException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleDepositPaymentException(
            DepositPaymentException ex, WebRequest request) {

        logger.warn("Deposit payment error: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                ValidationErrorResponseDTO.singleError(400, "Deposit Payment Error", ex.getMessage(), "payment",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 409 - PAYMENT CONFLICT ==========
    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ValidationErrorResponseDTO> handlePaymentConflictException(
            PaymentConflictException ex, WebRequest request) {

        logger.warn("Payment conflict: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ValidationErrorResponseDTO.singleError(409, "Payment Conflict", ex.getMessage(), "payment",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== 500 - INTERNAL SERVER ERROR (FALLBACK) ==========
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ValidationErrorResponseDTO> handleAll(Exception ex, WebRequest request) {
        logger.error("An unexpected error occurred: ", ex);

        return ResponseEntity.internalServerError().body(
                ValidationErrorResponseDTO.singleError(500, "Internal Server Error",
                        "An unexpected error occurred", "unknown",
                        request.getDescription(false).replace("uri=", ""))
        );
    }

    // ========== HELPER METHOD ==========
    private String determineFieldFromMessage(String message) {
        if (message == null) return "unknown";

        String lower = message.toLowerCase();

        if (lower.contains("email")) return "email";
        if (lower.contains("password") && lower.contains("confirm")) return "password,confirmPassword";
        if (lower.contains("old password")) return "oldPassword";
        if (lower.contains("new password")) return "newPassword";
        if (lower.contains("confirm password")) return "confirmPassword";
        if (lower.contains("reset token") || lower.contains("resettoken")) return "resetToken";
        if (lower.contains("refresh token") || lower.contains("refreshtoken")) return "refreshToken";
        if (lower.contains("otp")) return "otp";
        if (lower.contains("phone")) return "phone";
        if (lower.contains("token")) return "token";
        if (lower.contains("document")) return "document";
        if (lower.contains("file")) return "file";
        if (lower.contains("payment")) return "payment";
        if (lower.contains("contract")) return "contract";
        if (lower.contains("amount")) return "amount";

        return "general";
    }
}