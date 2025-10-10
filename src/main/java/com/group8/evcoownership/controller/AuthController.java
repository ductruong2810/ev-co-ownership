package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.LoginRequestDTO;
import com.group8.evcoownership.dto.LoginResponseDTO;
import com.group8.evcoownership.dto.RegisterRequestDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.service.AuthService;
import com.group8.evcoownership.utils.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")

public class AuthController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthService authService;
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

}
