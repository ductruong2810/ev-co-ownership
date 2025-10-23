package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DisputeCreateRequest;
import com.group8.evcoownership.dto.DisputeResponse;
import com.group8.evcoownership.dto.DisputeStaffUpdateRequest;
import com.group8.evcoownership.dto.DisputeStatusUpdateRequest;
import com.group8.evcoownership.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Tag(name = "Disputes", description = "Quản lý tranh chấp và khiếu nại")
public class DisputeController {

    private final DisputeService service;

    @PostMapping
    @Operation(summary = "Tạo tranh chấp mới", description = "Tạo một tranh chấp mới trong hệ thống")
    public ResponseEntity<DisputeResponse> create(@RequestBody @Valid DisputeCreateRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết tranh chấp", description = "Lấy thông tin chi tiết của một tranh chấp theo ID")
    public ResponseEntity<DisputeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "Danh sách tranh chấp", description = "Lấy danh sách tranh chấp với khả năng lọc và phân trang")
    public ResponseEntity<Page<DisputeResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long fundId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(service.list(page, size, fundId, status));
    }

    @PatchMapping("/{id}/staff")
    @Operation(summary = "Cập nhật bởi nhân viên", description = "Nhân viên cập nhật thông tin tranh chấp")
    public ResponseEntity<DisputeResponse> staffUpdate(@PathVariable Long id,
                                                       @RequestBody @Valid DisputeStaffUpdateRequest req,
                                                       @RequestHeader("X-UserId") Long staffUserId) {
        return ResponseEntity.ok(service.staffUpdate(id, req, staffUserId));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái", description = "Cập nhật trạng thái của tranh chấp")
    public ResponseEntity<DisputeResponse> updateStatus(@PathVariable Long id,
                                                        @RequestBody @Valid DisputeStatusUpdateRequest req,
                                                        @RequestHeader("X-UserId") Long staffUserId) {
        return ResponseEntity.ok(service.updateStatus(id, req, staffUserId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tranh chấp", description = "Xóa một tranh chấp khỏi hệ thống")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

