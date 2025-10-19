package com.group8.evcoownership.service;



import com.group8.evcoownership.dto.VehicleCreateRequest;
import com.group8.evcoownership.dto.VehicleResponse;
import com.group8.evcoownership.dto.VehicleUpdateRequest;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepo;
    private final OwnershipGroupRepository groupRepo;

    private VehicleResponse toDto(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getBrand(), v.getModel(),
                v.getLicensePlate(), v.getChassisNumber(), v.getQrCode(),
                v.getOwnershipGroup().getGroupId(), v.getCreatedAt(), v.getUpdatedAt()
        );
    }

    @Transactional
    public VehicleResponse create(VehicleCreateRequest req) {
        OwnershipGroup group = groupRepo.findById(req.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        if (vehicleRepo.existsByLicensePlateIgnoreCase(req.licensePlate()))
            throw new IllegalStateException("License plate already exists");
        if (vehicleRepo.existsByChassisNumberIgnoreCase(req.chassisNumber()))
            throw new IllegalStateException("Chassis number already exists");

        var v = Vehicle.builder()
                .brand(req.brand())
                .model(req.model())
                .licensePlate(req.licensePlate())
                .chassisNumber(req.chassisNumber())
                .ownershipGroup(group)
                .build();

        // Lưu lần 1 để có VehicleId
        v = vehicleRepo.save(v);

        // Tự sinh QR, KHÔNG lấy từ client
        v.setQrCode(buildQrPayload(v.getId(), v.getLicensePlate()));

        // Lưu lần 2 sau khi set QR
        v = vehicleRepo.save(v);

        return toDto(v);
    }

    private String buildQrPayload(Long vehicleId, String licensePlate) {
        return "EVCO:V" + vehicleId + ":" + licensePlate; // tuỳ bạn định dạng
    }

    // ======= Updte ============
    @Transactional
    public VehicleResponse update(Long vehicleId, VehicleUpdateRequest req) {
        var v = vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        if (!v.getLicensePlate().equalsIgnoreCase(req.licensePlate())
                && vehicleRepo.existsByLicensePlateIgnoreCase(req.licensePlate()))
            throw new IllegalStateException("License plate already exists");
        if (!v.getChassisNumber().equalsIgnoreCase(req.chassisNumber())
                && vehicleRepo.existsByChassisNumberIgnoreCase(req.chassisNumber()))
            throw new IllegalStateException("Chassis number already exists");

        v.setBrand(req.brand());
        v.setModel(req.model());
        v.setLicensePlate(req.licensePlate());
        v.setChassisNumber(req.chassisNumber());

        // KHÔNG thay đổi QR ở update
        return toDto(vehicleRepo.save(v));
    }




    // ======== Get ==============
    public VehicleResponse getById(Long vehicleId) {
        return vehicleRepo.findById(vehicleId)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));
    }

    public Page<VehicleResponse> listByGroup(Long groupId, Pageable pageable) {
        if (!groupRepo.existsById(groupId))
            throw new EntityNotFoundException("Group not found");
        return vehicleRepo.findByOwnershipGroupGroupId(groupId, pageable).map(this::toDto);
    }

    @Transactional
    public void delete(Long vehicleId) {
        var v = vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));
        // (tuỳ rule) có thể chặn xoá nếu group ACTIVE/đã có booking/incident...
        vehicleRepo.delete(v);
    }
}

