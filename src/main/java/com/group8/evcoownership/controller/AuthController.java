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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
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
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ================= REFRESH TOKEN =================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {

        // Lấy refresh token từ DTO
        String refreshToken = request.getRefreshToken();

        try {
            // 1. Validate refresh token
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Refresh token không được để trống"));
            }

            if (!jwtUtil.validateToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token không hợp lệ hoặc đã hết hạn"));
            }

            // 2. Extract email
            String email = jwtUtil.extractEmail(refreshToken);

            // 3. Find user
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

            // 4. Check user status
            if (user.getStatus() != UserStatus.Active) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tài khoản chưa được kích hoạt"));
            }

            // 5. Generate NEW tokens (Refresh Token Rotation)
            String newAccessToken = jwtUtil.generateToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

            log.info("Tokens refreshed successfully for user: {}", email);

            // 6. Return new tokens
            return ResponseEntity.ok(LoginResponseDTO.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build());

        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Không thể làm mới token. Vui lòng đăng nhập lại."));
        }
    }

    // ================= REGISTER - STEP 1: GỬI OTP =================
    @PostMapping("/register/request-otp")
    public ResponseEntity<Map<String, String>> requestOtp(@Valid @RequestBody RegisterRequestDTO request) {
        String message = authService.requestOtp(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ================= REGISTER - STEP 2: XÁC MINH OTP =================
    @PostMapping("/register/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @RequestBody String email,
            @RequestBody String otp) {

        String message = authService.verifyOtp(email, otp);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ================= REGISTER - RESEND OTP =================
    @PostMapping("/register/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @Valid @RequestBody ResendOtpRequestDTO request) {

        String message = authService.resendOtp(request.getEmail());
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ================= LOGOUT =================
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Không tìm thấy token"
                ));
            }

            String token = authHeader.substring(7);
            logoutService.logout(token);

            return ResponseEntity.ok(Map.of(
                    "message", "Đăng xuất thành công"
            ));

        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Có lỗi xảy ra khi đăng xuất"));
        }
    }

    // ================= FORGOT PASSWORD - STEP 1: GỬI OTP =================
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        String message = authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ================= FORGOT PASSWORD - STEP 2: VERIFY OTP =================
    @PostMapping("/forgot-password/verify-reset-otp")
    public ResponseEntity<VerifyResetOtpResponseDTO> verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequestDTO request) {

        VerifyResetOtpResponseDTO response = authService.verifyResetOtp(request.getOtp());
        return ResponseEntity.ok(response);
    }

    // ================= FORGOT PASSWORD - STEP 3: RESET PASSWORD =================
    @PostMapping("/forgot-password/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {

        String message = authService.resetPasswordWithToken(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ================= FORGOT PASSWORD - RESEND OTP =================
    @PostMapping("/forgot-password/resend-otp")
    public ResponseEntity<Map<String, String>> resendPasswordResetOtp(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        String message = authService.resendPasswordResetOtp(request.getEmail());
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ================= CHANGE PASSWORD (CẦN ĐĂNG NHẬP) =================
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication) {

        // Lấy email từ user đã đăng nhập
        User user = (User) authentication.getPrincipal();
        String email = user.getEmail();

        String message = authService.changePassword(email, request);
        return ResponseEntity.ok(Map.of("message", message));
    }
}
