package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.CreateGroupWithVehicleRequest;
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
            CreateGroupWithVehicleRequest request,
            Authentication authentication) {
        
        String userEmail = authentication.getName();
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
    
    // ---- Validation methods ----
    
    private void validateCreateGroupWithVehicleRequest(String groupName, String description, 
            Integer memberCapacity, java.math.BigDecimal vehicleValue, String licensePlate, 
            String chassisNumber, MultipartFile[] vehicleImages, String[] imageTypes) {
        
        // Validation: Group fields
        if (groupName == null || groupName.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name is required");
        }
        if (groupName.length() > 100) {
            throw new IllegalArgumentException("Group name must not exceed 100 characters");
        }
        
        if (description != null && description.length() > 4000) {
            throw new IllegalArgumentException("Description must not exceed 4000 characters");
        }
        
        if (memberCapacity == null || memberCapacity <= 0) {
            throw new IllegalArgumentException("Member capacity must be a positive number");
        }
        if (memberCapacity > 50) {
            throw new IllegalArgumentException("Member capacity cannot exceed 50");
        }
        
        // Validation: Vehicle fields
        if (vehicleValue == null || vehicleValue.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Vehicle value must be a positive number");
        }
        if (vehicleValue.compareTo(new java.math.BigDecimal("10000000000")) > 0) {
            throw new IllegalArgumentException("Vehicle value cannot exceed 10 billion VND");
        }
        
        if (licensePlate == null || licensePlate.trim().isEmpty()) {
            throw new IllegalArgumentException("License plate is required");
        }
        if (licensePlate.length() > 20) {
            throw new IllegalArgumentException("License plate must not exceed 20 characters");
        }
        
        if (chassisNumber == null || chassisNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Chassis number is required");
        }
        if (chassisNumber.length() > 50) {
            throw new IllegalArgumentException("Chassis number must not exceed 50 characters");
        }
        
        // Validation: Images
        if (vehicleImages.length == 0) {
            throw new IllegalArgumentException("At least one image is required");
        }
        
        if (vehicleImages.length > 10) {
            throw new IllegalArgumentException("Maximum 10 images allowed");
        }
        
        if (vehicleImages.length != imageTypes.length) {
            throw new IllegalArgumentException("Number of images must match number of image types");
        }
    }
}

