package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.ContractApprovalRequest;
import com.group8.evcoownership.dto.SaveContractDataRequest;
import com.group8.evcoownership.service.ContractGenerationService;
import com.group8.evcoownership.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
     * Lấy thông tin contract của group (cho members)
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("@ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    @Operation(summary = "Lấy thông tin hợp đồng", description = "Lấy thông tin hợp đồng của nhóm")
    public ResponseEntity<Map<String, Object>> getContractInfo(@PathVariable Long groupId) {
        Map<String, Object> contractInfo = contractService.getContractInfo(groupId);
        return ResponseEntity.ok(contractInfo);
    }

    /**
     * Lưu contract data xuống database
     */
    @PostMapping("/{groupId}/save")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Lưu dữ liệu hợp đồng", description = "Lưu dữ liệu hợp đồng xuống cơ sở dữ liệu")
    public ResponseEntity<Map<String, Object>> saveContractData(
            @PathVariable Long groupId,
            @RequestBody @Valid SaveContractDataRequest request
    ) {
        Map<String, Object> savedContract = contractGenerationService.saveContractFromData(groupId, request);
        return ResponseEntity.ok(savedContract);
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
