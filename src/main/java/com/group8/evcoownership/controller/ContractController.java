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
@Tag(name = "Contracts", description = "Create, export PDF, sign and approve contracts")
public class ContractController {

    private final ContractService contractService;

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
     * Get contract info of group (for members)
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    @Operation(summary = "Get contract information", description = "Get contract information of the group")
    public ResponseEntity<Map<String, Object>> getContractInfo(@PathVariable Long groupId) {
        Map<String, Object> contractInfo = contractService.getContractInfo(groupId);
        return ResponseEntity.ok(contractInfo);
    }

    /**
     * Save contract data to database
     */
    @PostMapping("/{groupId}/save")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Save contract data", description = "Save contract data to database")
    public ResponseEntity<Map<String, Object>> saveContractData(@PathVariable Long groupId) {
        Map<String, Object> savedContract = contractService.saveContractFromData(groupId);
        return ResponseEntity.ok(savedContract);
    }

    /**
     * Sign contract (admin only)
     */
    @PostMapping("/{groupId}/sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Sign contract", description = "Only group admin can sign the contract")
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
