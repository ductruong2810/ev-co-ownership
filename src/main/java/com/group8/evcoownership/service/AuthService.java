package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.LoginRequestDTO;
import com.group8.evcoownership.dto.LoginResponseDTO;
import com.group8.evcoownership.dto.RegisterRequestDTO;
import com.group8.evcoownership.entity.Role;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
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

    // ================= LOGIN =================
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (user.getStatus() != UserStatus.Active) {
            throw new RuntimeException("Account is not active. Please verify your email first.");
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
//    public String verifyOtp(String email, String otp) {
//        try {
//            log.info("Starting OTP verification for email: {}", email);
//
//            // Kiểm tra thông tin đăng ký
//            RegisterRequestDTO request = pendingUsers.get(email);
//            if (request == null) {
//                log.error("No pending registration found for email: {}", email);
//                throw new RuntimeException("Không tìm thấy thông tin đăng ký. Vui lòng đăng ký lại.");
//            }
//
//            // Verify OTP
//            if (!otpUtil.verifyOtp(email, otp)) {
//                int remainingAttempts = otpUtil.getRemainingAttempts(email);
//                String message = remainingAttempts > 0
//                        ? "OTP không hợp lệ hoặc đã hết hạn. Bạn còn " + remainingAttempts + " lần thử."
//                        : "OTP không hợp lệ hoặc đã hết hạn.";
//                log.error("OTP verification failed for email: {}", email);
//                throw new RuntimeException(message);
//            }
//
//            // Tạo user
//            Role coOwnerRole = roleRepository.findByRoleName(RoleName.Co_owner)
//                    .orElseThrow(() -> new RuntimeException("Role Co_owner not found"));
//
//            User user = User.builder()
//                    .fullName(request.getFullName())
//                    .email(request.getEmail())
//                    .passwordHash(passwordEncoder.encode(request.getPassword()))
//                    .phoneNumber(request.getPhone())
//                    .role(coOwnerRole)
//                    .status(UserStatus.Active)
//                    .build();
//
//            userRepository.save(user);
//            pendingUsers.remove(email);
//
//            log.info("User registered successfully: {}", email);
//            return "Đăng ký tài khoản thành công!";
//
//        } catch (Exception e) {
//            log.error("Error during OTP verification for email {}: {}", email, e.getMessage());
//            throw e;
//        }
//    }

    /**
     * Verify OTP - Bước 2 của quá trình đăng ký
     * Xác thực OTP và tạo tài khoản user
     */
    public String verifyOtp(String email, String otp) {
        log.info("Starting OTP verification for email: {}", email);

        // Validate input
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP không được để trống");
        }

        try {
            // Kiểm tra thông tin đăng ký pending
            RegisterRequestDTO request = pendingUsers.get(email);
            if (request == null) {
                log.error("No pending registration found for email: {}", email);
                throw new IllegalStateException(
                        "Không tìm thấy thông tin đăng ký. Vui lòng yêu cầu OTP mới."
                );
            }

            // Verify OTP
            boolean isOtpValid = otpUtil.verifyOtp(email, otp);
            if (!isOtpValid) {
                int remainingAttempts = otpUtil.getRemainingAttempts(email);

                if (remainingAttempts == 0) {
                    // Xóa pending user nếu hết lượt thử
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

            // OTP hợp lệ - Tạo tài khoản user
            User user = createUser(request);

            // Xóa thông tin pending
            pendingUsers.remove(email);

            log.info("User registered successfully: {}", email);
            return "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay bây giờ.";

        } catch (IllegalArgumentException | IllegalStateException e) {
            // Re-throw business logic exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during OTP verification for email {}: {}",
                    email, e.getMessage(), e);
            throw new RuntimeException("Đã xảy ra lỗi trong quá trình xác thực. Vui lòng thử lại.");
        }
    }

    /**
     * Tạo user mới từ thông tin đăng ký
     */
    private User createUser(RegisterRequestDTO request) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Lấy role Co_owner
        Role coOwnerRole = roleRepository.findByRoleName(RoleName.Co_owner)
                .orElseThrow(() -> {
                    log.error("Role Co_owner not found in database");
                    return new IllegalStateException("Lỗi cấu hình hệ thống. Vui lòng liên hệ quản trị viên.");
                });

        // Tạo user entity
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhone())
                .role(coOwnerRole)
                .status(UserStatus.Active)
                .build();

        // Lưu vào database
        return userRepository.save(user);
    }

    /**
     * Resend OTP - Gửi lại OTP nếu user chưa nhận được
     */
    public String resendOtp(String email) {
        log.info("Resending OTP for email: {}", email);

        // Kiểm tra có pending registration không
        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.warn("Resend OTP attempt for non-pending email: {}", email);
            throw new IllegalStateException(
                    "Không tìm thấy thông tin đăng ký. Vui lòng đăng ký lại từ đầu."
            );
        }

        try {
            // Generate OTP mới
            String newOtp = otpUtil.generateOtp(email);

            // Gửi email
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

    /**
     * Hủy đăng ký pending (optional - để cleanup)
     */
    public void cancelPendingRegistration(String email) {
        if (pendingUsers.remove(email) != null) {
            log.info("Cancelled pending registration for email: {}", email);
        }
    }

}
