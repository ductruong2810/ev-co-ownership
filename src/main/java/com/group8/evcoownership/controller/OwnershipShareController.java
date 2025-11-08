package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.OwnershipShareService;
import com.group8.evcoownership.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
@Tag(name = "Ownership Shares", description = "Quản lý tỷ lệ sở hữu")
@PreAuthorize("isAuthenticated()")
public class OwnershipShareController {

    private final OwnershipShareService service;
    private final UserProfileService userProfileService;

    // ========== MEMBERSHIP MANAGEMENT ==========

    // Thêm member + % sở hữu -> auto tryActivate
    @PostMapping
    @Operation(summary = "Thêm thành viên", description = "Thêm thành viên mới với tỷ lệ sở hữu và tự động kích hoạt nhóm")
    // Cần rõ là admin của group (owner/manager) HOẶC staff/admin hệ thống
    @PreAuthorize("hasAnyRole('STAFF','ADMIN') or @ownershipGroupService.isGroupAdmin(authentication.name, #req.groupId)")
    public OwnershipShareResponseDTO addMember(@RequestBody @Valid OwnershipShareCreateRequestDTO req) {
        return service.addGroupShare(req);
    }

    // Danh sách theo group
    @GetMapping("/by-group/{groupId}")
    @Operation(summary = "Danh sách thành viên theo nhóm", description = "Lấy danh sách tất cả thành viên của một nhóm")
    public List<OwnershipShareResponseDTO> listByGroup(@PathVariable Long groupId) {
        return service.listByGroup(groupId);
    }

    // Danh sách theo user
    @GetMapping("/my-groups")
    @Operation(summary = "Danh sách nhóm của tôi", description = "Lấy danh sách tất cả nhóm của người dùng hiện tại")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    public List<OwnershipShareResponseDTO> getMyGroups(@AuthenticationPrincipal String userEmail) {
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        return service.listByUser(userId);
    }

    // Rời khỏi nhóm (chỉ khi Pending) - chỉ cho phép xóa chính mình
    @DeleteMapping("/{groupId}")
    @Operation(summary = "Rời khỏi nhóm", description = "Rời khỏi nhóm (chỉ khi nhóm ở trạng thái Pending)")
    // Co-owner và phải là member của nhóm
    @PreAuthorize("hasRole('CO_OWNER') and @ownershipGroupService.isGroupMember(authentication.name, #groupId)")
    public void leaveGroup(@PathVariable Long groupId, @AuthenticationPrincipal String userEmail) {
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();
        service.removeMember(groupId, userId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "Kích thành viên khỏi nhóm", description = "Xóa thành viên khỏi nhóm. Chỉ thực hiện khi chưa có hợp đồng được ký hoặc đang hoạt động.")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN') or @ownershipGroupService.isGroupAdmin(authentication.name, #groupId)")
    public ResponseEntity<Void> removeMember(@PathVariable Long groupId, @PathVariable Long userId) {
        service.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== PERCENTAGE MANAGEMENT ==========

    /**
     * API TỔNG HỢP - Lấy tất cả dữ liệu cho trang nhập tỷ lệ sở hữu
     * GET /api/shares/page-data/{groupId}
     */
    @GetMapping("/page-data/{groupId}")
    @Operation(summary = "Dữ liệu trang tỷ lệ sở hữu", description = "Lấy tất cả dữ liệu cần thiết cho trang nhập tỷ lệ sở hữu bao gồm thông tin xe")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    public ResponseEntity<OwnershipPageDataResponseDTO> getOwnershipPageData(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        // Lấy thông tin user ownership
        OwnershipPercentageResponseDTO userOwnership = service.getOwnershipPercentage(userId, groupId);

        // Lấy tổng quan group
        GroupOwnershipSummaryResponseDTO groupSummary = service.getGroupOwnershipSummary(groupId, userId);

        // Lấy gợi ý tỷ lệ
        List<BigDecimal> suggestions = service.getOwnershipSuggestions(groupId);

        // Lấy thông tin xe (bao gồm biển số)
        VehicleResponseDTO vehicleInfo = service.getVehicleInfo(groupId);

        OwnershipPageDataResponseDTO response = OwnershipPageDataResponseDTO.builder()
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
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    public ResponseEntity<OwnershipPercentageResponseDTO> updateMyOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody OwnershipPercentageRequestDTO request) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        OwnershipPercentageResponseDTO response = service.updateOwnershipPercentage(
                userId, groupId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy tổng quan tỷ lệ sở hữu của group
     * GET /api/shares/group/{groupId}/summary
     */
    @GetMapping("/group/{groupId}/summary")
    @Operation(summary = "Tổng quan tỷ lệ nhóm", description = "Lấy tổng quan tỷ lệ sở hữu của tất cả thành viên trong nhóm")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    public ResponseEntity<GroupOwnershipSummaryResponseDTO> getGroupOwnershipSummary(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        GroupOwnershipSummaryResponseDTO response = service.getGroupOwnershipSummary(
                groupId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset tỷ lệ sở hữu của user về 0%
     * POST /api/shares/my-percentage/{groupId}/reset
     */
    @PostMapping("/my-percentage/{groupId}/reset")
    @Operation(summary = "Reset tỷ lệ sở hữu", description = "Reset tỷ lệ sở hữu của người dùng về 0%")
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    public ResponseEntity<OwnershipPercentageResponseDTO> resetMyOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        OwnershipPercentageResponseDTO response = service.resetOwnershipPercentage(userId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate tỷ lệ sở hữu trước khi lưu
     * POST /api/shares/my-percentage/{groupId}/validate
     */
    @PostMapping("/my-percentage/{groupId}/validate")
    @Operation(summary = "Kiểm tra tỷ lệ sở hữu", description = "Kiểm tra tính hợp lệ của tỷ lệ sở hữu trước khi lưu")
    public ResponseEntity<ValidationResponseDTO> validateMyOwnershipPercentage(
            @PathVariable Long groupId,
            @AuthenticationPrincipal String userEmail,
            @Valid @RequestBody OwnershipPercentageRequestDTO request) {

        // Lấy userId từ email
        Long userId = userProfileService.getUserProfile(userEmail).getUserId();

        // Gọi service để validate
        service.updateOwnershipPercentage(userId, groupId, request);

        return ResponseEntity.ok(ValidationResponseDTO.builder()
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
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    public ResponseEntity<List<BigDecimal>> getOwnershipSuggestions(@PathVariable Long groupId) {
        List<BigDecimal> suggestions = service.getOwnershipSuggestions(groupId);
        return ResponseEntity.ok(suggestions);
    }
}

