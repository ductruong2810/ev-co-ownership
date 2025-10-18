package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.OwnershipGroupCreateRequest;
import com.group8.evcoownership.dto.OwnershipGroupResponse;
import com.group8.evcoownership.dto.OwnershipGroupStatusUpdateRequest;
import com.group8.evcoownership.dto.OwnershipGroupUpdateRequest;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.service.OwnershipGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class OwnershipGroupController {

    private final OwnershipGroupService service;

    @PostMapping
    public OwnershipGroupResponse create(@RequestBody @Valid OwnershipGroupCreateRequest req) {
        return service.create(req);
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

    @GetMapping
    public Page<OwnershipGroupResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GroupStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(keyword, status, fromDate, toDate, PageRequest.of(page, size));
    }

    @DeleteMapping("/{groupId}")
    public void delete(@PathVariable Long groupId) {
        service.delete(groupId);
    }
}

