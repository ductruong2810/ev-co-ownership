package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.AuthService;
import com.group8.evcoownership.service.LogoutService;
import com.group8.evcoownership.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth") // Định nghĩa prefix cho tất cả API xác thực là /api/auth
@Tag(name = "Authentication", description = "Login, Register, OTP, Password Management")
// Dùng cho Swagger để mô tả nhóm API
public class AuthController {

    @Autowired
    private AuthService authService; // Service xử lý logic đăng nhập, đăng ký, token,...

    @Autowired
    private LogoutService logoutService; // Service xử lý logic đăng xuất (invalidate token)

    // 1. LOGIN
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and return access/refresh tokens")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        // Nhận thông tin đăng nhập từ client → gọi authService.login() → trả về token
        return ResponseEntity.ok(authService.login(request));
    }

    // 2. REFRESH TOKEN
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using valid refresh token")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        // Nhận refresh token → gọi service tạo mới access token
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    // 3. REQUEST OTP ĐĂNG KÝ
    @PostMapping("/register/request-otp")
    @Operation(summary = "Request registration OTP", description = "Send OTP to email for registration verification")
    public ResponseEntity<OtpResponseDTO> requestOtp(@Valid @RequestBody RegisterRequestDTO request) {
        // Gửi OTP xác thực email khi người dùng đăng ký
        return ResponseEntity.ok(authService.requestOtp(request));
    }

    // 4. XÁC THỰC OTP
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP for registration or password reset")
    public ResponseEntity<VerifyOtpResponseDTO> verifyOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        // Kiểm tra OTP có hợp lệ hay không (đăng ký hoặc quên mật khẩu)
        return ResponseEntity.ok(authService.verifyOtp(request.getOtp(), request.getType()));
    }

    // 5. GỬI LẠI OTP
    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Resend OTP for registration or password reset")
    public ResponseEntity<OtpResponseDTO> resendOtp(@Valid @RequestBody ResendOtpRequestDTO request) {
        // Gửi lại mã OTP khi người dùng chưa nhận hoặc mã hết hạn
        return ResponseEntity.ok(authService.resendOtp(request.getEmail(), request.getType()));
    }

    // 6. LOGOUT
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidate current token")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        // Lấy header Authorization từ request
        String authHeader = request.getHeader("Authorization");

        // Nếu không có token hoặc token không bắt đầu bằng "Bearer " → báo lỗi
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token not found");
        }

        // Cắt bỏ tiền tố "Bearer " để lấy phần token thực tế
        String token = authHeader.substring(7);

        // Gọi service để vô hiệu hoá token (thường là blacklist hoặc xóa khỏi cache)
        logoutService.logout(token);

        // Trả về thông báo logout thành công
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    // 7. QUÊN MẬT KHẨU - GỬI OTP
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password - request OTP", description = "Send OTP to reset password")
    public ResponseEntity<OtpResponseDTO> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        // Khi người dùng quên mật khẩu → gửi OTP về email
        return ResponseEntity.ok(authService.forgotPassword(request.getEmail()));
    }

    // 8. ĐẶT LẠI MẬT KHẨU
    @PostMapping("/forgot-password/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using token, auto login")
    public ResponseEntity<ResetPasswordResponseDTO> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        // Xác thực token hợp lệ → đổi mật khẩu mới → tự động đăng nhập
        return ResponseEntity.ok(authService.resetPasswordWithToken(request));
    }

    // 9. ĐỔI MẬT KHẨU (KHI ĐÃ LOGIN)
    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Logged-in users can change their password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication) {

        // Kiểm tra người dùng đã đăng nhập hay chưa
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("You need to login to perform this action");
        }

        // Lấy email người dùng từ đối tượng Authentication
        String email = AuthUtils.getCurrentUserEmail(authentication);

        // Nếu không lấy được email → lỗi xác thực
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalStateException("Unable to authenticate user");
        }

        // Gọi service để đổi mật khẩu
        String message = authService.changePassword(email, request);

        // Trả về thông báo kết quả
        return ResponseEntity.ok(Map.of("message", message));
    }
}

