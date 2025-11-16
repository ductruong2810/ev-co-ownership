package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.MaintenanceCreateRequestDTO;
import com.group8.evcoownership.dto.MaintenanceResponseDTO;
import com.group8.evcoownership.dto.MaintenanceUpdateRequestDTO;
import com.group8.evcoownership.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/maintenances")
@RequiredArgsConstructor
@Tag(name = "Maintenance", description = "Quản lý yêu cầu bảo trì xe (Technician tạo, Staff/Admin duyệt, Admin giám sát)")
@PreAuthorize("isAuthenticated()")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    // ======================================================
    // ================== TECHNICIAN / STAFF / ADMIN ========
    // ======================================================

    /**
     * Tạo yêu cầu bảo trì mới cho xe (Technician, Staff, Admin đều có thể làm)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'STAFF', 'ADMIN')")
    @Operation(
            summary = "[Technician] Tạo yêu cầu bảo trì",
            description = """
                    Người dùng kỹ thuật hoặc nhân viên/staff có thể mở yêu cầu bảo trì mới cho xe.
                    - Nhập ngày dự kiến bảo dưỡng (phải là tương lai).
                    - Gửi kèm mô tả và chi phí dự kiến.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> create(
            @Valid @RequestBody MaintenanceCreateRequestDTO req,
            Authentication auth
    ) {
        return ResponseEntity.ok(maintenanceService.create(req, auth.getName()));
    }

    // ==================== UPDATE ==========================
    // Technician cap nhat yeu cau khi van con trang thai Pending
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    @Operation(summary = "[Technician]Cập nhật yêu cầu bảo trì",
            description = "Chỉ cho phép cập nhật khi trạng thái là PENDING. Cho phép TECHNICIAN, STAFF, ADMIN.")
    public ResponseEntity<MaintenanceResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody MaintenanceUpdateRequestDTO req,
            Authentication auth
    ) {
        return ResponseEntity.ok(maintenanceService.update(id, req, auth.getName()));
    }

    // ==================== Technician get his Maintenance requests =============
    // Technician xem yeu cau bao tri cua minh
    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    @Operation(summary = "[Technician] Xem yêu cầu bảo trì của chính mình",
            description = "Dành cho technician xem lại các yêu cầu bảo trì do họ đã tạo.")
    public ResponseEntity<List<MaintenanceResponseDTO>> getMyRequests(Authentication auth) {
        return ResponseEntity.ok(maintenanceService.getMyRequests(auth.getName()));
    }


    // ======================================================
    // ===================== STAFF / ADMIN ==================
    // ======================================================

    /**
     * Staff/Admin Xem danh sách tất cả yêu cầu bảo trì
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "[Staff/Admin] Danh sách yêu cầu bảo trì", description = "Hiển thị danh sách toàn bộ các yêu cầu bảo trì hiện có trong hệ thống.")
    public ResponseEntity<List<MaintenanceResponseDTO>> getAll() {
        return ResponseEntity.ok(maintenanceService.getAll());
    }

    /**
     * Staff/Admin Duyệt yêu cầu bảo trì (PENDING → APPROVED)
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Duyệt yêu cầu bảo trì",
            description = """
                    Nhân viên hoặc quản trị viên duyệt yêu cầu bảo trì hợp lý → chuyển trạng thái từ PENDING sang APPROVED.
                    - Ghi nhận người duyệt.
                    - Trigger Expense tự động (trừ quỹ nhóm).
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> approve(
            @PathVariable Long id,
            Authentication auth
    ) {
        return ResponseEntity.ok(maintenanceService.approve(id, auth.getName()));
    }

    /**
     * Staff/Admin Từ chối yêu cầu bảo trì (PENDING → REJECTED)
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Từ chối yêu cầu bảo trì",
            description = """
                    Nhân viên hoặc quản trị viên xác định yêu cầu không hợp lệ → chuyển trạng thái từ PENDING sang REJECTED.
                    - Ghi nhận người từ chối.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> reject(
            @PathVariable Long id,
            Authentication auth
    ) {
        return ResponseEntity.ok(maintenanceService.reject(id, auth.getName()));
    }

    /**
     * Staff va Admin Xem chi tiết yêu cầu bảo trì
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Xem chi tiết yêu cầu bảo trì",
            description = """
                    Staff và Admin đều có thể xem chi tiết yêu cầu bảo trì để kiểm tra thông tin trước khi duyệt hoặc từ chối.
                    Admin có thể xem toàn bộ; Staff chỉ xem trong phạm vi nhóm mà họ quản lý.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> getOne(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(maintenanceService.getOne(id));
    }

    // ================== STAFF / ADMIN: START MAINTENANCE ==================
    @PutMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Bắt đầu bảo trì",
            description = """
                    Chỉ được bắt đầu bảo trì khi maintenance đã FUNDED (tất cả payment đã thanh toán).
                    - Chuyển trạng thái từ FUNDED → IN_PROGRESS.
                    - Ghi lại thời điểm bắt đầu và thời điểm dự kiến hoàn tất.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> startMaintenance(
            @PathVariable Long id,
            Authentication auth
    ) {
        return ResponseEntity.ok(maintenanceService.startMaintenance(id, auth.getName()));
    }

    // ================== STAFF / ADMIN: COMPLETE MAINTENANCE ==================
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Hoàn tất bảo trì",
            description = """
                    Chỉ được hoàn tất khi đang IN_PROGRESS.
                    - Chuyển trạng thái IN_PROGRESS → COMPLETED.
                    - Ghi lại thời điểm hoàn tất.
                    - Có thể cập nhật ngày bảo trì định kỳ tiếp theo (nextDueDate) nếu có.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> completeMaintenance(
            @PathVariable Long id,
            @RequestParam LocalDate nextDueDate,
            Authentication auth
    ) {
        return ResponseEntity.ok(maintenanceService.completeMaintenance(id, nextDueDate, auth.getName()));
    }


}
