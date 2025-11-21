package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.service.ContractService;
import com.group8.evcoownership.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/contracts")
@RequiredArgsConstructor
public class AdminContractController { // Khai báo class controller cho phần quản lý hợp đồng của admin

    // Inject các service cần thiết
    private final ContractService contractService; // Service chứa logic xử lý hợp đồng
    private final UserService userService; // Service chứa logic xử lý người dùng (admin)

    /**
     * CHỈ DÀNH CHO ADMIN: Cập nhật cả thời hạn và điều khoản hợp đồng trong một lần gọi
     */
    @PutMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin update contract (duration + terms)", description = "System admin updates start/end dates and terms together")
    public ResponseEntity<ApiResponseDTO<ContractUpdateResponseDTO>> updateContractByAdmin(
            // @PathVariable: lấy giá trị {contractId} từ URL
            @PathVariable Long contractId,
            // @RequestBody: parse JSON trong body thành object Java
            // @Valid: bật validation tự động dựa vào annotation trong DTO
            @Valid @RequestBody ContractAdminUpdateRequestDTO request
    ) {
        // Kiểm tra xem ngày kết thúc có trước ngày bắt đầu không
        if (request.isInvalidDateRange()) {
            // Nếu có lõi => tạo object lỗi để trả về cho client
            ApiResponseDTO<ContractUpdateResponseDTO> error = ApiResponseDTO.<ContractUpdateResponseDTO>builder()
                    .success(false) // đánh dấu thất bại
                    .message("End date must be after start date") // nội dung thông báo lỗi
                    .data(null) // không có dữ liệu trả về
                    .build(); // hoàn tất object
            // Trả HTTP 400 Bad Request kèm nội dung lỗi
            return ResponseEntity.badRequest().body(error);
        }

        // Nếu hợp lệ => gọi service để cập nhật hợp đồng theo quyền admin
        ApiResponseDTO<ContractUpdateResponseDTO> result = contractService.updateContractByAdminByContractId(contractId, request);
        // Trả về HTTP 200 OK cùng kết quả
        return ResponseEntity.ok(result);
    }

    // API xử lý việc admin approve hoặc reject hợp đồng
    @PutMapping("/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractDTO> processContractApproval(
            // Lấy dữ liệu từ body request và validate
            @Valid @RequestBody ContractApprovalRequestDTO request,
            // Lấy thông tin email của người dùng hiện tại (admin đăng nhập)
            @AuthenticationPrincipal String userEmail) {

        // Tìm thông tin admin từ email trong cơ sở dữ liệu
        User admin = userService.findByEmail(userEmail);

        // Gọi service xử lý approve/reject contract
        ContractDTO contract = contractService.processContractApproval(
                request.getContractId(), // ID hợp đồng
                request.getAction(), // hành động (APPROVE hoặc REJECT)
                request.getReason(), // lý do reject còn APPROVE thì k có
                admin // thông tin admin thực hiện
        );

        // Trả về kết quả contract sau khi được xử lý
        return ResponseEntity.ok(contract);
    }

    /**
     * Kiểm tra trạng thái đóng tiền cọc của hợp đồng (chỉ dành cho admin)
     */
    @GetMapping("/{groupId}/deposit-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractDepositStatusResponseDTO> checkDepositStatus(@PathVariable Long groupId) {
        ContractDepositStatusResponseDTO status = contractService.checkDepositStatus(groupId);
        return ResponseEntity.ok(status);
    }

