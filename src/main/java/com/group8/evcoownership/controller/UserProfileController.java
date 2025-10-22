package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.UserProfileResponseDTO;
import com.group8.evcoownership.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/profile")
@Slf4j
@Tag(name = "User Profile", description = "Quản lý hồ sơ người dùng")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    /**
     * GET /api/user/profile
     * Lấy profile của user đang login (cần token)
     */
    @GetMapping
    @Operation(summary = "Hồ sơ của tôi", description = "Lấy thông tin hồ sơ của người dùng hiện tại")
    public ResponseEntity<UserProfileResponseDTO> getMyProfile(@AuthenticationPrincipal String email) {
        UserProfileResponseDTO profile = userProfileService.getUserProfile(email);
        return ResponseEntity.ok(profile);
    }

    /**
     * GET /api/user/profile/{userId}
     * Xem profile theo userId (public - không cần token)
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Hồ sơ người dùng", description = "Xem hồ sơ của một người dùng cụ thể theo ID")
    public ResponseEntity<UserProfileResponseDTO> getUserProfileById(@PathVariable Long userId) {
        log.info("Fetching profile for userId: {}", userId);
        UserProfileResponseDTO profile = userProfileService.getUserProfileById(userId);
        return ResponseEntity.ok(profile);
    }
}
