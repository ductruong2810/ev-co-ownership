package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.LoginRequestDTO;
import com.group8.evcoownership.dto.LoginResponseDTO;
import com.group8.evcoownership.dto.RegisterRequestDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.Role;
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
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpUtil otpUtil;

    @Autowired
    private EmailService emailService;

    private final Map<String, RegisterRequestDTO> pendingUsers = new ConcurrentHashMap<>();

    // ========== LOGIN ==========
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        return LoginResponseDTO.builder()
                .accessToken(jwtUtil.generateToken(user))
                .refreshToken(jwtUtil.generateRefreshToken(user))
                .build();
    }

    // ========== REGISTER REQUEST OTP ==========
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

        return "OTP đã gửi đến email " + request.getEmail();
    }

    // ========== VERIFY OTP & REGISTER ==========
    public String verifyOtp(String email, String otp) {
        if (!otpUtil.verifyOtp(email, otp)) {
            throw new RuntimeException("OTP không hợp lệ hoặc đã hết hạn");
        }

        RegisterRequestDTO request = pendingUsers.get(email);
        if (request == null) {
            throw new RuntimeException("Không tìm thấy thông tin đăng ký");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.Co_owner)
                .build();


        userRepository.save(user);
        pendingUsers.remove(email);

        return "Đăng ký tài khoản thành công";

    }

}
