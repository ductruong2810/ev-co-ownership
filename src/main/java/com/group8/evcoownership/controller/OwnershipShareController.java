package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.OwnershipShareCreateRequest;
import com.group8.evcoownership.dto.OwnershipShareResponse;
import com.group8.evcoownership.dto.OwnershipShareUpdatePercentageRequest;
import com.group8.evcoownership.service.OwnershipShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class OwnershipShareController {

    private final OwnershipShareService service;

    // Thêm member + % sở hữu -> auto tryActivate
    @PostMapping
    public OwnershipShareResponse addShare(@RequestBody @Valid OwnershipShareCreateRequest req) {
        return service.addGroupShare(req);
    }

    // Cập nhật % sở hữu -> auto tryActivate
    @PutMapping("/{groupId}/{userId}/percentage")
    public OwnershipShareResponse updatePercentage(@PathVariable Long groupId,
                                                   @PathVariable Long userId,
                                                   @RequestBody @Valid OwnershipShareUpdatePercentageRequest req) {
        return service.updatePercentage(groupId, userId, req);
    }

    // Lấy 1 membership
    @GetMapping("/{groupId}/{userId}")
    public OwnershipShareResponse getOne(@PathVariable Long groupId, @PathVariable Long userId) {
        return service.getOne(groupId, userId);
    }

    // Danh sách theo group
    @GetMapping("/by-group/{groupId}")
    public List<OwnershipShareResponse> listByGroup(@PathVariable Long groupId) {
        return service.listByGroup(groupId);
    }

    // Danh sách theo user
    @GetMapping("/by-user/{userId}")
    public List<OwnershipShareResponse> listByUser(@PathVariable Long userId) {
        return service.listByUser(userId);
    }

    // Xoá member (chỉ khi Pending)
    @DeleteMapping("/{groupId}/{userId}")
    public void remove(@PathVariable Long groupId, @PathVariable Long userId) {
        service.removeMember(groupId, userId);
    }
}

