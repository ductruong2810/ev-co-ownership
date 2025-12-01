package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.CreateStaffRequestDTO;
import com.group8.evcoownership.dto.CreateTechnicianRequestDTO;
import com.group8.evcoownership.entity.Role;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.repository.RoleRepository;
import com.group8.evcoownership.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createStaff(CreateStaffRequestDTO request) {
        log.info("Admin creating STAFF user with email: {}", request.getEmail());

        validateEmailAndPhone(request.getEmail(), request.getPhoneNumber());
        Role staffRole = roleRepository.findByRoleName(RoleName.STAFF)
                .orElseThrow(() -> new IllegalStateException("Role STAFF not found in database"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User createTechnician(CreateTechnicianRequestDTO request) {
        log.info("Admin creating TECHNICIAN user with email: {}", request.getEmail());

        validateEmailAndPhone(request.getEmail(), request.getPhoneNumber());
        Role technicianRole = roleRepository.findByRoleName(RoleName.TECHNICIAN)
                .orElseThrow(() -> new IllegalStateException("Role TECHNICIAN not found in database"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(technicianRole)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> getAllStaff() {
        return userRepository.findByRoleRoleName(RoleName.STAFF);
    }

    @Transactional(readOnly = true)
    public List<User> getAllTechnicians() {
        return userRepository.findByRoleRoleName(RoleName.TECHNICIAN);
    }

    private void validateEmailAndPhone(String email, String phoneNumber) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (phoneNumber != null && !phoneNumber.isBlank() && userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists");
        }
    }
}


