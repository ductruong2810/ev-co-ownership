package com.group8.evcoownership.controller;
import com.group8.evcoownership.dto.ExpenseResponseDTO;
import com.group8.evcoownership.dto.ExpenseCreateRequestDTO;
import com.group8.evcoownership.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Quản lý chi phí (Incident & Maintenance)")
public class ExpenseController {

    private final ExpenseService expenseService;

    // =================== CREATE ===================
    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Tạo expense mới (staff/admin)", description = "Dành cho staff/admin mở chi phí thủ công hoặc tự động sau maintenance/incident.")
    public ResponseEntity<ExpenseResponseDTO> create(
            @RequestBody ExpenseCreateRequestDTO req,
            Authentication auth
    ) {
        return ResponseEntity.ok(expenseService.create(req, auth.getName()));
    }

    // =================== APPROVE ===================
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Duyệt expense (approve)", description = "Staff hoặc Admin xác nhận hoàn tất chi phí (trừ quỹ nhóm).")
    public ResponseEntity<ExpenseResponseDTO> approve(
            @PathVariable Long id,
            Authentication auth
    ) {
        return ResponseEntity.ok(expenseService.approve(id, auth.getName()));
    }

    // =================== GET ONE ===================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Xem chi tiết expense", description = "Staff hoặc Admin có thể xem thông tin chi tiết expense.")
    public ResponseEntity<ExpenseResponseDTO> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getOne(id));
    }

    // =================== LIST BY GROUP ===================
    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Danh sách expense theo group", description = "Lấy tất cả expense thuộc về group cụ thể.")
    public ResponseEntity<List<ExpenseResponseDTO>> listByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(expenseService.getByGroup(groupId));
    }
}
