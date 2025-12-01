package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.CreateStaffRequestDTO;
import com.group8.evcoownership.dto.CreateTechnicianRequestDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Quản lý tài khoản Staff và Technician từ Admin console")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/staff")
    @Operation(summary = "Tạo tài khoản Staff", description = "Admin tạo tài khoản nhân viên quản trị hệ thống")
    public ResponseEntity<Map<String, Object>> createStaff(@Valid @RequestBody CreateStaffRequestDTO request) {
        User user = adminUserService.createStaff(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Staff account created successfully",
                "userId", user.getUserId(),
                "email", user.getEmail()
        ));
    }

    @PostMapping("/technician")
    @Operation(summary = "Tạo tài khoản Technician", description = "Admin tạo tài khoản kỹ thuật viên bảo trì")
    public ResponseEntity<Map<String, Object>> createTechnician(@Valid @RequestBody CreateTechnicianRequestDTO request) {
        User user = adminUserService.createTechnician(request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Technician account created successfully",
                "userId", user.getUserId(),
                "email", user.getEmail()
        ));
    }

    @GetMapping("/staff")
    @Operation(summary = "Danh sách Staff", description = "Lấy danh sách tất cả tài khoản Staff trong hệ thống")
    public ResponseEntity<List<User>> getAllStaff() {
        return ResponseEntity.ok(adminUserService.getAllStaff());
    }

    @GetMapping("/technician")
    @Operation(summary = "Danh sách Technician", description = "Lấy danh sách tất cả tài khoản Technician trong hệ thống")
    public ResponseEntity<List<User>> getAllTechnicians() {
        return ResponseEntity.ok(adminUserService.getAllTechnicians());
    }
}


