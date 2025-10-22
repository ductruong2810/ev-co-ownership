package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.VehicleCreateRequest;
import com.group8.evcoownership.dto.VehicleResponse;
import com.group8.evcoownership.dto.VehicleUpdateRequest;
import com.group8.evcoownership.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Quản lý phương tiện")
public class VehicleController {

    private final VehicleService service;

    @PostMapping
    @Operation(summary = "Tạo phương tiện mới", description = "Tạo một phương tiện mới trong hệ thống")
    public VehicleResponse create(@RequestBody @Valid VehicleCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{vehicleId}")
    @Operation(summary = "Cập nhật phương tiện", description = "Cập nhật thông tin của một phương tiện theo ID")
    public VehicleResponse update(@PathVariable Long vehicleId,
                                  @RequestBody @Valid VehicleUpdateRequest req) {
        return service.update(vehicleId, req);
    }

    @GetMapping("/{vehicleId}")
    @Operation(summary = "Lấy thông tin phương tiện", description = "Lấy thông tin chi tiết của một phương tiện theo ID")
    public VehicleResponse getById(@PathVariable Long vehicleId) {
        return service.getById(vehicleId);
    }

    @GetMapping
    @Operation(summary = "Danh sách phương tiện theo nhóm", description = "Lấy danh sách phương tiện thuộc một nhóm cụ thể")
    public Page<VehicleResponse> listByGroup(@RequestParam Long groupId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.listByGroup(groupId, PageRequest.of(page, size));
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Xóa phương tiện", description = "Xóa một phương tiện khỏi hệ thống")
    public void delete(@PathVariable Long vehicleId) {
        service.delete(vehicleId);
    }
}

