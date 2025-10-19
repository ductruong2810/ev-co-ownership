package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.GroupWithVehicleResponse;
import com.group8.evcoownership.dto.OwnershipGroupCreateRequest;
import com.group8.evcoownership.dto.OwnershipGroupResponse;
import com.group8.evcoownership.dto.OwnershipGroupStatusUpdateRequest;
import com.group8.evcoownership.dto.OwnershipGroupUpdateRequest;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.service.OwnershipGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class OwnershipGroupController {

    private final OwnershipGroupService service;

    @PostMapping
    public OwnershipGroupResponse create(@RequestBody @Valid OwnershipGroupCreateRequest req, 
                                        Authentication authentication) {
        String userEmail = authentication.getName();
        return service.create(req, userEmail);
    }

    @PostMapping("/with-vehicle")
    @Operation(summary = "Create group with vehicle and multiple images")
    public GroupWithVehicleResponse createGroupWithVehicle(
            @Parameter(description = "Group name", required = true)
            @RequestParam("groupName") String groupName,
            
            @Parameter(description = "Group description")
            @RequestParam(value = "description", required = false) String description,
            
            @Parameter(description = "Maximum number of members", required = true)
            @RequestParam("memberCapacity") Integer memberCapacity,
            
            @Parameter(description = "Vehicle value in VND", required = true)
            @RequestParam("vehicleValue") java.math.BigDecimal vehicleValue,
            
            @Parameter(description = "Vehicle license plate", required = true)
            @RequestParam("licensePlate") String licensePlate,
            
            @Parameter(description = "Vehicle chassis number", required = true)
            @RequestParam("chassisNumber") String chassisNumber,
            
            @Parameter(description = "Vehicle images files", required = true, 
                      schema = @Schema(type = "array", format = "binary"))
            @RequestParam("vehicleImages") MultipartFile[] vehicleImages,
            
            @Parameter(description = "Image types corresponding to each image", required = true)
            @RequestParam("imageTypes") String[] imageTypes,
            
            Authentication authentication) {
        
        String userEmail = authentication.getName();
        return service.createGroupWithVehicle(
                groupName, description, memberCapacity, vehicleValue,
                licensePlate, chassisNumber, vehicleImages, imageTypes, userEmail);
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

