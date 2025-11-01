package com.group8.evcoownership.controller;
import com.group8.evcoownership.dto.ExpenseResponseDTO;
import com.group8.evcoownership.dto.ExpenseCreateRequestDTO;
import com.group8.evcoownership.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
            @Valid @RequestBody ExpenseCreateRequestDTO req,
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

    // ================== GET ALL ===================
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','TECHNICIAN')")
    @Operation(summary = "Danh sách Expense (lọc linh hoạt)", description = """
        Lấy danh sách chi phí có thể lọc theo nhiều điều kiện:
        - fundId: ID của quỹ
        - sourceType: MAINTENANCE / INCIDENT
        - status: PENDING / COMPLETED
        - approvedById: ID người duyệt
        - recipientUserId: ID người nhận tiền
        Nếu không truyền tham số nào, sẽ trả về toàn bộ danh sách (có phân trang).
        """)
    public ResponseEntity<Page<ExpenseResponseDTO>> getAll(
            @RequestParam(required = false) Long fundId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long approvedById,
            @RequestParam(required = false) Long recipientUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(expenseService.getAll(fundId, sourceType, status, approvedById, recipientUserId, pageable));
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
