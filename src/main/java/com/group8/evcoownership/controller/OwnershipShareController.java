package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
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
@RequestMapping("/api/shares")
@RequiredArgsConstructor
@Tag(name = "Ownership Shares", description = "Quản lý tỷ lệ sở hữu")
public class OwnershipShareController {

    private final OwnershipShareService service;
    private final UserProfileService userProfileService;

    // ========== MEMBERSHIP MANAGEMENT ==========

    // Thêm member + % sở hữu -> auto tryActivate
    @PostMapping
    @Operation(summary = "Thêm thành viên", description = "Thêm thành viên mới với tỷ lệ sở hữu và tự động kích hoạt nhóm")
    public OwnershipShareResponse addMember(@RequestBody @Valid OwnershipShareCreateRequest req) {
        return service.addGroupShare(req);
    }

    // Danh sách theo group
    @GetMapping("/by-group/{groupId}")
    @Operation(summary = "Danh sách thành viên theo nhóm", description = "Lấy danh sách tất cả thành viên của một nhóm")
    public List<OwnershipShareResponse> listByGroup(@PathVariable Long groupId) {
        return service.listByGroup(groupId);
    }

    // Danh sách theo user
    @GetMapping("/my-groups")
    @Operation(summary = "Danh sách nhóm của tôi", description = "Lấy danh sách tất cả nhóm của người dùng hiện tại")
    public List<OwnershipShareResponse> getMyGroups(@AuthenticationPrincipal String userEmail) {
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        return service.listByUser(userId);
    }

    // Xoá member (chỉ khi Pending) - chỉ cho phép xóa chính mình
    @DeleteMapping("/{groupId}")
    @Operation(summary = "Rời khỏi nhóm", description = "Rời khỏi nhóm (chỉ khi nhóm ở trạng thái Pending)")
    public void leaveGroup(@PathVariable Long groupId, @AuthenticationPrincipal String userEmail) {
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        service.removeMember(groupId, userId);
    }

    // ========== PERCENTAGE MANAGEMENT ==========

    /**
     * API TỔNG HỢP - Lấy tất cả dữ liệu cho trang nhập tỷ lệ sở hữu
     * GET /api/shares/page-data/{groupId}
     */
    @GetMapping("/page-data/{groupId}")
    @Operation(summary = "Dữ liệu trang tỷ lệ sở hữu", description = "Lấy tất cả dữ liệu cần thiết cho trang nhập tỷ lệ sở hữu bao gồm thông tin xe")
    public ResponseEntity<OwnershipPageDataResponse> getOwnershipPageData(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        // Lấy thông tin user ownership
        OwnershipPercentageResponse userOwnership = service.getOwnershipPercentage(userId, groupId);

        // Lấy tổng quan group
        GroupOwnershipSummaryResponse groupSummary = service.getGroupOwnershipSummary(groupId, userId);

        // Lấy gợi ý tỷ lệ
        List<BigDecimal> suggestions = service.getOwnershipSuggestions(groupId);

        // Lấy thông tin xe (bao gồm biển số)
        VehicleResponse vehicleInfo = service.getVehicleInfo(groupId);

        OwnershipPageDataResponse response = OwnershipPageDataResponse.builder()
                .userOwnership(userOwnership)
                .groupSummary(groupSummary)
                .suggestions(suggestions)
                .vehicleInfo(vehicleInfo)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật tỷ lệ sở hữu của user (cho trang nhập tỷ lệ)
     * PUT /api/shares/my-percentage/{groupId}
     */
    @PutMapping("/my-percentage/{groupId}")
    @Operation(summary = "Cập nhật tỷ lệ sở hữu của tôi", description = "Cập nhật tỷ lệ sở hữu của người dùng trong nhóm")
    public ResponseEntity<OwnershipPercentageResponse> updateMyOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody OwnershipPercentageRequest request) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        OwnershipPercentageResponse response = service.updateOwnershipPercentage(
                userId, groupId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật tỷ lệ sở hữu của thành viên (lấy userId từ token)
     * PUT /api/shares/{groupId}/percentage
     */
    @PutMapping("/{groupId}/percentage")
    @Operation(summary = "Cập nhật tỷ lệ sở hữu thành viên",
            description = "Cập nhật tỷ lệ sở hữu của thành viên")
    public OwnershipPercentageResponse updateMemberOwnershipPercentage(@PathVariable Long groupId,
                                                                       @AuthenticationPrincipal String userEmail,
                                                                       @RequestBody @Valid OwnershipPercentageRequest req) {
        // Lấy userId từ JWT token
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        return service.updateOwnershipPercentage(userId, groupId, req);
    }

    /**
     * Lấy tổng quan tỷ lệ sở hữu của group
     * GET /api/shares/group/{groupId}/summary
     */
    @GetMapping("/group/{groupId}/summary")
    @Operation(summary = "Tổng quan tỷ lệ nhóm", description = "Lấy tổng quan tỷ lệ sở hữu của tất cả thành viên trong nhóm")
    public ResponseEntity<GroupOwnershipSummaryResponse> getGroupOwnershipSummary(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        GroupOwnershipSummaryResponse response = service.getGroupOwnershipSummary(
                groupId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset tỷ lệ sở hữu của user về 0%
     * POST /api/shares/my-percentage/{groupId}/reset
     */
    @PostMapping("/my-percentage/{groupId}/reset")
    @Operation(summary = "Reset tỷ lệ sở hữu", description = "Reset tỷ lệ sở hữu của người dùng về 0%")
    public ResponseEntity<OwnershipPercentageResponse> resetMyOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        OwnershipPercentageResponse response = service.resetOwnershipPercentage(userId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate tỷ lệ sở hữu trước khi lưu
     * POST /api/shares/my-percentage/{groupId}/validate
     */
    @PostMapping("/my-percentage/{groupId}/validate")
    @Operation(summary = "Kiểm tra tỷ lệ sở hữu", description = "Kiểm tra tính hợp lệ của tỷ lệ sở hữu trước khi lưu")
    public ResponseEntity<ValidationResponse> validateMyOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody OwnershipPercentageRequest request) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        // Gọi service để validate
        service.updateOwnershipPercentage(userId, groupId, request);

        return ResponseEntity.ok(ValidationResponse.builder()
                .valid(true)
                .message("Tỷ lệ sở hữu hợp lệ")
                .build());
    }

    /**
     * Lấy danh sách các tỷ lệ sở hữu có thể chọn (gợi ý)
     * GET /api/shares/{groupId}/suggestions
     */
    @GetMapping("/{groupId}/suggestions")
    @Operation(summary = "Gợi ý tỷ lệ sở hữu", description = "Lấy danh sách các tỷ lệ sở hữu được gợi ý cho nhóm")
    public ResponseEntity<List<BigDecimal>> getOwnershipSuggestions(@PathVariable Long groupId) {
        List<BigDecimal> suggestions = service.getOwnershipSuggestions(groupId);
        return ResponseEntity.ok(suggestions);
    }
}

