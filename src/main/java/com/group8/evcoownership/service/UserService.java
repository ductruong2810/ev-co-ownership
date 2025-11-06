package com.group8.evcoownership.service;

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
}

