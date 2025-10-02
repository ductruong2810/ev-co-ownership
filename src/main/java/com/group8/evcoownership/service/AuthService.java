package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.LoginRequestDTO;
import com.group8.evcoownership.dto.LoginResponseDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user);
        return new LoginResponseDTO(token, user.getEmail(), user.getRole().name());
    }
}
