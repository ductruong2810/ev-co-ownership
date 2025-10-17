package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.Role;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.exception.InvalidCredentialsException;
import com.group8.evcoownership.repository.RoleRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.utils.JwtUtil;
import com.group8.evcoownership.utils.OtpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpUtil otpUtil;

    @Autowired
    private EmailService emailService;

    // Lưu tạm thông tin người dùng chờ xác minh OTP
    private final Map<String, String> registerOtpToEmailMap = new ConcurrentHashMap<>();
    private final Map<String, RegisterRequestDTO> pendingUsers = new ConcurrentHashMap<>();
    private final Map<String, String> pendingPasswordResets = new ConcurrentHashMap<>();
    private final Map<String, String> otpToEmailMap = new ConcurrentHashMap<>();
    private final Map<String, String> resetTokens = new ConcurrentHashMap<>();

    // ================= LOGIN =================
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Tài khoản chưa được kích hoạt. Vui lòng xác thực email.");
        }

        boolean rememberMe = request.isRememberMe();

        return LoginResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(jwtUtil.generateRefreshToken(user, rememberMe))
                .build();
    }

    // ================= REQUEST OTP =================
    //chinh sua tra luon email de frontend xu ly
    public Map<String, Object> requestOtp(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email đã được đăng ký");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu và xác nhận mật khẩu không khớp");
        }

        String otp = otpUtil.generateOtp(request.getEmail());
        pendingUsers.put(request.getEmail(), request);
        registerOtpToEmailMap.put(otp, request.getEmail());
        emailService.sendOtpEmail(request.getEmail(), otp);

        log.info("OTP sent to email: {}", request.getEmail());

        return Map.of(
                "message", "Mã OTP đã được gửi đến email của bạn",
                "email", request.getEmail(),
                "expiresIn", 300 // 5 phút = 300 giây
        );
    }

    // ================= VERIFY OTP (ĐÃ SỬA - TRẢ VỀ TOKEN + USER INFO) =================
    public RegisterResponseDTO verifyOtp(String otp) {
        log.info("Verifying OTP without email parameter");

        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP không được để trống");
        }

        try {
            // Tìm email từ OTP
            String email = registerOtpToEmailMap.get(otp);
            if (email == null) {
                log.error("OTP not found or expired: {}", otp);
                throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
            }

            RegisterRequestDTO request = pendingUsers.get(email);
            if (request == null) {
                log.error("No pending registration found for email: {}", email);
                registerOtpToEmailMap.remove(otp);
                throw new IllegalStateException(
                        "Không tìm thấy thông tin đăng ký. Vui lòng yêu cầu OTP mới."
                );
            }

            boolean isOtpValid = otpUtil.verifyOtp(email, otp);
            if (!isOtpValid) {
                int remainingAttempts = otpUtil.getRemainingAttempts(email);

                if (remainingAttempts == 0) {
                    pendingUsers.remove(email);
                    registerOtpToEmailMap.remove(otp);
                    log.error("OTP verification failed - no attempts remaining");
                    throw new IllegalStateException(
                            "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu OTP mới."
                    );
                }

                log.error("Invalid OTP. Remaining attempts: {}", remainingAttempts);
                throw new IllegalArgumentException(
                        "OTP không hợp lệ. Bạn còn " + remainingAttempts + " lần thử."
                );
            }

            // Create user và generate tokens
            User user = createUser(request);

            // Cleanup
            pendingUsers.remove(email);
            registerOtpToEmailMap.remove(otp);

            // Generate tokens
            String accessToken = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user, false);

            log.info("User registered and logged in successfully: {}", email);

            return RegisterResponseDTO.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(RegisterResponseDTO.UserInfoDTO.builder()
                            .userId(user.getUserId())
                            .fullName(user.getFullName())
                            .email(user.getEmail())
                            .phoneNumber(user.getPhoneNumber())
                            .avatarUrl(user.getAvatarUrl())
                            .roleName(user.getRole().getRoleName().name())
                            .status(user.getStatus().name())
                            .createdAt(user.getCreatedAt())
                            .build())
                    .message("Đăng ký tài khoản thành công! Bạn đã được tự động đăng nhập.")
                    .build();

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OTP verification: {}", e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại.");
        }
    }

    // ========== CREATE USER (ĐÃ SỬA - TRẢ VỀ USER) ==========
    private User createUser(RegisterRequestDTO request) {
        log.info("Creating new user with email: {}", request.getEmail());

        Role coOwnerRole = roleRepository.findByRoleName(RoleName.CO_OWNER)
                .orElseThrow(() -> {
                    log.error("Role Co_owner not found in database");
                    return new IllegalStateException("Lỗi cấu hình hệ thống. Vui lòng liên hệ quản trị viên.");
                });

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhone())
                .role(coOwnerRole)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user); // ← TRẢ VỀ USER
    }

    // ================= RESEND OTP =================
    public Map<String, Object> resendOtp(String email) {
        log.info("Resending OTP for email: {}", email);

        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.warn("Resend OTP attempt for non-pending email: {}", email);
            throw new IllegalStateException(
                    "Không tìm thấy thông tin đăng ký. Vui lòng đăng ký lại từ đầu."
            );
        }

        try {
            String newOtp = otpUtil.generateOtp(email);
            registerOtpToEmailMap.put(newOtp, email);
            emailService.sendOtpEmail(email, newOtp);

            log.info("OTP resent successfully to email: {}", email);

            return Map.of(
                    "message", "Mã OTP mới đã được gửi đến email của bạn",
                    "email", email,
                    "expiresIn", 300
            );

        } catch (RuntimeException e) {
            log.error("Failed to resend OTP to email {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while resending OTP to email {}: {}",
                    email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi lại OTP. Vui lòng thử lại sau.");
        }
    }

    public void cancelPendingRegistration(String email) {
        if (pendingUsers.remove(email) != null) {
            log.info("Cancelled pending registration for email: {}", email);
        }
    }

    // ================= FORGOT PASSWORD - GỬI OTP =================
    public Map<String, Object> forgotPassword(String email) {
        log.info("Processing forgot password request for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Forgot password attempt for non-existent email: {}", email);
                    return new IllegalArgumentException("Email không tồn tại trong hệ thống");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Forgot password attempt for inactive account: {}", email);
            throw new IllegalStateException("Tài khoản chưa được kích hoạt. Vui lòng liên hệ quản trị viên.");
        }

        try {
            String otp = otpUtil.generateOtp(email);
            pendingPasswordResets.put(email, email);
            otpToEmailMap.put(otp, email);
            emailService.sendPasswordResetOtpEmail(email, otp);

            log.info("Password reset OTP sent successfully to: {}", email);

            return Map.of(
                    "message", "Mã OTP đã được gửi đến email của bạn",
                    "email", email,
                    "expiresIn", 300
            );

        } catch (RuntimeException e) {
            log.error("Failed to send password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during forgot password for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi OTP. Vui lòng thử lại sau.");
        }
    }

    // ================= VERIFY RESET OTP =================
    public VerifyResetOtpResponseDTO verifyResetOtp(String otp) {
        log.info("Verifying reset OTP");

        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP không được để trống");
        }

        try {
            String email = otpToEmailMap.get(otp);
            if (email == null) {
                log.error("OTP not found or expired: {}", otp);
                throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
            }

            if (!pendingPasswordResets.containsKey(email)) {
                log.error("No pending password reset found for email: {}", email);
                otpToEmailMap.remove(otp);
                throw new IllegalStateException(
                        "Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng yêu cầu OTP mới."
                );
            }

            boolean isOtpValid = otpUtil.verifyOtp(email, otp);
            if (!isOtpValid) {
                int remainingAttempts = otpUtil.getRemainingAttempts(email);

                if (remainingAttempts == 0) {
                    pendingPasswordResets.remove(email);
                    otpToEmailMap.remove(otp);
                    log.error("Reset OTP verification failed - no attempts remaining");
                    throw new IllegalStateException(
                            "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu OTP mới."
                    );
                }

                log.error("Invalid reset OTP. Remaining attempts: {}", remainingAttempts);
                throw new IllegalArgumentException(
                        "OTP không hợp lệ. Bạn còn " + remainingAttempts + " lần thử."
                );
            }

            String resetToken = generateResetToken();
            resetTokens.put(resetToken, email);
            pendingPasswordResets.remove(email);
            otpToEmailMap.remove(otp);

            log.info("Reset OTP verified successfully for email: {}", email);

            return VerifyResetOtpResponseDTO.builder()
                    .message("Xác thực OTP thành công. Vui lòng đặt mật khẩu mới.")
                    .resetToken(resetToken)
                    .build();

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during reset OTP verification: {}", e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại.");
        }
    }

    // ================= RESET PASSWORD WITH TOKEN =================
    public String resetPasswordWithToken(ResetPasswordRequestDTO request) {
        String resetToken = request.getResetToken();
        log.info("Processing password reset with token");

        if (resetToken == null || resetToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Reset token không được để trống");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu và xác nhận mật khẩu không khớp");
        }

        try {
            String email = resetTokens.get(resetToken);
            if (email == null) {
                log.error("Invalid or expired reset token");
                throw new IllegalArgumentException(
                        "Reset token không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu OTP mới."
                );
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản"));

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);
            resetTokens.remove(resetToken);

            log.info("Password reset successfully for email: {}", email);
            return "Đặt lại mật khẩu thành công! Bạn có thể đăng nhập với mật khẩu mới.";

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during password reset: {}", e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình đặt lại mật khẩu. Vui lòng thử lại.");
        }
    }

    // ================= RESEND PASSWORD RESET OTP =================
    public Map<String, Object> resendPasswordResetOtp(String email) {
        log.info("Resending password reset OTP for email: {}", email);

        if (!pendingPasswordResets.containsKey(email)) {
            log.warn("Resend OTP attempt for non-pending reset: {}", email);
            throw new IllegalStateException(
                    "Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng yêu cầu lại từ đầu."
            );
        }

        try {
            String newOtp = otpUtil.generateOtp(email);
            otpToEmailMap.values().removeIf(e -> e.equals(email));
            otpToEmailMap.put(newOtp, email);
            emailService.sendPasswordResetOtpEmail(email, newOtp);

            log.info("Password reset OTP resent successfully to: {}", email);

            return Map.of(
                    "message", "Mã OTP mới đã được gửi đến email của bạn",
                    "email", email,
                    "expiresIn", 300
            );

        } catch (RuntimeException e) {
            log.error("Failed to resend password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while resending password reset OTP to {}: {}",
                    email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi lại OTP. Vui lòng thử lại sau.");
        }
    }
    // ================= CHANGE PASSWORD =================
    public String changePassword(String email, ChangePasswordRequestDTO request) {
        log.info("Processing change password request for email: {}", email);

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.error("User not found for email: {}", email);
                        return new IllegalArgumentException("Không tìm thấy tài khoản");
                    });

            if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
                log.warn("Invalid old password for email: {}", email);
                throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
            }

            if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                log.warn("New password same as old password for email: {}", email);
                throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ");
            }

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            log.info("Password changed successfully for email: {}", email);
            return "Đổi mật khẩu thành công!";

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during password change for email {}: {}",
                    email, e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình đổi mật khẩu. Vui lòng thử lại.");
        }
    }

    // ================= HELPER =================
    private String generateResetToken() {
        return java.util.UUID.randomUUID().toString();
    }
}
