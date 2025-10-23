package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.AuthService;
import com.group8.evcoownership.service.LogoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, Register, OTP, Password Management")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private LogoutService logoutService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return access/refresh tokens")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using valid refresh token")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/register/request-otp")
    @Operation(summary = "Request registration OTP", description = "Send OTP to email for registration verification")
    public ResponseEntity<OtpResponseDTO> requestOtp(@Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.ok(authService.requestOtp(request));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP for registration or password reset")
    public ResponseEntity<VerifyOtpResponseDTO> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        return ResponseEntity.ok(authService.verifyOtp(request.getOtp(), request.getType()));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Resend OTP for registration or password reset")
    public ResponseEntity<OtpResponseDTO> resendOtp(@Valid @RequestBody ResendOtpRequestDTO request) {
        return ResponseEntity.ok(authService.resendOtp(request.getEmail(), request.getType()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate current token")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token not found");
        }

        String token = authHeader.substring(7);
        logoutService.logout(token);

        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password - request OTP", description = "Send OTP to reset password")
    public ResponseEntity<OtpResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        return ResponseEntity.ok(authService.forgotPassword(request.getEmail()));
    }

    @PostMapping("/forgot-password/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using token, auto login")
    public ResponseEntity<ResetPasswordResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        return ResponseEntity.ok(authService.resetPasswordWithToken(request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Logged-in users can change their password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("You need to login to perform this action");
        }

        String email = com.group8.evcoownership.util.AuthUtils.getCurrentUserEmail(authentication);

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalStateException("Unable to authenticate user");
        }

        String message = authService.changePassword(email, request);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
