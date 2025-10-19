package com.group8.evcoownership.controller;


import com.group8.evcoownership.dto.VehicleCreateRequest;
import com.group8.evcoownership.dto.VehicleResponse;
import com.group8.evcoownership.dto.VehicleUpdateRequest;
import com.group8.evcoownership.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService service;

    @PostMapping
    public VehicleResponse create(@RequestBody @Valid VehicleCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{vehicleId}")
    public VehicleResponse update(@PathVariable Long vehicleId,
                                  @RequestBody @Valid VehicleUpdateRequest req) {
        return service.update(vehicleId, req);
    }

    @GetMapping("/{vehicleId}")
    public VehicleResponse getById(@PathVariable Long vehicleId) {
        return service.getById(vehicleId);
    }

    @GetMapping
    public Page<VehicleResponse> listByGroup(@RequestParam Long groupId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.listByGroup(groupId, PageRequest.of(page, size));
    }

    @DeleteMapping("/{vehicleId}")
    public void delete(@PathVariable Long vehicleId) {
        service.delete(vehicleId);
    }
}

