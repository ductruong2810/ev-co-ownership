package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    /**
     * GET /api/user/profile
     * Lấy profile của user đang login
     */
    @GetMapping
    public ResponseEntity<UserProfileResponseDTO> getMyProfile(Authentication authentication) {
        String email = authentication.getName(); // Email từ JWT
        UserProfileResponseDTO profile = userProfileService.getUserProfile(email);
        return ResponseEntity.ok(profile);
    }
}
