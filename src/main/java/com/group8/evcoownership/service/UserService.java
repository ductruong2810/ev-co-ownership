package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.UserUpdateRequestDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User createUser(User user) {
        return userRepository.save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User banUser(Long userId) {
        return updateStatus(userId, UserStatus.BANNED);
    }

    @Transactional
    public User updateStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserProfile(Long userId, UserUpdateRequestDTO updateRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (updateRequest.getFullName() != null && !updateRequest.getFullName().isBlank()) {
            user.setFullName(updateRequest.getFullName());
        }

        if (updateRequest.getPhoneNumber() != null && !updateRequest.getPhoneNumber().isBlank()) {
            if (userRepository.findByPhoneNumber(updateRequest.getPhoneNumber()).isPresent()) {
                throw new IllegalArgumentException("Phone number already exists");
            }
            user.setPhoneNumber(updateRequest.getPhoneNumber());
        }

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserName(Long userId, String fullName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setFullName(fullName);
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserPhoneNumber(Long userId, String phoneNumber) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (userRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists");
        }

        user.setPhoneNumber(phoneNumber);
        return userRepository.save(user);
    }

}

