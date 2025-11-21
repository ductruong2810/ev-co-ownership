package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.MaintenanceAfterCheckOutCreateRequestDTO;
import com.group8.evcoownership.dto.MaintenanceResponseDTO;
import com.group8.evcoownership.dto.UserWithRejectedCheckDTO;
import com.group8.evcoownership.service.MaintenanceAfterCheckOutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/after-checkout/maintenances")
@RequiredArgsConstructor
@Tag(
        name = "Maintenance After Check-out",
        description = "Flow bảo trì sau khi đi xe về, co-owner làm hư xe (PERSONAL – co-owner tự trả tiền)"
)
@PreAuthorize("isAuthenticated()")
public class MaintenanceAfterCheckOutController {

    private final MaintenanceAfterCheckOutService maintenanceAfterCheckOutService;

    // ======================================================
    // ===================== TECHNICIAN =====================
    // ======================================================

    @GetMapping("/rejected-users")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    @Operation(
            summary = "[Technician] Danh sách user có VehicleCheck bị reject",
            description = """
                Dựa trên bảng VehicleCheck:
                - Lọc các check có status REJECTED / FAILED / NEEDS_ATTENTION
                - Lấy booking.user
                - Trả về danh sách user (id, name, email) không trùng.
                """
    )
    public ResponseEntity<List<UserWithRejectedCheckDTO>> getUsersWithRejectedChecks() {
        return ResponseEntity.ok(
                maintenanceAfterCheckOutService.getUsersWithRejectedChecks()
        );
    }

    @PostMapping("/vehicles/{vehicleId}")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    @Operation(
            summary = "[Technician] Tạo yêu cầu bảo trì sau khi đi xe về (PERSONAL)",
            description = """
                Technician mở yêu cầu bảo trì khi phát hiện co-owner làm hư xe sau khi trả xe.
                - coverageType = PERSONAL.
                - Path: vehicleId (xe đang kiểm tra).
                - Body: userId (co-owner phải trả), description, cost, estimatedDurationDays.
                - Backend kiểm tra userId có thuộc group của vehicle hay không.
                - Trạng thái ban đầu: PENDING.
                """
    )
    public ResponseEntity<MaintenanceResponseDTO> createAfterCheckOut(
            @PathVariable Long vehicleId,
            @Valid @RequestBody MaintenanceAfterCheckOutCreateRequestDTO req,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                maintenanceAfterCheckOutService.createAfterCheckOut(
                        vehicleId,
                        req,
                        auth.getName()
                )
        );
    }


    /**
     * Technician cập nhật yêu cầu bảo trì PERSONAL khi vẫn còn PENDING
     */
//    @PutMapping("/{id}")
//    @PreAuthorize("hasAnyRole('TECHNICIAN')")
//    @Operation(
//            summary = "[Technician] Cập nhật yêu cầu bảo trì PENDING (PERSONAL)",
//            description = """
//                    Chỉ cho phép cập nhật khi trạng thái là PENDING và người cập nhật chính là technician đã tạo.
//                    - Cho phép sửa: description, cost, nextDueDate, estimatedDurationDays.
//                    - Không cho phép đổi vehicle hoặc liableUser.
//                    """
//    )
//    public ResponseEntity<MaintenanceResponseDTO> updatePending(
//            @PathVariable Long id,
//            @Valid @RequestBody MaintenanceUpdateRequestDTO req,
//            Authentication auth
//    ) {
//        return ResponseEntity.ok(
//                maintenanceAfterCheckOutService.update(id, req, auth.getName())
//        );
//    }

    // ==================== Technician get his PERSONAL maintenance requests =============
    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    @Operation(
            summary = "[Technician] Xem các yêu cầu bảo trì sau check-out do mình tạo (PERSONAL)",
            description = """
                    Dành cho technician xem lại các yêu cầu bảo trì coverageType = PERSONAL
                    mà chính họ đã tạo sau khi kiểm tra xe (sau khi co-owner trả xe).
                    Sắp xếp theo RequestDate giảm dần.
                    """
    )
    public ResponseEntity<List<MaintenanceResponseDTO>> getMyPersonalRequests(Authentication auth) {
        return ResponseEntity.ok(
                maintenanceAfterCheckOutService.getMyPersonalRequests(auth.getName())
        );
    }


    // ======================================================
    // ===================== CO-OWNER =======================
    // ======================================================

    /**
     * Co-owner xem danh sách maintenance mà mình là người phải trả tiền (liableUser)
     */
    @GetMapping("/my-liabilities")
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    @Operation(
            summary = "[Co-owner] Danh sách maintenance mình phải trả (PERSONAL)",
            description = """
                    Trả về tất cả yêu cầu bảo trì coverageType = PERSONAL mà user hiện tại là liableUser.
                    Sắp xếp theo RequestDate giảm dần.
                    """
    )
    public ResponseEntity<List<MaintenanceResponseDTO>> getMyLiabilities(
            Authentication auth
    ) {
        return ResponseEntity.ok(
                maintenanceAfterCheckOutService.getMyLiabilities(auth.getName())
        );
    }

    // ======================================================
    // ================== STAFF / ADMIN =====================
    // ======================================================

    /**
     * Staff/Admin xem chi tiết 1 maintenance (PERSONAL hoặc GROUP_FUND)
     * (ở đây chủ yếu dùng cho PERSONAL sau check-out)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Xem chi tiết tat ca maintenance(PERSONAL AND GROUP) ",
            description = """
                    Staff và Admin có thể xem chi tiết yêu cầu bảo trì (bao gồm thông tin xe, technician, liableUser, cost, status...).
                    Dùng cho việc kiểm tra trước khi duyệt/từ chối hoặc theo dõi tiến độ.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> getOne(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(maintenanceAfterCheckOutService.getOne(id));
    }

    /**
     * Staff/Admin duyệt yêu cầu bảo trì PERSONAL: PENDING → APPROVED
     */
