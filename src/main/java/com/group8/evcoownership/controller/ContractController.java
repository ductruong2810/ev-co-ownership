package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.ContractGenerationService;
import com.group8.evcoownership.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Tạo, xuất PDF, ký và duyệt hợp đồng")
public class ContractController {

    private final ContractService contractService;
    private final ContractGenerationService contractGenerationService;

    /**
     * API: Xem chi tiết hợp đồng của một nhóm
     * ------------------------------------------------------------
     * Dành cho:
     *   - Group Admin
     *   - Các thành viên trong nhóm
     *
     * Mục đích:
     *   Khi người dùng bấm "Xem hợp đồng" ở giao diện FE,
     *   API này sẽ trả về thông tin chi tiết gồm:
     *     1. Hợp đồng (terms, deposit, startDate, endDate, ...)
     *     2. Nhóm (tên, trạng thái, ngày tạo, ...)
     *     3. Danh sách thành viên (tên, email, vai trò, % sở hữu, trạng thái cọc, ...)
     */
    @GetMapping("/{groupId}/details")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    @Operation(
            summary = "Xem chi tiết hợp đồng",
            description = "Trả về thông tin hợp đồng, nhóm và danh sách thành viên trong nhóm"
    )
    public ResponseEntity<Map<String, Object>> getContractInfoDetail(
            @PathVariable Long groupId) {

        // Gọi service xử lý logic lấy thông tin chi tiết
        Map<String, Object> contractDetail = contractService.getContractInfoDetail(groupId);

        // Trả kết quả về client với HTTP 200 OK
        return ResponseEntity.ok(contractDetail);
    }


    /**
     * Generate contract automatically from React TSX template (Frontend provides templateContent)
     * - startDate = today
     * - endDate = today + 1 year
     * - terms extracted from TSX content
     */
    @PostMapping("/generate/{groupId}/auto")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Tạo hợp đồng tự động", description = "Sinh hợp đồng từ React TSX template do FE gửi lên")
    public ResponseEntity<ContractGenerationResponse> generateContractAuto(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> request
    ) {
        String templateContent = request == null ? null : request.get("templateContent");
        ContractGenerationResponse response = contractGenerationService.generateContractAuto(
                groupId,
                "REACT_TSX",
                templateContent
        );
        return ResponseEntity.ok(response);
    }

    // Removed legacy flexible/preview/with-template: TSX-only flow below

    /**
     * Export hợp đồng thành PDF từ React TSX template (Frontend gửi templateContent)
     */
    @PostMapping("/export/{groupId}/pdf")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Xuất hợp đồng PDF", description = "Xuất PDF từ nội dung TSX template do FE gửi lên")
    public ResponseEntity<byte[]> exportContractPdf(
            @PathVariable Long groupId,
            @RequestBody Map<String, String> request) {

        String templateContent = request.get("templateContent");
        if (templateContent == null || templateContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Template content is required from Frontend");
        }

        byte[] pdfBytes = contractGenerationService.exportToPdf(groupId, templateContent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "contract-" + groupId + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    /**
     * Lấy thông tin contract của group
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    @Operation(summary = "Lấy thông tin hợp đồng", description = "Thông tin hợp đồng của một group")
    public ResponseEntity<Map<String, Object>> getContractInfo(@PathVariable Long groupId) {
        Map<String, Object> contractInfo = contractService.getContractInfo(groupId);
        return ResponseEntity.ok(contractInfo);
    }

    /**
     * Ký hợp đồng (chỉ admin group)
     */
    @PostMapping("/{groupId}/sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Ký hợp đồng", description = "Chỉ admin của nhóm được phép ký")
    public ResponseEntity<Map<String, Object>> signContract(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> signRequest) {

        Map<String, Object> result = contractService.signContract(groupId, signRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * Staff duyệt hợp đồng
     */
    @PatchMapping("/{contractId}/approve")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Duyệt hợp đồng", description = "Staff/Admin phê duyệt hợp đồng")
    public ResponseEntity<Map<String, Object>> approveContract(
            @PathVariable Long contractId,
            @Valid @RequestBody ContractApprovalRequest request,
            @AuthenticationPrincipal String staffEmail) {

        Map<String, Object> result = contractService.approveContract(contractId, request, staffEmail);
        return ResponseEntity.ok(result);
    }

    /**
     * Lấy danh sách hợp đồng chờ duyệt (cho staff)
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Danh sách hợp đồng chờ duyệt", description = "Dành cho Staff/Admin, có phân trang")
    public ResponseEntity<Map<String, Object>> getPendingContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> result = contractService.getPendingContracts(page, size);
        return ResponseEntity.ok(result);
    }
}
