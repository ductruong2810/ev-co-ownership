package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.UserStatus;
import com.group8.evcoownership.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Quản lý người dùng")
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả người dùng", description = "Trả về danh sách tất cả người dùng trong hệ thống")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
    @Operation(summary = "Tạo người dùng mới", description = "Tạo một người dùng mới trong hệ thống")
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    @PatchMapping("/{userId}/ban")
    @Operation(summary = "Khóa tài khoản người dùng", description = "Cập nhật trạng thái tài khoản sang BANNED để vô hiệu hóa đăng nhập")
    @PreAuthorize("hasRole('ADMIN')")
    public User banUser(@PathVariable Long userId) {
        return userService.banUser(userId);
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Cập nhật trạng thái tài khoản", description = "Cho phép quản trị viên cập nhật trạng thái người dùng bất kỳ")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUserStatus(@PathVariable Long userId, @RequestParam UserStatus status) {
        return userService.updateStatus(userId, status);
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật thông tin profile", description = "Cập nhật fullName và phoneNumber qua body JSON, chứa userId")
    @PreAuthorize("hasRole('CO_OWNER')")
    public ResponseEntity<ApiResponseDTO<UserUpdateResponseDTO>> updateUserProfile(
            @Valid @RequestBody UserUpdateRequestDTO req) {

        // Nếu principal không có field id, bạn có thể tự đối chiếu trong service hoặc kiểm tra thủ công tại đây.
        User updatedUser = userService.updateUserProfile(req.getUserId(), req);

        UserUpdateResponseDTO response = new UserUpdateResponseDTO(
                updatedUser.getUserId(),
                updatedUser.getFullName(),
                updatedUser.getEmail(),
                updatedUser.getPhoneNumber()
        );

        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Profile updated successfully", response));
    }


    @PatchMapping("/profile/name")
    @Operation(summary = "Cập nhật tên qua body JSON")
    @PreAuthorize("hasRole('CO_OWNER')")
    public ResponseEntity<ApiResponseDTO<String>> updateUserName(
            @Valid @RequestBody UpdateNameRequestDTO req) {
        User updatedUser = userService.updateUserName(req.getUserId(), req.getFullName());
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Name updated successfully", updatedUser.getFullName()));
    }

    @PatchMapping("/profile/phone")
    @Operation(summary = "Cập nhật số điện thoại qua body JSON")
    @PreAuthorize("hasRole('CO_OWNER')")
    public ResponseEntity<ApiResponseDTO<String>> updateUserPhoneNumber(
            @Valid @RequestBody UpdatePhoneRequestDTO req) {
        User updatedUser = userService.updateUserPhoneNumber(req.getUserId(), req.getPhoneNumber());
        return ResponseEntity.ok(new ApiResponseDTO<>(true, "Phone number updated successfully", updatedUser.getPhoneNumber()));
    }


}

