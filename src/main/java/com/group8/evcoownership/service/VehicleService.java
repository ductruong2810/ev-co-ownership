package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.VehicleCreateRequest;
import com.group8.evcoownership.dto.VehicleResponse;
import com.group8.evcoownership.dto.VehicleUpdateRequest;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.entity.VehicleImage;
import com.group8.evcoownership.enums.ImageApprovalStatus;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.VehicleImageRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepo;
    private final OwnershipGroupRepository groupRepo;
    private final VehicleImageRepository vehicleImageRepository;
    private final AzureBlobStorageService azureBlobStorageService;

    private VehicleResponse toDto(Vehicle v) {
        return new VehicleResponse(
                v.getId(), v.getBrand(), v.getModel(),
                v.getLicensePlate(), v.getChassisNumber(), v.getQrCode(),
                v.getOwnershipGroup().getGroupId(), v.getVehicleValue(), v.getCreatedAt(), v.getUpdatedAt()
        );
    }

    @Transactional
    public VehicleResponse create(VehicleCreateRequest req) {
        OwnershipGroup group = groupRepo.findById(req.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        if (vehicleRepo.existsByOwnershipGroup_GroupId(req.groupId()))
            throw new IllegalStateException("Group already has a vehicle");
        if (vehicleRepo.existsByLicensePlateIgnoreCase(req.licensePlate()))
            throw new IllegalStateException("License plate already exists");
        if (vehicleRepo.existsByChassisNumberIgnoreCase(req.chassisNumber()))
            throw new IllegalStateException("Chassis number already exists");

        var v = Vehicle.builder()
                .brand(req.brand())
                .model(req.model())
                .licensePlate(req.licensePlate())
                .chassisNumber(req.chassisNumber())
                .vehicleValue(req.vehicleValue())
                .ownershipGroup(group)
                .build();

        // Lưu lần 1 để có VehicleId
        v = vehicleRepo.save(v);

        // Tự sinh QR, KHÔNG lấy từ client
        v.setQrCode(buildQrPayload(v.getId()));

        // Lưu lần 2 sau khi set QR
        v = vehicleRepo.save(v);

        return toDto(v);
    }

    private String buildQrPayload(Long vehicleId) {
        // QR chứa Group ID để checkin đơn giản (1 group = 1 xe)
        Vehicle vehicle = vehicleRepo.findById(vehicleId).orElse(null);
        if (vehicle != null && vehicle.getOwnershipGroup() != null) {
            return "GROUP:" + vehicle.getOwnershipGroup().getGroupId();
        }
        return "VEHICLE:" + vehicleId; // fallback nếu không có group
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
        v.setVehicleValue(req.vehicleValue());

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

    // ======== Upload Multiple Vehicle Images ==============
    @Transactional
    public Map<String, Object> uploadMultipleVehicleImages(Long vehicleId, MultipartFile[] images, String[] imageTypes) {
        Map<String, Object> uploadedImages = new HashMap<>();

        // Validation: Số lượng images
        if (images.length != imageTypes.length) {
            throw new IllegalArgumentException("Number of images must match number of image types");
        }

        // Validation: Giới hạn số lượng images
        if (images.length > 10) {
            throw new IllegalArgumentException("Maximum 10 images allowed per vehicle");
        }

        // Validation: Image types hợp lệ
        String[] validImageTypes = {"VEHICLE", "FRONT", "BACK", "LEFT", "RIGHT", "INTERIOR", "ENGINE", "LICENSE", "REGISTRATION"};
        for (String imageType : imageTypes) {
            if (!Arrays.asList(validImageTypes).contains(imageType)) {
                throw new IllegalArgumentException("Invalid image type: " + imageType + ". Valid types: " + Arrays.toString(validImageTypes));
            }
        }

        // Validation: Kích thước file
        long maxFileSize = 10 * 1024 * 1024; // 10MB
        for (MultipartFile image : images) {
            if (image.getSize() > maxFileSize) {
                throw new IllegalArgumentException("File size exceeds 10MB limit: " + image.getOriginalFilename());
            }
            if (image.isEmpty()) {
                throw new IllegalArgumentException("Empty file not allowed: " + image.getOriginalFilename());
            }
        }

        try {
            Vehicle vehicle = new Vehicle();
            vehicle.setId(vehicleId);

            // Map để nhóm images theo type
            Map<String, List<String>> typeImages = new HashMap<>();

            for (int i = 0; i < images.length; i++) {
                String imageUrl = azureBlobStorageService.uploadFile(images[i]);
                String imageType = imageTypes[i];

                VehicleImage vehicleImg = VehicleImage.builder()
                        .imageUrl(imageUrl)
                        .imageType(imageType)
                        .approvalStatus(ImageApprovalStatus.PENDING)
                        .build();
                vehicleImg.setVehicle(vehicle);
                vehicleImageRepository.save(vehicleImg);

                // Nhóm images theo type
                typeImages.computeIfAbsent(imageType, k -> new ArrayList<>()).add(imageUrl);
            }

            // Convert sang format response
            for (Map.Entry<String, List<String>> entry : typeImages.entrySet()) {
                String imageType = entry.getKey();
                List<String> urls = entry.getValue();

                // Nếu chỉ có 1 image, trả về string
                // Nếu có nhiều images, trả về array
                if (urls.size() == 1) {
                    uploadedImages.put(imageType, urls.get(0));
                } else {
                    uploadedImages.put(imageType, urls.toArray(new String[0]));
                }
            }

        } catch (Exception e) {
            // Cleanup uploaded files if any
            uploadedImages.values().forEach(url -> {
                try {
                    if (url instanceof String) {
                        azureBlobStorageService.deleteFile((String) url);
                    } else if (url instanceof String[]) {
                        for (String singleUrl : (String[]) url) {
                            azureBlobStorageService.deleteFile(singleUrl);
                        }
                    }
                } catch (Exception cleanupError) {
                    // Log cleanup error but don't throw
                }
            });
            throw new RuntimeException("Failed to upload multiple vehicle images: " + e.getMessage(), e);
        }

        return uploadedImages;
    }
}

