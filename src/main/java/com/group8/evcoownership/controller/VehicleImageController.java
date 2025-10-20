package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.VehicleImageResponse;
import com.group8.evcoownership.service.VehicleImageApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-images")
@RequiredArgsConstructor
public class VehicleImageController {

    private final VehicleImageApprovalService approvalService;

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<List<VehicleImageResponse>> getImagesByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getImagesByGroupId(groupId));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<List<VehicleImageResponse>> getImagesByVehicleId(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(approvalService.getImagesByVehicleId(vehicleId));
    }
}
