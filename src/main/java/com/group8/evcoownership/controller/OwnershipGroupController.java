package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.service.OwnershipGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Ownership Groups", description = "Quản lý nhóm đồng sở hữu và phương tiện")
public class OwnershipGroupController {

    private final OwnershipGroupService service;

    @PostMapping("/with-vehicle")
    @Operation(summary = "Tạo nhóm với phương tiện", description = "Tạo nhóm đồng sở hữu mới kèm theo phương tiện và nhiều hình ảnh với OCR auto-fill")
    public GroupWithVehicleResponseDTO createGroupWithVehicle(
            @Valid @ModelAttribute CreateGroupWithVehicleRequestDTO request,
            @AuthenticationPrincipal String userEmail) {
        Integer memberCapacity = Integer.parseInt(request.memberCapacity());
        return service.createGroupWithVehicle(
                request.groupName(), request.description(), memberCapacity,
                request.vehicleValue(), request.licensePlate(), request.chassisNumber(),
                request.vehicleImages(), request.imageTypes(), userEmail,
                request.brand(), request.model(), request.enableAutoFill());
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Cập nhật nhóm", description = "Chủ nhóm hoặc thành viên có quyền cập nhật thông tin nhóm")
    public OwnershipGroupResponseDTO updateByUser(@PathVariable Long groupId,
                                                  @RequestBody @Valid OwnershipGroupUpdateRequestDTO req) {
        return service.updateByUser(groupId, req);
    }

    @PatchMapping("/{groupId}/status")
    @Operation(summary = "Cập nhật trạng thái nhóm", description = "Thay đổi trạng thái hoạt động của nhóm")
    public OwnershipGroupResponseDTO updateStatus(@PathVariable Long groupId,
                                                  @RequestBody @Valid OwnershipGroupStatusUpdateRequestDTO req) {
        return service.updateStatus(groupId, req);
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Lấy chi tiết nhóm", description = "Trả về thông tin chi tiết của một nhóm theo ID")
    public OwnershipGroupResponseDTO getById(@PathVariable Long groupId, @AuthenticationPrincipal String userEmail) {
        return service.getByIdWithUserRole(groupId, userEmail);
    }

    /**
     * Lấy danh sách tất cả groups trong hệ thống (chỉ dành cho Staff và Admin)
     */
    @GetMapping("/staff/all")
    @Operation(summary = "Danh sách tất cả nhóm (staff)", description = "Chỉ STAFF/ADMIN có thể xem, hỗ trợ lọc và phân trang với custom vehicle description")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public Page<StaffOwnershipGroupResponseDTO> listAllGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GroupStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {

        return service.listForStaff(keyword, status, fromDate, toDate, PageRequest.of(page, size));
    }

    /**
     * Lấy tất cả groups mà user hiện tại đã tạo và tham gia
     */
    @GetMapping("/my-groups")
    @Operation(summary = "Nhóm của tôi", description = "Lấy tất cả nhóm mà người dùng hiện tại tạo hoặc tham gia")
    public List<OwnershipGroupResponseDTO> getMyGroups(@AuthenticationPrincipal String userEmail) {
        return service.getGroupsByUser(userEmail);
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "Xóa nhóm", description = "Xóa một nhóm theo ID")
    public void delete(@PathVariable Long groupId) {
        service.delete(groupId);
    }
}