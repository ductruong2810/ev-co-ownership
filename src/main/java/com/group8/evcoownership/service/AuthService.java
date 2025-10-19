package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.Role;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.OtpType;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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

    // ================= REQUEST OTP (REGISTRATION) =================
    public OtpResponseDTO requestOtp(RegisterRequestDTO request) {
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

        log.info("Registration OTP sent to email: {}", request.getEmail());

        return OtpResponseDTO.builder()
                .email(request.getEmail())
                .message("Mã OTP đã được gửi đến email của bạn")
                .type(OtpType.REGISTRATION)
                .expiresIn(300)
                .build();
    }

    // ================= VERIFY OTP - AUTO DETECT (TRẢ VỀ TYPE) =================
    public VerifyOtpResponseDTO verifyOtp(String otp) {
        log.info("Auto-detecting OTP type");

        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP không được để trống");
        }

        try {
            // Check registration OTP
            if (registerOtpToEmailMap.containsKey(otp)) {
                log.info("Detected REGISTRATION OTP");
                return verifyRegistrationOtp(otp);
            }
            // Check password reset OTP
            else if (otpToEmailMap.containsKey(otp)) {
                log.info("Detected PASSWORD_RESET OTP");
                return verifyPasswordResetOtp(otp);
            }
            // Not found
            else {
                log.error("OTP not found: {}", otp);
                throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại.");
        }
    }

    // ================= VERIFY REGISTRATION OTP (PRIVATE) =================
    @Autowired
    private UserProfileService userProfileService;  // ← THÊM

    // ================= VERIFY REGISTRATION OTP (PRIVATE) =================
    private VerifyOtpResponseDTO verifyRegistrationOtp(String otp) {
        log.info("Verifying registration OTP");

        String email = registerOtpToEmailMap.get(otp);
        if (email == null) {
            log.error("Registration OTP not found or expired: {}", otp);
            throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
        }

        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.error("No pending registration found for email: {}", email);
            registerOtpToEmailMap.remove(otp);
            throw new IllegalStateException("Không tìm thấy thông tin đăng ký. Vui lòng yêu cầu OTP mới.");
        }

        boolean isOtpValid = otpUtil.verifyOtp(email, otp);
        if (!isOtpValid) {
            int remainingAttempts = otpUtil.getRemainingAttempts(email);
            if (remainingAttempts == 0) {
                pendingUsers.remove(email);
                registerOtpToEmailMap.remove(otp);
                log.error("Registration OTP verification failed - no attempts remaining");
                throw new IllegalStateException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu OTP mới.");
            }
            log.error("Invalid registration OTP. Remaining attempts: {}", remainingAttempts);
            throw new IllegalArgumentException("OTP không hợp lệ. Bạn còn " + remainingAttempts + " lần thử.");
        }

        User user = createUser(request);
        pendingUsers.remove(email);
        registerOtpToEmailMap.remove(otp);

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user, false);

        // ← THAY ĐỔI: Dùng UserProfileService để build profile đầy đủ
        UserProfileResponseDTO userProfile = userProfileService.getUserProfile(user.getEmail());

        log.info("User registered successfully: {}", email);

        return VerifyOtpResponseDTO.builder()
                .email(email)
                .message("Đăng ký tài khoản thành công! Bạn đã được tự động đăng nhập.")
                .type(OtpType.REGISTRATION)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userProfile)  // ← THAY ĐỔI
                .build();
    }

    // ================= VERIFY PASSWORD RESET OTP (PRIVATE) =================
    private VerifyOtpResponseDTO verifyPasswordResetOtp(String otp) {
        log.info("Verifying password reset OTP");

        String email = otpToEmailMap.get(otp);
        if (email == null) {
            log.error("Password reset OTP not found or expired: {}", otp);
            throw new IllegalArgumentException("OTP không hợp lệ hoặc đã hết hạn");
        }

        if (!pendingPasswordResets.containsKey(email)) {
            log.error("No pending password reset found for email: {}", email);
            otpToEmailMap.remove(otp);
            throw new IllegalStateException("Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng yêu cầu OTP mới.");
        }

        boolean isOtpValid = otpUtil.verifyOtp(email, otp);
        if (!isOtpValid) {
            int remainingAttempts = otpUtil.getRemainingAttempts(email);
            if (remainingAttempts == 0) {
                pendingPasswordResets.remove(email);
                otpToEmailMap.remove(otp);
                log.error("Password reset OTP verification failed - no attempts remaining");
                throw new IllegalStateException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu OTP mới.");
            }
            log.error("Invalid password reset OTP. Remaining attempts: {}", remainingAttempts);
            throw new IllegalArgumentException("OTP không hợp lệ. Bạn còn " + remainingAttempts + " lần thử.");
        }

        String resetToken = generateResetToken();
        resetTokens.put(resetToken, email);
        pendingPasswordResets.remove(email);
        otpToEmailMap.remove(otp);

        log.info("Reset OTP verified successfully: {}", email);

        return VerifyOtpResponseDTO.builder()
                .email(email)
                .message("Xác thực OTP thành công. Vui lòng đặt mật khẩu mới.")
                .type(OtpType.PASSWORD_RESET)
                .resetToken(resetToken)
                .build();
    }

    // ================= CREATE USER =================
    private User createUser(RegisterRequestDTO request) {
        log.info("Creating new user with email: {}", request.getEmail());

        Role coOwnerRole = roleRepository.findByRoleName(RoleName.CO_OWNER)
                .orElseThrow(() -> {
                    log.error("Role CO_OWNER not found in database");
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

        return userRepository.save(user);
    }

    // ================= RESEND OTP (UNIFIED - REGISTRATION & PASSWORD_RESET) =================
    public OtpResponseDTO resendOtp(String email, OtpType type) {
        log.info("Resending {} OTP for email: {}", type, email);

        if (type == OtpType.REGISTRATION) {
            return resendRegistrationOtp(email);
        } else if (type == OtpType.PASSWORD_RESET) {
            return resendPasswordResetOtp(email);
        } else {
            throw new IllegalArgumentException("OTP type không hợp lệ");
        }
    }

    // ================= RESEND REGISTRATION OTP (PRIVATE) =================
    private OtpResponseDTO resendRegistrationOtp(String email) {
        log.info("Resending registration OTP for email: {}", email);

        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.warn("Resend OTP attempt for non-pending email: {}", email);
            throw new IllegalStateException("Không tìm thấy thông tin đăng ký. Vui lòng đăng ký lại từ đầu.");
        }

        try {
            String newOtp = otpUtil.generateOtp(email);
            registerOtpToEmailMap.put(newOtp, email);
            emailService.sendOtpEmail(email, newOtp);

            log.info("Registration OTP resent successfully to email: {}", email);

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("Mã OTP mới đã được gửi đến email của bạn")
                    .type(OtpType.REGISTRATION)
                    .expiresIn(300)
                    .build();

        } catch (RuntimeException e) {
            log.error("Failed to resend registration OTP to email {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while resending registration OTP to email {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi lại OTP. Vui lòng thử lại sau.");
        }
    }

    // ================= RESEND PASSWORD RESET OTP (PRIVATE) =================
    private OtpResponseDTO resendPasswordResetOtp(String email) {
        log.info("Resending password reset OTP for email: {}", email);

        if (!pendingPasswordResets.containsKey(email)) {
            log.warn("Resend password reset OTP attempt for non-pending reset: {}", email);
            throw new IllegalStateException("Không tìm thấy yêu cầu đặt lại mật khẩu. Vui lòng yêu cầu lại từ đầu.");
        }

        try {
            String newOtp = otpUtil.generateOtp(email);
            otpToEmailMap.values().removeIf(e -> e.equals(email));
            otpToEmailMap.put(newOtp, email);
            emailService.sendPasswordResetOtpEmail(email, newOtp);

            log.info("Password reset OTP resent successfully to: {}", email);

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("Mã OTP mới đã được gửi đến email của bạn")
                    .type(OtpType.PASSWORD_RESET)
                    .expiresIn(300)
                    .build();

        } catch (RuntimeException e) {
            log.error("Failed to resend password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while resending password reset OTP to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi lại OTP. Vui lòng thử lại sau.");
        }
    }

    // ================= CANCEL PENDING REGISTRATION =================
    public void cancelPendingRegistration(String email) {
        if (pendingUsers.remove(email) != null) {
            log.info("Cancelled pending registration for email: {}", email);
        }
    }

    // ================= FORGOT PASSWORD - GỬI OTP =================
    public OtpResponseDTO forgotPassword(String email) {
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

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("Mã OTP đã được gửi đến email của bạn")
                    .type(OtpType.PASSWORD_RESET)
                    .expiresIn(300)
                    .build();

        } catch (RuntimeException e) {
            log.error("Failed to send password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during forgot password for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Không thể gửi OTP. Vui lòng thử lại sau.");
        }
    }

    // ================= RESET PASSWORD WITH TOKEN (AUTO LOGIN) =================
    public ResetPasswordResponseDTO resetPasswordWithToken(ResetPasswordRequestDTO request) {
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
                throw new IllegalArgumentException("Reset token không hợp lệ hoặc đã hết hạn. Vui lòng yêu cầu OTP mới.");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản"));

            // Update password
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            // Clean up
            resetTokens.remove(resetToken);

            // Generate token for auto login
            String accessToken = jwtUtil.generateToken(user);

            log.info("Password reset successfully for email: {} - Auto login enabled", email);

            return ResetPasswordResponseDTO.builder()
                    .message("Đặt lại mật khẩu thành công! Bạn đã được tự động đăng nhập.")
                    .accessToken(accessToken)
                    .build();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during password reset: {}", e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình đặt lại mật khẩu. Vui lòng thử lại.");
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
            log.error("Unexpected error during password change for email {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình đổi mật khẩu. Vui lòng thử lại.");
        }
    }

    // ================= HELPER =================
    private String generateResetToken() {
        return java.util.UUID.randomUUID().toString();
    }
}
