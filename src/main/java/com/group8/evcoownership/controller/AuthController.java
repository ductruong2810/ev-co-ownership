package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.AuthService;
import com.group8.evcoownership.service.LogoutService;
import com.group8.evcoownership.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    private LogoutService logoutService; // ← THÊM DEPENDENCY NÀY
    @Autowired
    private JwtUtil jwtUtil;

    //Login
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (jwtUtil.validateToken(refreshToken)) {
            String email = jwtUtil.extractEmail(refreshToken);
            User user = userRepository.findByEmail(email).orElseThrow();
            return ResponseEntity.ok(LoginResponseDTO.builder()
                    .accessToken(jwtUtil.generateToken(user))
                    .refreshToken(refreshToken)
                    //hoặc sinh lại mới
                    .build());
        }
        return ResponseEntity.status(401).build();
    }

    //Register
    //Controller tiếp nhận RegisterRequestDTO
    //sau đó gọi thằng authService.register() để xử lý lozic
//    @PostMapping("/register")
//    //thêm @Valid vào req để Spring tự kiểm tra
//    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDTO request) {
//        authService.register(request);
//        return ResponseEntity.ok("User registered successfully hihi");
//    }
    //chua comment

    // STEP 1: Gửi OTP
    @PostMapping("/register/request-otp")
    public ResponseEntity<Map<String, String>> requestOtp(@Valid @RequestBody RegisterRequestDTO request) {
        String message = authService.requestOtp(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    // STEP 2: Xác minh OTP
    @PostMapping("/register/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(
            @RequestParam String email,
            @RequestParam String otp
    ) {
        String message = authService.verifyOtp(email, otp);
        return ResponseEntity.ok(Map.of("message", message));
    }

    /**
     * Endpoint resend OTP
     */
    @PostMapping("/register/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @Valid @RequestBody ResendOtpRequestDTO request) {

        String message = authService.resendOtp(request.getEmail());
        return ResponseEntity.ok(Map.of("message", message));
    }

    /// Không cần inject LogoutService nữa
// Chỉ cần AuthService
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

            // Gọi LogoutService
            logoutService.logout(token);

            return ResponseEntity.ok(Map.of(
                    "message", "Đăng xuất thành công"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Có lỗi xảy ra: " + e.getMessage()
            ));
        }
    }

    /**
     * BƯỚC 1: Quên mật khẩu - Gửi OTP
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        String message = authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", message));
    }

    /**
     * BƯỚC 2: Verify OTP - Nhận reset token
     */
    @PostMapping("/forgot-password/verify-reset-otp")
    public ResponseEntity<VerifyResetOtpResponseDTO> verifyResetOtp(
            @Valid @RequestBody VerifyResetOtpRequestDTO request) {

        VerifyResetOtpResponseDTO response = authService.verifyResetOtp(
                request.getOtp()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * BƯỚC 3: Đặt lại mật khẩu với reset token
     */
    @PostMapping("/forgot-password/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO request) {

        String message = authService.resetPasswordWithToken(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    /**
     * Resend OTP (optional)
     */
    @PostMapping("/forgot-password/resend-otp")
    public ResponseEntity<Map<String, String>> resendPasswordResetOtp(
            @Valid @RequestBody ForgotPasswordRequestDTO request) {

        String message = authService.resendPasswordResetOtp(request.getEmail());
        return ResponseEntity.ok(Map.of("message", message));
    }
}
