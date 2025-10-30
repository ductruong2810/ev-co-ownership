package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.VehicleImageResponseDTO;
import com.group8.evcoownership.service.VehicleImageApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Vehicle Images", description = "Xem hình ảnh phương tiện")
public class VehicleImageController {

    private final VehicleImageApprovalService approvalService;

    @GetMapping("/groups/{groupId}")
    @Operation(summary = "Hình ảnh theo nhóm", description = "Lấy danh sách hình ảnh phương tiện của một nhóm")
    public ResponseEntity<List<VehicleImageResponseDTO>> getImagesByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(approvalService.getImagesByGroupId(groupId));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Hình ảnh theo phương tiện", description = "Lấy danh sách hình ảnh của một phương tiện cụ thể")
    public ResponseEntity<List<VehicleImageResponseDTO>> getImagesByVehicleId(@PathVariable Long vehicleId) {
        return ResponseEntity.ok(approvalService.getImagesByVehicleId(vehicleId));
    }
}
