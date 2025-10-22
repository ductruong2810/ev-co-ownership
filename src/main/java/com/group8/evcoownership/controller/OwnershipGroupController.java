package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.service.OwnershipGroupService;
import io.swagger.v3.oas.annotations.Operation;
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
public class OwnershipGroupController {

    private final OwnershipGroupService service;

    @PostMapping
    public OwnershipGroupResponse create(@RequestBody @Valid OwnershipGroupCreateRequest req,
                                         @AuthenticationPrincipal String userEmail) {
        return service.create(req, userEmail);
    }

    @PostMapping("/with-vehicle")
    @Operation(summary = "Create group with vehicle and multiple images")
    public GroupWithVehicleResponse createGroupWithVehicle(
            CreateGroupWithVehicleRequest request,
            @AuthenticationPrincipal String userEmail) {

        return service.createGroupWithVehicle(
                request.groupName(), request.description(), request.memberCapacity(),
                request.vehicleValue(), request.licensePlate(), request.chassisNumber(),
                request.vehicleImages(), request.imageTypes(), userEmail);
    }

    @PutMapping("/{groupId}")
    public OwnershipGroupResponse updateByUser(@PathVariable Long groupId,
                                               @RequestBody @Valid OwnershipGroupUpdateRequest req) {
        return service.updateByUser(groupId, req);
    }

    @PatchMapping("/{groupId}/status")
    public OwnershipGroupResponse updateStatus(@PathVariable Long groupId,
                                               @RequestBody @Valid OwnershipGroupStatusUpdateRequest req) {
        return service.updateStatus(groupId, req);
    }

    @GetMapping("/{groupId}")
    public OwnershipGroupResponse getById(@PathVariable Long groupId) {
        return service.getById(groupId);
    }

    /**
     * Lấy danh sách tất cả groups trong hệ thống (chỉ dành cho Staff và Admin)
     */
    @GetMapping("/staff/all")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    public Page<OwnershipGroupResponse> listAllGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GroupStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(keyword, status, fromDate, toDate, PageRequest.of(page, size));
    }

    /**
     * Lấy tất cả groups mà user hiện tại đã tạo và tham gia
     */
    @GetMapping("/my-groups")
    public List<OwnershipGroupResponse> getMyGroups(@AuthenticationPrincipal String userEmail) {
        return service.getGroupsByUser(userEmail);
    }

    @DeleteMapping("/{groupId}")
    public void delete(@PathVariable Long groupId) {
        service.delete(groupId);
    }
}

