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
    private final Map<String, RegisterRequestDTO> pendingUsers = new ConcurrentHashMap<>();
    private final Map<String, String> pendingPasswordResets = new ConcurrentHashMap<>(); // email → email
    private final Map<String, String> otpToEmailMap = new ConcurrentHashMap<>(); // otp → email (MỚI THÊM)
    private final Map<String, String> resetTokens = new ConcurrentHashMap<>(); // resetToken → email

    // ================= LOGIN =================
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() != UserStatus.Active) {
            throw new IllegalStateException("Tài khoản chưa được kích hoạt. Vui lòng xác thực email.");
        }

        return LoginResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(jwtUtil.generateRefreshToken(user))
                .build();
    }

    // ================= REQUEST OTP =================
    public String requestOtp(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        String otp = otpUtil.generateOtp(request.getEmail());
        pendingUsers.put(request.getEmail(), request);
        emailService.sendOtpEmail(request.getEmail(), otp);

        return "OTP đã được gửi đến email: " + request.getEmail();
    }

    // ================= VERIFY OTP =================
    public String verifyOtp(String email, String otp) {
        log.info("Starting OTP verification for email: {}", email);

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP không được để trống");
        }

        try {
            RegisterRequestDTO request = pendingUsers.get(email);
            if (request == null) {
                log.error("No pending registration found for email: {}", email);
                throw new IllegalStateException(
                        "Không tìm thấy thông tin đăng ký. Vui lòng yêu cầu OTP mới."
                );
            }

            boolean isOtpValid = otpUtil.verifyOtp(email, otp);
            if (!isOtpValid) {
                int remainingAttempts = otpUtil.getRemainingAttempts(email);

                if (remainingAttempts == 0) {
                    pendingUsers.remove(email);
                    log.error("OTP verification failed - no attempts remaining for email: {}", email);
                    throw new IllegalStateException(
                            "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu OTP mới."
                    );
                }

                log.error("Invalid OTP for email: {}. Remaining attempts: {}", email, remainingAttempts);
                throw new IllegalArgumentException(
                        "OTP không hợp lệ hoặc đã hết hạn. Bạn còn " + remainingAttempts + " lần thử."
                );
            }

            createUser(request);
            pendingUsers.remove(email);

            log.info("User registered successfully: {}", email);
            return "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.";

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OTP verification for email {}: {}",
                    email, e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại.");
        }
    }

    private void createUser(RegisterRequestDTO request) {
        log.info("Creating new user with email: {}", request.getEmail());

        Role coOwnerRole = roleRepository.findByRoleName(RoleName.Co_owner)
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
                .status(UserStatus.Active)
                .build();

        userRepository.save(user);
    }

    // ================= RESEND OTP =================
    public String resendOtp(String email) {
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
            emailService.sendOtpEmail(email, newOtp);

            log.info("OTP resent successfully to email: {}", email);
            return "OTP mới đã được gửi đến email: " + email;

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

    // ================= BƯỚC 1: FORGOT PASSWORD - GỬI OTP =================
    /**
     * Bước 1: Nhập email, gửi OTP
     */
    public String forgotPassword(String email) {
        log.info("Processing forgot password request for email: {}", email);

        // Kiểm tra email có tồn tại không
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Forgot password attempt for non-existent email: {}", email);
                    return new IllegalArgumentException("Email không tồn tại trong hệ thống");
                });

        // Kiểm tra account có active không
        if (user.getStatus() != UserStatus.Active) {
            log.warn("Forgot password attempt for inactive account: {}", email);
            throw new IllegalStateException("Tài khoản chưa được kích hoạt. Vui lòng liên hệ quản trị viên.");
        }

        try {
            // Generate và gửi OTP
            String otp = otpUtil.generateOtp(email);

            // Lưu email vào pending resets
            pendingPasswordResets.put(email, email);

            // Lưu mapping OTP -> Email
            otpToEmailMap.put(otp, email);

            // Gửi email OTP
            emailService.sendPasswordResetOtpEmail(email, otp);

            log.info("Password reset OTP sent successfully to: {}", email);
            return "Mã OTP đã được gửi đến email: " + email + ". Mã có hiệu lực trong 5 phút.";

        } catch (RuntimeException e) {
            log.error("Failed to send password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during forgot password for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi OTP. Vui lòng thử lại sau.");
        }
    }

    // ================= BƯỚC 2: VERIFY OTP - CHỈ CẦN OTP =================
    /**
     * Bước 2: Verify OTP (không cần email), trả về resetToken
     */
    public VerifyResetOtpResponseDTO verifyResetOtp(String otp) {
        log.info("Verifying reset OTP");

        // Validate input
        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP không được để trống");
        }

        try {
            // Tìm email từ OTP
            String email = otpToEmailMap.get(otp);
            if (email == null) {
                log.error("OTP not found or expired: {}", otp);
                throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
            }

            // Kiểm tra email có trong pending resets không
            if (!pendingPasswordResets.containsKey(email)) {
                log.error("No pending password reset found for email: {}", email);
                otpToEmailMap.remove(otp);
                throw new IllegalStateException(
                        "Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng yêu cầu OTP mới."
                );
            }

            // Verify OTP
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

            // OTP hợp lệ - Generate reset token
            String resetToken = generateResetToken();
            resetTokens.put(resetToken, email);

            // Cleanup
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

    // ================= BƯỚC 3: ĐỔI MẬT KHẨU VỚI RESET TOKEN =================
    /**
     * Bước 3: Dùng resetToken để đổi mật khẩu mới
     */
    public String resetPasswordWithToken(ResetPasswordRequestDTO request) {
        String resetToken = request.getResetToken();

        log.info("Processing password reset with token");

        // Validate input
        if (resetToken == null || resetToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Reset token không được để trống");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu và xác nhận mật khẩu không khớp");
        }

        try {
            // Kiểm tra reset token có hợp lệ không
            String email = resetTokens.get(resetToken);
            if (email == null) {
                log.error("Invalid or expired reset token");
                throw new IllegalArgumentException(
                        "Reset token không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu OTP mới."
                );
            }

            // Lấy user và cập nhật mật khẩu
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            // Xóa reset token sau khi dùng xong
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
    // ================= RESEND OTP FOR PASSWORD RESET =================
    /**
     * Gửi lại OTP cho reset password
     */
    public String resendPasswordResetOtp(String email) {
        log.info("Resending password reset OTP for email: {}", email);

        // Kiểm tra có pending reset không
        if (!pendingPasswordResets.containsKey(email)) {
            log.warn("Resend OTP attempt for non-pending reset: {}", email);
            throw new IllegalStateException(
                    "Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng yêu cầu lại từ đầu."
            );
        }

        try {
            // Generate OTP mới
            String newOtp = otpUtil.generateOtp(email);

            // Xóa OTP cũ của email này (nếu có)
            otpToEmailMap.values().removeIf(e -> e.equals(email));
            // Thêm OTP mới
            otpToEmailMap.put(newOtp, email);

            // Gửi email
            emailService.sendPasswordResetOtpEmail(email, newOtp);

            log.info("Password reset OTP resent successfully to: {}", email);
            return "OTP mới đã được gửi đến email: " + email;

        } catch (RuntimeException e) {
            log.error("Failed to resend password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while resending password reset OTP to {}: {}",
                    email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi lại OTP. Vui lòng thử lại sau.");
        }
    }

    // ================= HELPER METHOD =================
    /**
     * Generate random reset token
     */
    private String generateResetToken() {
        return java.util.UUID.randomUUID().toString();
    }

    // ================= CHANGE PASSWORD (ĐÃ LOGIN) =================
    /**
     * Đổi mật khẩu khi user đã đăng nhập
     */
    public String changePassword(String email, ChangePasswordRequestDTO request) {
        log.info("Processing change password request for email: {}", email);

        // Validate input
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        try {
            // Lấy user từ database
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.error("User not found for email: {}", email);
                        return new IllegalArgumentException("Không tìm thấy tài khoản");
                    });

            // Kiểm tra mật khẩu cũ có đúng không
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
                log.warn("Invalid old password for email: {}", email);
                throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
            }

            // Kiểm tra mật khẩu mới không được trùng với mật khẩu cũ
            if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                log.warn("New password same as old password for email: {}", email);
                throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ");
            }

            // Cập nhật mật khẩu mới
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
}
