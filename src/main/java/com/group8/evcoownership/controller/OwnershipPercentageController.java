package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.GroupOwnershipSummaryResponse;
import com.group8.evcoownership.dto.OwnershipPercentageRequest;
import com.group8.evcoownership.dto.OwnershipPercentageResponse;
import com.group8.evcoownership.dto.OwnershipPageDataResponse;
import com.group8.evcoownership.dto.ValidationResponse;
import com.group8.evcoownership.service.OwnershipShareService;
import com.group8.evcoownership.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/ownership-percentage")
@RequiredArgsConstructor
@Tag(name = "Ownership Percentage", description = "Quản lý tỷ lệ sở hữu trong nhóm")
public class OwnershipPercentageController {

    private final OwnershipShareService ownershipShareService;
    private final UserProfileService userProfileService;

    /**
     * API TỔNG HỢP - Lấy tất cả dữ liệu cho trang nhập tỷ lệ sở hữu
     * GET /api/ownership-percentage/page-data/{groupId}
     */
    @GetMapping("/page-data/{groupId}")
    @Operation(summary = "Dữ liệu trang tỷ lệ sở hữu", description = "Lấy tất cả dữ liệu cần thiết cho trang nhập tỷ lệ sở hữu")
    public ResponseEntity<OwnershipPageDataResponse> getOwnershipPageData(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {
        
        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        
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
     * GET /api/ownership-percentage/{groupId}
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "Tỷ lệ sở hữu của tôi", description = "Lấy thông tin tỷ lệ sở hữu của người dùng hiện tại trong nhóm")
    public ResponseEntity<OwnershipPercentageResponse> getOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {
        
        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        
        OwnershipPercentageResponse response = ownershipShareService.getOwnershipPercentage(userId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật tỷ lệ sở hữu của user
     * PUT /api/ownership-percentage/{groupId}
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "Cập nhật tỷ lệ sở hữu", description = "Cập nhật tỷ lệ sở hữu của người dùng trong nhóm")
    public ResponseEntity<OwnershipPercentageResponse> updateOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody OwnershipPercentageRequest request) {
        
        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        
        OwnershipPercentageResponse response = ownershipShareService.updateOwnershipPercentage(
                userId, groupId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy tổng quan tỷ lệ sở hữu của group
     * GET /api/ownership-percentage/group/{groupId}/summary
     */
    @GetMapping("/group/{groupId}/summary")
    @Operation(summary = "Tổng quan tỷ lệ nhóm", description = "Lấy tổng quan tỷ lệ sở hữu của tất cả thành viên trong nhóm")
    public ResponseEntity<GroupOwnershipSummaryResponse> getGroupOwnershipSummary(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {
        
        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        
        GroupOwnershipSummaryResponse response = ownershipShareService.getGroupOwnershipSummary(
                groupId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset tỷ lệ sở hữu của user về 0%
     * POST /api/ownership-percentage/{groupId}/reset
     */
    @PostMapping("/{groupId}/reset")
    @Operation(summary = "Reset tỷ lệ sở hữu", description = "Reset tỷ lệ sở hữu của người dùng về 0%")
    public ResponseEntity<OwnershipPercentageResponse> resetOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {
        
        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        
        OwnershipPercentageResponse response = ownershipShareService.resetOwnershipPercentage(userId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate tỷ lệ sở hữu trước khi lưu
     * POST /api/ownership-percentage/{groupId}/validate
     */
    @PostMapping("/{groupId}/validate")
    @Operation(summary = "Kiểm tra tỷ lệ sở hữu", description = "Kiểm tra tính hợp lệ của tỷ lệ sở hữu trước khi lưu")
    public ResponseEntity<ValidationResponse> validateOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody OwnershipPercentageRequest request) {
        
        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        
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
    @Operation(summary = "Gợi ý tỷ lệ sở hữu", description = "Lấy danh sách các tỷ lệ sở hữu được gợi ý cho nhóm")
    public ResponseEntity<List<BigDecimal>> getOwnershipSuggestions(@PathVariable Long groupId) {
        List<BigDecimal> suggestions = ownershipShareService.getOwnershipSuggestions(groupId);
        return ResponseEntity.ok(suggestions);
    }

}
