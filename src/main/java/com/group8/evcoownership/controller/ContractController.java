package com.group8.evcoownership.controller;

import com.group8.evcoownership.service.ContractService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Tạo, xuất PDF, ký và duyệt hợp đồng")
public class ContractController {

    private final ContractService contractService;

    /**
     * API: Xem chi tiết hợp đồng của một nhóm
     * ------------------------------------------------------------
     * Dành cho:
     *   - Group Admin
     *   - Các thành viên trong nhóm
     * <p>
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
     * Generate contract data (chỉ tạo nội dung, không save DB)
     */
    @GetMapping("/{groupId}/generate")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Tạo nội dung hợp đồng", description = "Tạo nội dung hợp đồng để preview, không lưu vào database")
    public ResponseEntity<Map<String, Object>> generateContractData(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        // Lấy userId từ authentication
        Long userId = Long.parseLong(userDetails.getUsername());
        
        Map<String, Object> contractData = contractService.generateContractData(groupId, userId);
        return ResponseEntity.ok(contractData);
    }



    /**
     * Hủy contract (chỉ admin group)
     */
    @PostMapping("/{groupId}/cancel")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Hủy hợp đồng", description = "Chỉ admin của nhóm được phép hủy hợp đồng với lý do")
    public ResponseEntity<Map<String, Object>> cancelContract(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> cancelRequest) {

        String reason = (String) cancelRequest.getOrDefault("reason", "Contract cancelled by group admin");
        contractService.cancelContract(groupId, reason);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Contract has been cancelled successfully");
        result.put("reason", reason);
        result.put("cancelledAt", LocalDateTime.now());

        return ResponseEntity.ok(result);
    }

    /**
     * API: Tự động ký contract cho group
     * ------------------------------------------------------------
     * Dành cho:
     *   - Group Admin
     *   - System (tự động trigger)
     * <p>
     * Điều kiện:
     *   - Group đã có đủ thành viên theo memberCapacity
     *   - Group đã có vehicle với vehicleValue
     *   - Contract chưa được ký
     */
    @PostMapping("/{groupId}/auto-sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Tự động ký contract", description = "Tự động ký contract khi đủ điều kiện")
    public ResponseEntity<Map<String, Object>> autoSignContract(@PathVariable Long groupId) {
        Map<String, Object> result = contractService.autoSignContract(groupId);
        return ResponseEntity.ok(result);
    }

    /**
     * API: Kiểm tra điều kiện ký tự động contract
     * ------------------------------------------------------------
     * Dành cho:
     *   - Group Admin
     *   - Các thành viên trong nhóm
     */
    @GetMapping("/{groupId}/auto-sign-conditions")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Kiểm tra điều kiện ký tự động", description = "Kiểm tra các điều kiện cần thiết để ký tự động contract")
    public ResponseEntity<Map<String, Object>> checkAutoSignConditions(@PathVariable Long groupId) {
        Map<String, Object> conditions = contractService.checkAutoSignConditions(groupId);
        return ResponseEntity.ok(conditions);
    }

    /**
     * API: Tự động kiểm tra và ký contract nếu đủ điều kiện
     * ------------------------------------------------------------
     * Dành cho:
     *   - System (scheduler)
     *   - Group Admin
     */
    @PostMapping("/{groupId}/check-and-auto-sign")
    @PreAuthorize("@ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    @Operation(summary = "Kiểm tra và ký tự động", description = "Tự động kiểm tra điều kiện và ký contract nếu đủ điều kiện")
    public ResponseEntity<Map<String, Object>> checkAndAutoSignContract(@PathVariable Long groupId) {
        Map<String, Object> result = contractService.checkAndAutoSignContract(groupId);
        return ResponseEntity.ok(result);
    }

    // Removed manual approval endpoints - contracts are now auto-approved
}