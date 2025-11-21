package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.enums.MemberFeedbackStatus;
import com.group8.evcoownership.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Tạo, xuất PDF, ký và duyệt hợp đồng")
@PreAuthorize("isAuthenticated()")
public class ContractController {

    private final ContractService contractService;

    /**
     * API: Xem chi tiết hợp đồng của một nhóm
     */
    @GetMapping("/{groupId}/details")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId) or hasAnyRole('ADMIN','STAFF')")
    @Operation(
            summary = "Xem chi tiết hợp đồng",
            description = "Trả về thông tin hợp đồng, nhóm và danh sách thành viên trong nhóm"
    )
    public ResponseEntity<ContractDetailResponseDTO> getContractInfoDetail(
            @PathVariable Long groupId) {

        // Gọi service xử lý logic lấy thông tin chi tiết
        ContractDetailResponseDTO contractDetail = contractService.getContractInfoDetail(groupId);

        // Trả kết quả về client với HTTP 200 OK
        return ResponseEntity.ok(contractDetail);
    }


    /**
     * Lấy thông tin contract của group (cho members)
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId) or hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Lấy thông tin hợp đồng", description = "Lấy thông tin hợp đồng của nhóm")
    public ResponseEntity<ContractInfoResponseDTO> getContractInfo(@PathVariable Long groupId) {
        ContractInfoResponseDTO contractInfo = contractService.getContractInfo(groupId);
        return ResponseEntity.ok(contractInfo);
    }

    /**
     * chỉ tạo nội dung, không save DB
     */
    @GetMapping("/{groupId}/generate")
    @Operation(summary = "Tạo nội dung hợp đồng", description = "Tạo nội dung hợp đồng để preview, không lưu vào database")
    public ResponseEntity<ContractGenerationResponseDTO> generateContractData(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = contractService.getUserIdByEmail(userEmail);

        ContractGenerationResponseDTO contractData = contractService.generateContractData(groupId, userId);
        return ResponseEntity.ok(contractData);
    }

    /**
     * API: Lấy thông tin tính toán deposit amount (cho group admin hiểu công thức)
     */
    @GetMapping("/{groupId}/deposit-calculation")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(
            summary = "Lấy thông tin tính toán deposit",
            description = "Lấy giá trị deposit được tính toán tự động và giải thích công thức tính toán"
    )
    public ResponseEntity<DepositCalculationInfoDTO> getDepositCalculation(@PathVariable Long groupId) {
        DepositCalculationInfoDTO calculation = contractService.getDepositCalculationInfo(groupId);
        return ResponseEntity.ok(calculation);
    }

    /**
     * Từ chối contract và tạo feedback DISAGREE với status APPROVED (chỉ admin group)
     */
    @PostMapping("/{groupId}/cancel")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Từ chối hợp đồng", description = "Admin group từ chối hợp đồng và tạo feedback DISAGREE với status APPROVED để admin hệ thống xem. Contract status không thay đổi. Reason là bắt buộc.")
    public ResponseEntity<ContractCancelResponseDTO> cancelContract(
            @PathVariable Long groupId,
            @Valid @RequestBody ContractCancelRequestDTO request) {

        ContractCancelResponseDTO result = contractService.cancelContract(groupId, request.getReason());

        return ResponseEntity.ok(result);
    }

    /**
     * API: Tự động ký contract cho group
     */
    @PostMapping("/{groupId}/auto-sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Tự động ký contract", description = "Tự động ký contract khi đủ điều kiện")
    public ResponseEntity<AutoSignContractResponseDTO> autoSignContract(@PathVariable Long groupId) {
        AutoSignContractResponseDTO result = contractService.autoSignContract(groupId);
        return ResponseEntity.ok(result);
    }

    /**
     * API: Kiểm tra điều kiện ký tự động contract
     */
    @GetMapping("/{groupId}/auto-sign-conditions")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Kiểm tra điều kiện ký tự động", description = "Kiểm tra các điều kiện cần thiết để ký tự động contract")
    public ResponseEntity<AutoSignConditionsResponseDTO> checkAutoSignConditions(@PathVariable Long groupId) {
        AutoSignConditionsResponseDTO conditions = contractService.checkAutoSignConditions(groupId);
        return ResponseEntity.ok(conditions);
    }

    /**
     * API: Tự động kiểm tra và ký contract nếu đủ điều kiện
     */
    @PostMapping("/{groupId}/check-and-auto-sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Kiểm tra và ký tự động", description = "Tự động kiểm tra điều kiện và ký contract nếu đủ điều kiện")
    public ResponseEntity<AutoSignOutcomeResponseDTO> checkAndAutoSignContract(@PathVariable Long groupId) {
        AutoSignOutcomeResponseDTO result = contractService.checkAndAutoSignContract(groupId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{contractId}/member-feedback")
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    @Operation(
            summary = "Member agree/disagree với contract",
            description = "Member có thể agree hoặc disagree với contract. Nếu disagree, phải có lý do."
    )
    public ResponseEntity<ApiResponseDTO<SubmitMemberFeedbackResponseDTO>> submitMemberFeedback(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractMemberFeedbackRequestDTO request,
            @AuthenticationPrincipal String userEmail) {

        Long userId = contractService.getUserIdByEmail(userEmail);
        ApiResponseDTO<SubmitMemberFeedbackResponseDTO> result = contractService.submitMemberFeedback(contractId, userId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * API: Lấy tất cả feedback DISAGREE của members cho contract
     * - Admin group: xem tất cả DISAGREE với mọi status (PENDING, APPROVED, REJECTED)
     * - System admin: chỉ xem DISAGREE với status APPROVED (đã được approve)
     */
    @GetMapping("/{contractId}/member-feedbacks")
    @PreAuthorize("@ownershipGroupService.isGroupAdminForContract(authentication.name, #contractId) or hasAnyRole('ADMIN','STAFF')")
    @Operation(
            summary = "Get contract member feedbacks (DISAGREE only)",
            description = "Admin group xem tất cả DISAGREE (PENDING, APPROVED, REJECTED), System admin xem DISAGREE APPROVED"
    )
    public ResponseEntity<ContractFeedbacksResponseDTO> getContractMemberFeedbacks(
            @PathVariable Long contractId,
            Authentication authentication) {

        // Kiểm tra xem user là system admin hay group admin
        boolean isSystemAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_STAFF"));
        
        // Admin group: xem tất cả status (null = không filter), System admin: chỉ xem APPROVED
        MemberFeedbackStatus filterStatus = isSystemAdmin
                ? MemberFeedbackStatus.APPROVED
                : null; // null = trả về tất cả status

        ContractFeedbacksResponseDTO feedbacks = contractService.getContractFeedbacks(contractId, filterStatus);
        return ResponseEntity.ok(feedbacks);
    }

    /**
     * API: Lấy tất cả feedback DISAGREE của members theo groupId (cho admin group)
     */
    @GetMapping("/group/{groupId}/member-feedbacks")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId) or hasAnyRole('ADMIN','STAFF')")
    @Operation(
            summary = "Get member feedbacks by groupId (DISAGREE only)",
            description = "Lấy tất cả feedback DISAGREE của members theo groupId. Áp dụng cho nhóm có 1 hợp đồng hiện tại."
    )
    public ResponseEntity<ContractFeedbacksResponseDTO> getGroupMemberFeedbacks(
            @PathVariable Long groupId) {

        ContractFeedbacksResponseDTO feedbacks = contractService.getContractFeedbacksByGroup(groupId);
        return ResponseEntity.ok(feedbacks);
    }
}