//    @PutMapping("/{id}/approve")
//    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
//    @Operation(
//            summary = "[Staff/Admin] Duyệt yêu cầu bảo trì sau check-out (PENDING → APPROVED)",
//            description = """
//                    Chỉ áp dụng cho các yêu cầu coverageType = PERSONAL.
//                    - Chuyển trạng thái từ PENDING sang APPROVED.
//                    - Ghi nhận người duyệt và thời điểm duyệt.
//                    - Không chia tiền theo tỷ lệ sở hữu, không tạo Expense từ quỹ nhóm.
//                    """
//    )
//    public ResponseEntity<MaintenanceResponseDTO> approveAfterCheckOut(
//            @PathVariable Long id,
//            Authentication auth
//    ) {
//        return ResponseEntity.ok(
//                maintenanceAfterCheckOutService.approveAfterCheckOut(id, auth.getName())
//        );
//    }

    /**
     * Staff/Admin từ chối yêu cầu bảo trì PERSONAL: PENDING → REJECTED
     */
//    @PutMapping("/{id}/reject")
//    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
//    @Operation(
//            summary = "[Staff/Admin] Từ chối yêu cầu bảo trì sau check-out (PENDING → REJECTED)",
//            description = """
//                    Chỉ áp dụng cho các yêu cầu coverageType = PERSONAL.
//                    - Chuyển trạng thái từ PENDING sang REJECTED.
//                    - Ghi nhận người xử lý và thời điểm.
//                    """
//    )
//    public ResponseEntity<MaintenanceResponseDTO> rejectAfterCheckOut(
//            @PathVariable Long id,
//            Authentication auth
//    ) {
//        return ResponseEntity.ok(
//                maintenanceAfterCheckOutService.rejectAfterCheckOut(id, auth.getName())
//        );
//    }

    /**
     * Staff/Admin: FUNDED → IN_PROGRESS cho flow sau check-out (PERSONAL)
     */
//    @PutMapping("/{id}/start")
//    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
//    @Operation(
//            summary = "[Staff/Admin] Bắt đầu bảo trì sau check-out (FUNDED → IN_PROGRESS)",
//            description = """
//                    Dùng cho flow after check-out, coverageType = PERSONAL.
//                    - Chỉ cho phép khi trạng thái hiện tại là FUNDED (co-owner đã thanh toán xong).
//                    - Ghi lại thời điểm bắt đầu và thời điểm dự kiến hoàn tất (nếu có estimatedDurationDays).
//                    """
//    )
//    public ResponseEntity<MaintenanceResponseDTO> startAfterCheckOut(
//            @PathVariable Long id,
//            Authentication auth
//    ) {
//        return ResponseEntity.ok(
//                maintenanceAfterCheckOutService.startAfterCheckOut(id, auth.getName())
//        );
//    }

    /**
     * Staff/Admin: FUNDED → COMPLETED cho flow sau check-out (PERSONAL)
     */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('TECHNICIAN')")
    @Operation(
            summary = "[Staff/Admin] Hoàn tất bảo trì sau check-out (FUNDED → COMPLETED)",
            description = """
                    Chỉ áp dụng cho coverageType = PERSONAL.
                    - Chỉ được hoàn tất khi trạng thái hiện tại là FUNDED
                    - Ghi lại thời điểm hoàn tất.
                    - Đây là case sự cố, không phải bảo trì định kỳ nên thường không đặt nextDueDate.
                    """
    )
    public ResponseEntity<MaintenanceResponseDTO> completeAfterCheckOut(
            @PathVariable Long id,
            Authentication auth
    ) {
        return ResponseEntity.ok(
                maintenanceAfterCheckOutService.completeAfterCheckOut(id, auth.getName())
        );
    }

    /**
     * Staff/Admin: search maintenance PERSONAL sau check-out theo nhiều tiêu chí
     */
    @GetMapping("/staff/search")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(
            summary = "[Staff/Admin] Tìm kiếm maintenance sau check-out (PERSONAL)",
            description = """
                    Lọc các yêu cầu bảo trì coverageType = PERSONAL theo nhiều tiêu chí:
                    - status: PENDING / APPROVED / FUNDED / IN_PROGRESS / COMPLETED / REJECTED
                    - groupId: nhóm sở hữu xe (vehicle.ownershipGroup.groupId)
                    - vehicleId: xe cụ thể
                    - liableUserId: co-owner phải trả tiền
                    - requestedById: technician tạo yêu cầu
                    - fromRequestDate, toRequestDate: khoảng ngày tạo yêu cầu
                    - costFrom, costTo: khoảng chi phí ActualCost
                    Nếu không truyền field nào thì trả về toàn bộ PERSONAL, sort theo RequestDate giảm dần.
                    """
    )
    public ResponseEntity<List<MaintenanceResponseDTO>> searchPersonalForStaff(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long liableUserId,
            @RequestParam(required = false) Long requestedById,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromRequestDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toRequestDate,
            @RequestParam(required = false) BigDecimal costFrom,
            @RequestParam(required = false) BigDecimal costTo
    ) {
        return ResponseEntity.ok(
                maintenanceAfterCheckOutService.searchPersonalForStaff(
                        status,
                        groupId,
                        vehicleId,
                        liableUserId,
                        requestedById,
                        fromRequestDate,
                        toRequestDate,
                        costFrom,
                        costTo
                )
        );
    }
}
