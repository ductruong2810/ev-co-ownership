package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.AuthService;
import com.group8.evcoownership.service.LogoutService;
import com.group8.evcoownership.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, Register, OTP, Password Management")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private LogoutService logoutService;

    @Autowired
    private JwtUtil jwtUtil;

    // ================= LOGIN =================
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return access/refresh tokens")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
        try {
            return ResponseEntity.ok(authService.login(request));
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 401,
                            "error", "Unauthorized",
                            "message", e.getMessage(),
                            "field", "email,password",
                            "path", "/api/auth/login"
                    ));
        }
    }

    // ================= REFRESH TOKEN =================
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using valid refresh token")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        String refreshToken = request.getRefreshToken();

        try {
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", "Refresh token cannot be empty",
                                "field", "refreshToken",
                                "path", "/api/auth/refresh"
                        ));
            }

            if (!jwtUtil.validateToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 401,
                                "error", "Unauthorized",
                                "message", "Refresh token is invalid or has expired",
                                "field", "refreshToken",
                                "path", "/api/auth/refresh"
                        ));
            }

            String email = jwtUtil.extractEmail(refreshToken);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (user.getStatus() != UserStatus.ACTIVE) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 403,
                                "error", "Forbidden",
                                "message", "Account is not activated",
                                "field", "status",
                                "path", "/api/auth/refresh"
                        ));
            }

            String newAccessToken = jwtUtil.generateToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

            log.info("Tokens refreshed successfully for user: {}", email);

            return ResponseEntity.ok(LoginResponseDTO.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .role(user.getRole().getRoleName().name())
                    .build());

        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 401,
                            "error", "Unauthorized",
                            "message", "Unable to refresh token. Please login again",
                            "field", "refreshToken",
                            "path", "/api/auth/refresh"
                    ));
        }
    }

    // ================= REGISTER - STEP 1: REQUEST OTP =================
    @PostMapping("/register/request-otp")
    @Operation(summary = "Request registration OTP", description = "Send OTP to email for registration verification")
    public ResponseEntity<?> requestOtp(@Valid @RequestBody RegisterRequestDTO request) {
        try {
            OtpResponseDTO response = authService.requestOtp(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            String field = determineFieldFromMessage(e.getMessage(), "email,password,confirmPassword");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", field,
                            "path", "/api/auth/register/request-otp"
                    ));
        } catch (Exception e) {
            log.error("Registration OTP error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", e.getMessage(),
                            "field", "email",
                            "path", "/api/auth/register/request-otp"
                    ));
        }
    }

    // ================= VERIFY OTP (UNIFIED) =================
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP for registration or password reset")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        try {
            VerifyOtpResponseDTO response = authService.verifyOtp(request.getOtp(), request.getType());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", "otp",
                            "path", "/api/auth/verify-otp"
                    ));
        } catch (IllegalStateException e) {
            log.error("State error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", "otp",
                            "path", "/api/auth/verify-otp"
                    ));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An unexpected error occurred. Please try again later",
                            "field", "otp",
                            "path", "/api/auth/verify-otp"
                    ));
        }
    }

    // ================= RESEND OTP (UNIFIED) =================
    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Resend OTP for registration or password reset")
    public ResponseEntity<?> resendOtp(@Valid @RequestBody ResendOtpRequestDTO request) {
        try {
            OtpResponseDTO response = authService.resendOtp(request.getEmail(), request.getType());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Resend OTP error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", "email",
                            "path", "/api/auth/resend-otp"
                    ));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", e.getMessage(),
                            "field", "email",
                            "path", "/api/auth/resend-otp"
                    ));
        }
    }

    // ================= LOGOUT =================
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate current token")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Token not found",
                        "field", "Authorization"
                ));
            }

            String token = authHeader.substring(7);
            logoutService.logout(token);

            return ResponseEntity.ok(Map.of("message", "Logout successful"));

        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "An error occurred during logout",
                            "field", "token"
                    ));
        }
    }

    // ================= FORGOT PASSWORD - REQUEST OTP =================
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password - request OTP", description = "Send OTP to reset password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        try {
            OtpResponseDTO response = authService.forgotPassword(request.getEmail());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Forgot password error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", "email",
                            "path", "/api/auth/forgot-password"
                    ));
        } catch (IllegalStateException e) {
            log.error("Account state error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 403,
                            "error", "Forbidden",
                            "message", e.getMessage(),
                            "field", "status",
                            "path", "/api/auth/forgot-password"
                    ));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", e.getMessage(),
                            "field", "email",
                            "path", "/api/auth/forgot-password"
                    ));
        }
    }

    // ================= RESET PASSWORD WITH TOKEN =================
    @PostMapping("/forgot-password/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using token, auto login")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        try {
            ResetPasswordResponseDTO response = authService.resetPasswordWithToken(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Reset password validation error: {}", e.getMessage());
            String field = determineFieldFromMessage(e.getMessage(), "resetToken,newPassword,confirmPassword");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", field,
                            "path", "/api/auth/forgot-password/reset-password"
                    ));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", e.getMessage(),
                            "field", "resetToken",
                            "path", "/api/auth/forgot-password/reset-password"
                    ));
        }
    }

    // ================= CHANGE PASSWORD =================
    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Logged-in users can change their password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication) {

        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Unauthorized change password attempt - no authentication");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 401,
                                "error", "Unauthorized",
                                "message", "You need to login to perform this action",
                                "field", "authentication",
                                "path", "/api/auth/change-password"
                        ));
            }

            String email = com.group8.evcoownership.util.AuthUtils.getCurrentUserEmail(authentication);

            if (email == null || email.trim().isEmpty()) {
                log.warn("Invalid authentication - empty email");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 401,
                                "error", "Unauthorized",
                                "message", "Unable to authenticate user",
                                "field", "email",
                                "path", "/api/auth/change-password"
                        ));
            }

            String message = authService.changePassword(email, request);
            return ResponseEntity.ok(Map.of("message", message));

        } catch (IllegalArgumentException e) {
            log.error("Validation error during password change: {}", e.getMessage());
            String field = determineFieldFromMessage(e.getMessage(), "oldPassword,newPassword,confirmPassword");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "field", field,
                            "path", "/api/auth/change-password"
                    ));

        } catch (Exception e) {
            log.error("Unexpected error during password change: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An unexpected error occurred. Please try again",
                            "field", "unknown",
                            "path", "/api/auth/change-password"
                    ));
        }
    }

    // ================= HELPER METHOD TO DETERMINE FIELD =================
    private String determineFieldFromMessage(String message, String defaultFields) {
        message = message.toLowerCase();

        if (message.contains("email")) return "email";
        if (message.contains("password") && message.contains("confirm")) return "password,confirmPassword";
        if (message.contains("old password")) return "oldPassword";
        if (message.contains("new password")) return "newPassword";
        if (message.contains("confirm password")) return "confirmPassword";
        if (message.contains("reset token")) return "resetToken";
        if (message.contains("otp")) return "otp";

        return defaultFields;
    }
}
