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

    @Autowired
    private UserProfileService userProfileService;

    private final Map<String, String> registerOtpToEmailMap = new ConcurrentHashMap<>();
    private final Map<String, RegisterRequestDTO> pendingUsers = new ConcurrentHashMap<>();
    private final Map<String, String> pendingPasswordResets = new ConcurrentHashMap<>();
    private final Map<String, String> otpToEmailMap = new ConcurrentHashMap<>();
    private final Map<String, String> resetTokens = new ConcurrentHashMap<>();

    // ================= LOGIN =================
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Email or password is incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Email or password is incorrect");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not activated. Please verify your email");
        }

        boolean rememberMe = request.isRememberMe();

        return LoginResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(jwtUtil.generateRefreshToken(user, rememberMe))
                .role(user.getRole().getRoleName().name())
                .build();
    }

    // ================= REFRESH TOKEN =================
    public LoginResponseDTO refreshToken(String refreshToken) {
        log.info("Processing token refresh");

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh token cannot be empty");
        }

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh token is invalid or has expired");
        }

        String email = jwtUtil.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not activated");
        }

        String newAccessToken = jwtUtil.generateToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user);

        log.info("Tokens refreshed successfully for user: {}", email);

        return LoginResponseDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .role(user.getRole().getRoleName().name())
                .build();
    }

    // ================= REQUEST OTP (REGISTRATION) =================
    public OtpResponseDTO requestOtp(RegisterRequestDTO request) {
        String email = request.getEmail();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

        String otp = null;

        try {
            otp = otpUtil.generateOtp(email);
            emailService.sendOtpEmail(email, request.getFullName(), otp);
            pendingUsers.put(email, request);
            registerOtpToEmailMap.put(otp, email);

            log.info("Registration OTP sent to email: {}", email);

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("OTP has been sent to your email")
                    .type(OtpType.REGISTRATION)
                    .expiresIn(300)
                    .build();

        } catch (Exception e) {
            log.error("Failed to send registration OTP to {}: {}", email, e.getMessage());

            if (otp != null) {
                otpUtil.invalidateOtp(email);
            }
            pendingUsers.remove(email);
            if (otp != null) {
                registerOtpToEmailMap.remove(otp);
            }

            throw new RuntimeException("Unable to send verification email. Please try again later");
        }
    }

    // ================= VERIFY OTP =================
    public VerifyOtpResponseDTO verifyOtp(String otp, OtpType type) {
        log.info("Verifying OTP with type: {}", type);

        if (otp == null || otp.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP cannot be empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("OTP type is required");
        }

        try {
            if (type == OtpType.REGISTRATION) {
                log.info("Verifying REGISTRATION OTP");
                return verifyRegistrationOtp(otp);
            } else if (type == OtpType.PASSWORD_RESET) {
                log.info("Verifying PASSWORD_RESET OTP");
                return verifyPasswordResetOtp(otp);
            } else {
                throw new IllegalArgumentException("Invalid OTP type");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("An error occurred during verification. Please try again");
        }
    }

    // ================= VERIFY REGISTRATION OTP (PRIVATE) =================
    private VerifyOtpResponseDTO verifyRegistrationOtp(String otp) {
        log.info("Verifying registration OTP");

        String email = registerOtpToEmailMap.get(otp);
        if (email == null) {
            log.error("Registration OTP not found or expired: {}", otp);
            throw new IllegalArgumentException("OTP is invalid or has expired");
        }

        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.error("No pending registration found for email: {}", email);
            registerOtpToEmailMap.remove(otp);
            throw new IllegalStateException("Registration information not found. Please request a new OTP");
        }

        boolean isOtpValid = otpUtil.verifyOtp(email, otp);
        if (!isOtpValid) {
            int remainingAttempts = otpUtil.getRemainingAttempts(email);
            if (remainingAttempts == 0) {
                pendingUsers.remove(email);
                registerOtpToEmailMap.remove(otp);
                log.error("Registration OTP verification failed - no attempts remaining");
                throw new IllegalStateException("You have entered an incorrect OTP too many times. Please request a new OTP");
            }
            log.error("Invalid registration OTP. Remaining attempts: {}", remainingAttempts);
            throw new IllegalArgumentException("Invalid OTP. You have " + remainingAttempts + " attempts remaining");
        }

        User user = createUser(request);
        pendingUsers.remove(email);
        registerOtpToEmailMap.remove(otp);

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user, false);

        UserProfileResponseDTO userProfile = userProfileService.getUserProfile(user.getEmail());

        log.info("User registered successfully: {}", email);

        return VerifyOtpResponseDTO.builder()
                .email(email)
                .message("Account registration successful! You have been automatically logged in")
                .type(OtpType.REGISTRATION)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userProfile)
                .build();
    }

    // ================= VERIFY PASSWORD RESET OTP (PRIVATE) =================
    private VerifyOtpResponseDTO verifyPasswordResetOtp(String otp) {
        log.info("Verifying password reset OTP");

        String email = otpToEmailMap.get(otp);
        if (email == null) {
            log.error("Password reset OTP not found or expired: {}", otp);
            throw new IllegalArgumentException("OTP is invalid or has expired");
        }

        if (!pendingPasswordResets.containsKey(email)) {
            log.error("No pending password reset found for email: {}", email);
            otpToEmailMap.remove(otp);
            throw new IllegalStateException("Password reset request not found. Please request a new OTP");
        }

        boolean isOtpValid = otpUtil.verifyOtp(email, otp);
        if (!isOtpValid) {
            int remainingAttempts = otpUtil.getRemainingAttempts(email);
            if (remainingAttempts == 0) {
                pendingPasswordResets.remove(email);
                otpToEmailMap.remove(otp);
                log.error("Password reset OTP verification failed - no attempts remaining");
                throw new IllegalStateException("You have entered an incorrect OTP too many times. Please request a new OTP");
            }
            log.error("Invalid password reset OTP. Remaining attempts: {}", remainingAttempts);
            throw new IllegalArgumentException("Invalid OTP. You have " + remainingAttempts + " attempts remaining");
        }

        String resetToken = generateResetToken();
        resetTokens.put(resetToken, email);
        pendingPasswordResets.remove(email);
        otpToEmailMap.remove(otp);

        log.info("Reset OTP verified successfully: {}", email);

        return VerifyOtpResponseDTO.builder()
                .email(email)
                .message("OTP verification successful. Please set your new password")
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
                    return new IllegalStateException("System configuration error. Please contact the administrator");
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

    // ================= RESEND OTP =================
    public OtpResponseDTO resendOtp(String email, OtpType type) {
        log.info("Resending {} OTP for email: {}", type, email);

        if (type == OtpType.REGISTRATION) {
            return resendRegistrationOtp(email);
        } else if (type == OtpType.PASSWORD_RESET) {
            return resendPasswordResetOtp(email);
        } else {
            throw new IllegalArgumentException("Invalid OTP type");
        }
    }

    // ================= RESEND REGISTRATION OTP (PRIVATE) =================
    private OtpResponseDTO resendRegistrationOtp(String email) {
        log.info("Resending registration OTP for email: {}", email);

        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            log.warn("Resend OTP attempt for non-pending email: {}", email);
            throw new IllegalStateException("Registration information not found. Please register again from the beginning");
        }

        String newOtp = null;

        try {
            newOtp = otpUtil.generateOtp(email);
            emailService.sendOtpEmail(email, request.getFullName(), newOtp);
            registerOtpToEmailMap.put(newOtp, email);

            log.info("Registration OTP resent successfully to email: {}", email);

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("A new OTP has been sent to your email")
                    .type(OtpType.REGISTRATION)
                    .expiresIn(300)
                    .build();

        } catch (Exception e) {
            log.error("Failed to resend registration OTP to email {}: {}", email, e.getMessage());
            if (newOtp != null) {
                otpUtil.invalidateOtp(email);
            }

            throw new RuntimeException("Unable to resend OTP. Please try again later");
        }
    }

    // ================= RESEND PASSWORD RESET OTP (PRIVATE) =================
    private OtpResponseDTO resendPasswordResetOtp(String email) {
        log.info("Resending password reset OTP for email: {}", email);

        if (!pendingPasswordResets.containsKey(email)) {
            log.warn("Resend password reset OTP attempt for non-pending reset: {}", email);
            throw new IllegalStateException("Password reset request not found. Please request again from the beginning");
        }

        try {
            String newOtp = otpUtil.generateOtp(email);
            otpToEmailMap.values().removeIf(e -> e.equals(email));
            otpToEmailMap.put(newOtp, email);
            emailService.sendPasswordResetOtpEmail(email, newOtp);

            log.info("Password reset OTP resent successfully to: {}", email);

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("A new OTP has been sent to your email")
                    .type(OtpType.PASSWORD_RESET)
                    .expiresIn(300)
                    .build();

        } catch (RuntimeException e) {
            log.error("Failed to resend password reset OTP to {}: {}", email, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while resending password reset OTP to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Unable to resend OTP. Please try again later");
        }
    }

    // ================= FORGOT PASSWORD =================
    public OtpResponseDTO forgotPassword(String email) {
        log.info("Processing forgot password request for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Forgot password attempt for non-existent email: {}", email);
                    return new IllegalArgumentException("Email does not exist in the system");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Forgot password attempt for inactive account: {}", email);
            throw new IllegalStateException("Account is not activated. Please contact the administrator");
        }

        String otp = null;

        try {
            otp = otpUtil.generateOtp(email);
            emailService.sendPasswordResetOtpEmail(email, otp);
            pendingPasswordResets.put(email, email);
            otpToEmailMap.put(otp, email);

            log.info("Password reset OTP sent successfully to: {}", email);

            return OtpResponseDTO.builder()
                    .email(email)
                    .message("OTP has been sent to your email")
                    .type(OtpType.PASSWORD_RESET)
                    .expiresIn(300)
                    .build();

        } catch (Exception e) {
            log.error("Failed to send password reset OTP to {}: {}", email, e.getMessage());

            if (otp != null) {
                otpUtil.invalidateOtp(email);
            }
            pendingPasswordResets.remove(email);
            if (otp != null) {
                otpToEmailMap.remove(otp);
            }

            throw new RuntimeException("Unable to send OTP. Please try again later");
        }
    }

    // ================= RESET PASSWORD WITH TOKEN =================
    public ResetPasswordResponseDTO resetPasswordWithToken(ResetPasswordRequestDTO request) {
        String resetToken = request.getResetToken();
        log.info("Processing password reset with token");

        if (resetToken == null || resetToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Reset token cannot be empty");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }

        try {
            String email = resetTokens.get(resetToken);
            if (email == null) {
                log.error("Invalid or expired reset token");
                throw new IllegalArgumentException("Reset token is invalid or has expired. Please request a new OTP");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Account not found"));

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            resetTokens.remove(resetToken);

            String accessToken = jwtUtil.generateToken(user);

            log.info("Password reset successfully for email: {} - Auto login enabled", email);

            return ResetPasswordResponseDTO.builder()
                    .message("Password reset successful! You have been automatically logged in")
                    .accessToken(accessToken)
                    .build();

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during password reset: {}", e.getMessage(), e);
            throw new RuntimeException("An error occurred during password reset. Please try again");
        }
    }

    // ================= CHANGE PASSWORD =================
    public String changePassword(String email, ChangePasswordRequestDTO request) {
        log.info("Processing change password request for email: {}", email);

        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.error("User not found for email: {}", email);
                        return new IllegalArgumentException("Account not found");
                    });

            if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
                log.warn("Invalid old password for email: {}", email);
                throw new IllegalArgumentException("Old password is incorrect");
            }

            if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                log.warn("New password same as old password for email: {}", email);
                throw new IllegalArgumentException("New password must be different from old password");
            }

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            log.info("Password changed successfully for email: {}", email);
            return "Password changed successfully!";

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during password change for email {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("An error occurred during password change. Please try again");
        }
    }

    // ================= HELPER =================
    private String generateResetToken() {
        return java.util.UUID.randomUUID().toString();
    }
}
