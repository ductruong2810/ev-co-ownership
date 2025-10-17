package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/profile")
@Slf4j
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    /**
     * GET /api/user/profile
     * Lấy profile của user đang login (cần token)
     */
    @GetMapping
    public ResponseEntity<UserProfileResponseDTO> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        UserProfileResponseDTO profile = userProfileService.getUserProfile(email);
        return ResponseEntity.ok(profile);
    }

    /**
     * GET /api/user/profile/{userId}
     * Xem profile theo userId (public - không cần token)   
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponseDTO> getUserProfileById(@PathVariable Long userId) {
        log.info("Fetching profile for userId: {}", userId);
        UserProfileResponseDTO profile = userProfileService.getUserProfileById(userId);
        return ResponseEntity.ok(profile);
    }
}
