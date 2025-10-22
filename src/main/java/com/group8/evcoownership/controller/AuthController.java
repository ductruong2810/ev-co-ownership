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
@Tag(name = "Authentication", description = "Đăng nhập, đăng ký, OTP, đổi mật khẩu")
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
    @Operation(summary = "Đăng nhập", description = "Xác thực người dùng và trả về access/refresh token")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ================= REFRESH TOKEN =================
    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token", description = "Nhận access token mới bằng refresh token hợp lệ")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {

        String refreshToken = request.getRefreshToken();

        try {
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Refresh token không được để trống"));
            }

            if (!jwtUtil.validateToken(refreshToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token không hợp lệ hoặc đã hết hạn"));
            }

            String email = jwtUtil.extractEmail(refreshToken);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

            if (user.getStatus() != UserStatus.ACTIVE) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Tài khoản chưa được kích hoạt"));
            }

            String newAccessToken = jwtUtil.generateToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

            log.info("Tokens refreshed successfully for user: {}", email);

            return ResponseEntity.ok(LoginResponseDTO.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .role(user.getRole().getRoleName().name())  // ← THÊM DÒNG NÀY
                    .build());

        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Không thể làm mới token. Vui lòng đăng nhập lại."));
        }
    }

    // ================= REGISTER - STEP 1: GỬI OTP =================
    @PostMapping("/register/request-otp")
    @Operation(summary = "Yêu cầu OTP đăng ký", description = "Gửi OTP đến email để xác thực đăng ký")
    public ResponseEntity<OtpResponseDTO> requestOtp(@Valid @RequestBody RegisterRequestDTO request) {
        OtpResponseDTO response = authService.requestOtp(request);
        return ResponseEntity.ok(response);
    }

    // ================= VERIFY OTP CHUNG (UNIFIED) =================
    @PostMapping("/verify-otp")
    @Operation(summary = "Xác minh OTP", description = "Xác minh OTP cho đăng ký hoặc đặt lại mật khẩu")
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
                            "path", "/api/auth/verify-otp"
                    ));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "Đã xảy ra lỗi không xác định. Vui lòng thử lại sau",
                            "path", "/api/auth/verify-otp"
                    ));
        }
    }

    // ================= RESEND OTP (UNIFIED - REGISTRATION & PASSWORD_RESET) =================
    @PostMapping("/resend-otp")
    @Operation(summary = "Gửi lại OTP", description = "Gửi lại OTP cho đăng ký hoặc đặt lại mật khẩu")
    public ResponseEntity<OtpResponseDTO> resendOtp(@Valid @RequestBody ResendOtpRequestDTO request) {
        OtpResponseDTO response = authService.resendOtp(request.getEmail(), request.getType());
        return ResponseEntity.ok(response);
    }

    // ================= LOGOUT =================
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất", description = "Vô hiệu hóa token hiện tại")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không tìm thấy token"));
            }

            String token = authHeader.substring(7);
            logoutService.logout(token);

            return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));

        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Có lỗi xảy ra khi đăng xuất"));
        }
    }

    // ================= FORGOT PASSWORD - STEP 1: GỬI OTP =================
    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu - yêu cầu OTP", description = "Gửi OTP để đặt lại mật khẩu")
    public ResponseEntity<OtpResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        OtpResponseDTO response = authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(response);
    }

    // ================= FORGOT PASSWORD - STEP 3: RESET PASSWORD (AUTO LOGIN) =================
    @PostMapping("/forgot-password/reset-password")
    @Operation(summary = "Đặt lại mật khẩu", description = "Đặt lại mật khẩu bằng OTP/refresh token, tự động đăng nhập")
    public ResponseEntity<ResetPasswordResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        ResetPasswordResponseDTO response = authService.resetPasswordWithToken(request);
        return ResponseEntity.ok(response);
    }

    // ================= CHANGE PASSWORD =================
    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu", description = "Người dùng đã đăng nhập có thể đổi mật khẩu")
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
                                "message", "Bạn cần đăng nhập để thực hiện thao tác này",
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
                                "message", "Không thể xác thực người dùng",
                                "path", "/api/auth/change-password"
                        ));
            }

            String message = authService.changePassword(email, request);
            return ResponseEntity.ok(Map.of("message", message));

        } catch (IllegalArgumentException e) {
            log.error("Validation error during password change: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "path", "/api/auth/change-password"
                    ));

        } catch (Exception e) {
            log.error("Unexpected error during password change: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "timestamp", LocalDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "Đã xảy ra lỗi không xác định. Vui lòng thử lại",
                            "path", "/api/auth/change-password"
                    ));
        }
    }
}
