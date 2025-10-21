package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.GroupOwnershipSummaryResponse;
import com.group8.evcoownership.dto.OwnershipPercentageRequest;
import com.group8.evcoownership.dto.OwnershipPercentageResponse;
import com.group8.evcoownership.service.OwnershipShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/ownership-percentage")
@RequiredArgsConstructor
public class OwnershipPercentageController {

    private final OwnershipShareService ownershipShareService;

    /**
     * API TỔNG HỢP - Lấy tất cả dữ liệu cho trang nhập tỷ lệ sở hữu
     * GET /api/ownership-percentage/page-data/{userId}/{groupId}
     */
    @GetMapping("/page-data/{userId}/{groupId}")
    public ResponseEntity<OwnershipPageDataResponse> getOwnershipPageData(
            @PathVariable Long userId,
            @PathVariable Long groupId) {
        
        // Lấy thông tin user ownership
        OwnershipPercentageResponse userOwnership = ownershipShareService.getOwnershipPercentage(userId, groupId);
        
        // Lấy tổng quan group
        GroupOwnershipSummaryResponse groupSummary = ownershipShareService.getGroupOwnershipSummary(groupId, userId);
        
        // Lấy gợi ý tỷ lệ
        List<BigDecimal> suggestions = ownershipShareService.getOwnershipSuggestions(groupId);
        
        OwnershipPageDataResponse response = OwnershipPageDataResponse.builder()
                .userOwnership(userOwnership)
                .groupSummary(groupSummary)
                .suggestions(suggestions)
                .build();
        
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin tỷ lệ sở hữu của user trong group
     * GET /api/ownership-percentage/{userId}/{groupId}
     */
    @GetMapping("/{userId}/{groupId}")
    public ResponseEntity<OwnershipPercentageResponse> getOwnershipPercentage(
            @PathVariable Long userId,
            @PathVariable Long groupId) {
        
        OwnershipPercentageResponse response = ownershipShareService.getOwnershipPercentage(userId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật tỷ lệ sở hữu của user
     * PUT /api/ownership-percentage/{userId}/{groupId}
     */
    @PutMapping("/{userId}/{groupId}")
    public ResponseEntity<OwnershipPercentageResponse> updateOwnershipPercentage(
            @PathVariable Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody OwnershipPercentageRequest request) {
        
        OwnershipPercentageResponse response = ownershipShareService.updateOwnershipPercentage(
                userId, groupId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy tổng quan tỷ lệ sở hữu của group
     * GET /api/ownership-percentage/group/{groupId}/summary?currentUserId={currentUserId}
     */
    @GetMapping("/group/{groupId}/summary")
    public ResponseEntity<GroupOwnershipSummaryResponse> getGroupOwnershipSummary(
            @PathVariable Long groupId,
            @RequestParam Long currentUserId) {
        
        GroupOwnershipSummaryResponse response = ownershipShareService.getGroupOwnershipSummary(
                groupId, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset tỷ lệ sở hữu của user về 0%
     * POST /api/ownership-percentage/{userId}/{groupId}/reset
     */
    @PostMapping("/{userId}/{groupId}/reset")
    public ResponseEntity<OwnershipPercentageResponse> resetOwnershipPercentage(
            @PathVariable Long userId,
            @PathVariable Long groupId) {
        
        OwnershipPercentageResponse response = ownershipShareService.resetOwnershipPercentage(userId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate tỷ lệ sở hữu trước khi lưu
     * POST /api/ownership-percentage/{userId}/{groupId}/validate
     */
    @PostMapping("/{userId}/{groupId}/validate")
    public ResponseEntity<ValidationResponse> validateOwnershipPercentage(
            @PathVariable Long userId,
            @PathVariable Long groupId,
            @Valid @RequestBody OwnershipPercentageRequest request) {
        
        try {
            // Gọi service để validate
            ownershipShareService.updateOwnershipPercentage(userId, groupId, request);
            
            return ResponseEntity.ok(ValidationResponse.builder()
                    .valid(true)
                    .message("Tỷ lệ sở hữu hợp lệ")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(ValidationResponse.builder()
                    .valid(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * Lấy danh sách các tỷ lệ sở hữu có thể chọn (gợi ý)
     * GET /api/ownership-percentage/{groupId}/suggestions
     */
    @GetMapping("/{groupId}/suggestions")
    public ResponseEntity<List<BigDecimal>> getOwnershipSuggestions(@PathVariable Long groupId) {
        List<BigDecimal> suggestions = ownershipShareService.getOwnershipSuggestions(groupId);
        return ResponseEntity.ok(suggestions);
    }

    // Inner classes for response
    @lombok.Data
    @lombok.Builder
    public static class ValidationResponse {
        private boolean valid;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    public static class OwnershipPageDataResponse {
        private OwnershipPercentageResponse userOwnership;
        private GroupOwnershipSummaryResponse groupSummary;
        private List<BigDecimal> suggestions;
    }
}
