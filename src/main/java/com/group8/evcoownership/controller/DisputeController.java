package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DisputeCreateRequest;
import com.group8.evcoownership.dto.DisputeResponse;
import com.group8.evcoownership.dto.DisputeStaffUpdateRequest;
import com.group8.evcoownership.dto.DisputeStatusUpdateRequest;
import com.group8.evcoownership.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService service;

    @PostMapping
    public ResponseEntity<DisputeResponse> create(@RequestBody @Valid DisputeCreateRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<Page<DisputeResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long fundId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(service.list(page, size, fundId, status));
    }

    @PatchMapping("/{id}/staff")
    public ResponseEntity<DisputeResponse> staffUpdate(@PathVariable Long id,
                                                       @RequestBody @Valid DisputeStaffUpdateRequest req,
                                                       @RequestHeader("X-UserId") Long staffUserId) {
        return ResponseEntity.ok(service.staffUpdate(id, req, staffUserId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<DisputeResponse> updateStatus(@PathVariable Long id,
                                                        @RequestBody @Valid DisputeStatusUpdateRequest req,
                                                        @RequestHeader("X-UserId") Long staffUserId) {
        return ResponseEntity.ok(service.updateStatus(id, req, staffUserId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