    // Lấy danh sách tất cả hợp đồng (chỉ admin)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContractDTO>> getAllContracts() {
        // Gọi service lấy toàn bộ hợp đồng
        List<ContractDTO> contracts = contractService.getAllContracts();
        // Trả về danh sách đó cho client
        return ResponseEntity.ok(contracts);
    }

    // Lấy thông tin chi tiết 1 hợp đồng theo ID
    @GetMapping("/{contractId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractDTO> getContractById(@PathVariable Long contractId) {
        // Gọi service để lấy hợp đồng theo ID
        ContractDTO contract = contractService.getContractById(contractId);
        // Trả về kết quả
        return ResponseEntity.ok(contract);
    }

    // Lấy danh sách hợp đồng đang ở trạng thái PENDING (chờ xử lý)
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')") // chỉ admin được gọi
    public ResponseEntity<List<ContractDTO>> getPendingContracts() {
        // Gọi service để lọc hợp đồng có trạng thái PENDING
        List<ContractDTO> contracts = contractService.getContractsByStatus(ContractApprovalStatus.PENDING);
        // Trả kết quả cho client
        return ResponseEntity.ok(contracts);
    }

    /**
     * Lấy danh sách tất cả hợp đồng thuộc 1 group (Admin only)
     */
    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContractDTO>> getContractsByGroup(@PathVariable Long groupId) {
        // Gọi service lấy danh sách hợp đồng của group đó (phiên bản dành cho admin)
        List<ContractDTO> contracts = contractService.getContractsByGroupForAdmin(groupId);
        // Trả danh sách đó về
        return ResponseEntity.ok(contracts);
    }

    // Lấy danh sách hợp đồng đang chờ thành viên phê duyệt
    @GetMapping("/pending-member-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ContractDTO>> getPendingMemberApprovalContracts() {
        // Gọi service lọc theo trạng thái PENDING_MEMBER_APPROVAL
        List<ContractDTO> contracts = contractService.getContractsByStatuses(
                List.of(ContractApprovalStatus.PENDING, ContractApprovalStatus.PENDING_MEMBER_APPROVAL)
        );        // Trả danh sách về client
        return ResponseEntity.ok(contracts);
    }

    /**
     * Approve (chấp thuận) một feedback cụ thể
     * Chỉ dành cho Group Admin (CO_OWNER)
     */
    @PutMapping("/feedbacks/{feedbackId}/approve")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(
            summary = "Approve feedback",
            description = "Group admin approve một feedback cụ thể. Chỉ có thể approve feedbacks có status = PENDING."
    )
    public ResponseEntity<ApiResponseDTO<FeedbackActionResponseDTO>> approveFeedback(
            @PathVariable Long feedbackId, // ID feedback cần xử lý
            // request body có thể có hoặc không (admin có thể gửi note hoặc để trống)
            @RequestBody(required = false) FeedbackActionRequestDTO request,
            @AuthenticationPrincipal String userEmail
    ) {
        // Group admin: cần userId để validate
        Long userId = contractService.getUserIdByEmail(userEmail);
        ApiResponseDTO<FeedbackActionResponseDTO> result = contractService.approveFeedbackByGroupAdmin(feedbackId, request, userId);
        
        // Trả kết quả thành công
        return ResponseEntity.ok(result);
    }

    /**
     * Reject (từ chối) một feedback cụ thể
     * Chỉ dành cho Group Admin (CO_OWNER)
     */
    @PutMapping("/feedbacks/{feedbackId}/reject")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(
            summary = "Reject feedback",
            description = "Group admin reject một feedback cụ thể. Chỉ có thể reject feedbacks có status = PENDING."
    )
    public ResponseEntity<ApiResponseDTO<FeedbackActionResponseDTO>> rejectFeedback(
            @PathVariable Long feedbackId, // ID feedback cần reject
            @RequestBody(required = false) FeedbackActionRequestDTO request, // có thể chứa lý do reject
            @AuthenticationPrincipal String userEmail
    ) {
        // Group admin: cần userId để validate
        Long userId = contractService.getUserIdByEmail(userEmail);
        ApiResponseDTO<FeedbackActionResponseDTO> result = contractService.rejectFeedbackByGroupAdmin(feedbackId, request, userId);
        
        // Trả về kết quả
        return ResponseEntity.ok(result);
    }
}
