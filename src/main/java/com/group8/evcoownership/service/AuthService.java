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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

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
    public String verifyOtp(String email, String otp) {
        try {

            if (!otpUtil.verifyOtp(email, otp)) {

                throw new RuntimeException("OTP không hợp lệ hoặc đã hết hạn");
            }

            RegisterRequestDTO request = pendingUsers.get(email);
            if (request == null) {
                throw new RuntimeException("Không tìm thấy thông tin đăng ký");
            }

            Role coOwnerRole = roleRepository.findByRoleName(RoleName.Co_owner)
                    .orElseThrow(() -> new RuntimeException("Role Co_owner not found"));

            User user = User.builder()
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .phoneNumber(request.getPhone())
                    .role(coOwnerRole)
                    .status(UserStatus.Active)
                    .build();

            userRepository.save(user);
            pendingUsers.remove(email);
            return "Đăng ký tài khoản thành công!";
        } catch (Exception e) {
            e.printStackTrace();
            throw e; //
        }
    }

}