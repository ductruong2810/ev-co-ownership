package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.OwnershipShareCreateRequest;
import com.group8.evcoownership.dto.OwnershipShareResponse;
import com.group8.evcoownership.dto.OwnershipShareUpdatePercentageRequest;
import com.group8.evcoownership.service.OwnershipShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
@Tag(name = "Ownership Shares", description = "Quản lý cổ phần sở hữu")
public class OwnershipShareController {

    private final OwnershipShareService service;

    // Thêm member + % sở hữu -> auto tryActivate
    @PostMapping
    @Operation(summary = "Thêm cổ phần", description = "Thêm thành viên mới với tỷ lệ sở hữu và tự động kích hoạt nhóm")
    public OwnershipShareResponse addShare(@RequestBody @Valid OwnershipShareCreateRequest req) {
        return service.addGroupShare(req);
    }

    // Cập nhật % sở hữu -> auto tryActivate
    @PutMapping("/{groupId}/{userId}/percentage")
    @Operation(summary = "Cập nhật tỷ lệ cổ phần", description = "Cập nhật tỷ lệ sở hữu của thành viên và tự động kích hoạt nhóm")
    public OwnershipShareResponse updatePercentage(@PathVariable Long groupId,
                                                   @PathVariable Long userId,
                                                   @RequestBody @Valid OwnershipShareUpdatePercentageRequest req) {
        return service.updatePercentage(groupId, userId, req);
    }

    // Lấy 1 membership
    @GetMapping("/{groupId}/{userId}")
    @Operation(summary = "Chi tiết cổ phần", description = "Lấy thông tin chi tiết cổ phần của một thành viên trong nhóm")
    public OwnershipShareResponse getOne(@PathVariable Long groupId, @PathVariable Long userId) {
        return service.getOne(groupId, userId);
    }

    // Danh sách theo group
    @GetMapping("/by-group/{groupId}")
    @Operation(summary = "Danh sách cổ phần theo nhóm", description = "Lấy danh sách tất cả cổ phần của một nhóm")
    public List<OwnershipShareResponse> listByGroup(@PathVariable Long groupId) {
        return service.listByGroup(groupId);
    }

    // Danh sách theo user
    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Danh sách cổ phần theo người dùng", description = "Lấy danh sách tất cả cổ phần của một người dùng")
    public List<OwnershipShareResponse> listByUser(@PathVariable Long userId) {
        return service.listByUser(userId);
    }

    // Xoá member (chỉ khi Pending)
    @DeleteMapping("/{groupId}/{userId}")
    @Operation(summary = "Xóa thành viên", description = "Xóa thành viên khỏi nhóm (chỉ khi nhóm ở trạng thái Pending)")
    public void remove(@PathVariable Long groupId, @PathVariable Long userId) {
        service.removeMember(groupId, userId);
    }
}

