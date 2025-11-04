package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
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
        return userService.createUser(user); // 404 , 400 , 200 , 201 , 500
    }


}

