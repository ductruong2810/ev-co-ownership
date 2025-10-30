package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.dto.ImageCountsDTO;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.entity.VehicleImage;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.enums.ImageApprovalStatus;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleImageRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleImageApprovalService {

    private final VehicleImageRepository vehicleImageRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final OwnershipGroupRepository ownershipGroupRepository;

    /**
     * Lấy danh sách hình ảnh chờ duyệt
     */
    public Page<VehicleImageResponse> getPendingImages(Pageable pageable) {
        return vehicleImageRepository.findByApprovalStatus(ImageApprovalStatus.PENDING, pageable)
                .map(this::toResponse);
    }

    /**
     * Lấy thông tin đầy đủ của xe và hình ảnh theo group ID
     */
    public VehicleWithImagesResponse getVehicleWithImagesByGroupId(Long groupId) {
        // Lấy vehicle của group
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found for group"));

        // Lấy tất cả hình ảnh của vehicle
        List<VehicleImage> images = vehicleImageRepository.findByVehicleId(vehicle.getId());
        List<VehicleImageResponse> imageResponses = mapToResponses(images);
        ImageCountsDTO counts = computeCounts(images);

        return VehicleWithImagesResponse.builder()
                // Vehicle information
                .vehicleId(vehicle.getId())
                .brand(vehicle.getBrand())
                .model(vehicle.getModel())
                .licensePlate(vehicle.getLicensePlate())
                .chassisNumber(vehicle.getChassisNumber())
                .qrCode(vehicle.getQrCode())
                .vehicleValue(vehicle.getVehicleValue())
                .vehicleCreatedAt(vehicle.getCreatedAt())
                .vehicleUpdatedAt(vehicle.getUpdatedAt())

                // Group information
                .groupId(vehicle.getOwnershipGroup().getGroupId())
                .groupName(vehicle.getOwnershipGroup().getGroupName())

                // Images information
                .images(imageResponses)

                // Summary
                .totalImages(images.size())
                .pendingImages((int) counts.getPending())
                .approvedImages((int) counts.getApproved())
                .rejectedImages((int) counts.getRejected())
                .build();
    }

    /**
     * Lấy danh sách hình ảnh của một group (backward compatibility)
     */
    public List<VehicleImageResponse> getImagesByGroupId(Long groupId) {
        return mapToResponses(vehicleImageRepository.findByVehicle_OwnershipGroup_GroupId(groupId));
    }

    /**
     * Lấy danh sách hình ảnh theo vehicle ID (backward compatibility)
     */
    public List<VehicleImageResponse> getImagesByVehicleId(Long vehicleId) {
        return mapToResponses(vehicleImageRepository.findByVehicleId(vehicleId));
    }

    /**
     * Lấy danh sách group có hình ảnh chờ duyệt
     */
    public List<GroupImageApprovalSummary> getGroupsWithPendingImages() {
        return vehicleImageRepository.findGroupsWithPendingImages()
                .stream()
                .map(this::toGroupSummary)
                .collect(Collectors.toList());
    }

    /**
     * Duyệt tất cả hình ảnh của một group
     */
    @Transactional
    public GroupApprovalResult approveGroupImages(Long groupId, VehicleImageApprovalRequest request, String staffEmail) {
        // Kiểm tra quyền staff
        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("Staff not found"));
        assertStaffPermissions(staff);

        // Lấy tất cả hình ảnh của group
        List<VehicleImage> groupImages = vehicleImageRepository.findByVehicle_OwnershipGroup_GroupId(groupId);

        if (groupImages.isEmpty()) {
            throw new EntityNotFoundException("No images found for this group");
        }

        // Cập nhật trạng thái tất cả hình ảnh
        LocalDateTime now = LocalDateTime.now();
        for (VehicleImage image : groupImages) {
            image.setApprovalStatus(request.status());
            image.setApprovedBy(staff);
            image.setApprovedAt(now);

            if (request.status() == ImageApprovalStatus.REJECTED) {
                image.setRejectionReason(request.rejectionReason());
            } else {
                image.setRejectionReason(null);
            }
        }

        vehicleImageRepository.saveAll(groupImages);

        // Cập nhật trạng thái group dựa trên kết quả duyệt
        OwnershipGroup group = ownershipGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        if (request.status() == ImageApprovalStatus.APPROVED) {
            // Nếu tất cả hình được duyệt, chuyển group thành ACTIVE
            if (group.getStatus() == GroupStatus.PENDING) {
                group.setStatus(GroupStatus.ACTIVE);
                group.setRejectionReason(null); // Xóa lý do từ chối khi duyệt
                ownershipGroupRepository.save(group);
                logGroupStatusUpdate(groupId, GroupStatus.ACTIVE, null);
            }
        } else if (request.status() == ImageApprovalStatus.REJECTED) {
            // Nếu có hình bị từ chối, chuyển group thành INACTIVE và lưu lý do từ chối
            if (group.getStatus() == GroupStatus.PENDING) {
                group.setStatus(GroupStatus.INACTIVE);
                group.setRejectionReason(request.rejectionReason()); // Lưu lý do từ chối hình ảnh làm lý do INACTIVE của group
                ownershipGroupRepository.save(group);
                logGroupStatusUpdate(groupId, GroupStatus.INACTIVE, request.rejectionReason());
            }
        }

        return GroupApprovalResult.builder()
                .groupId(groupId)
                .totalImages(groupImages.size())
                .approvedImages(request.status() == ImageApprovalStatus.APPROVED ? groupImages.size() : 0)
                .rejectedImages(request.status() == ImageApprovalStatus.REJECTED ? groupImages.size() : 0)
                .groupStatus(group.getStatus())
                .build();
    }

    /**
     * Staff duyệt/từ chối hình ảnh
     */
    @Transactional
    public VehicleImageResponse approveImage(Long imageId, VehicleImageApprovalRequest request, String staffEmail) {
        // Kiểm tra quyền staff
        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("Staff not found"));
        assertStaffPermissions(staff);

        VehicleImage image = vehicleImageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Image not found"));

        // Cập nhật trạng thái duyệt
        image.setApprovalStatus(request.status());
        image.setApprovedBy(staff);
        image.setApprovedAt(LocalDateTime.now());

        if (request.status() == ImageApprovalStatus.REJECTED) {
            image.setRejectionReason(request.rejectionReason());
        } else {
            image.setRejectionReason(null);
        }

        VehicleImage savedImage = vehicleImageRepository.save(image);

        // Kiểm tra xem tất cả hình ảnh của vehicle đã được duyệt chưa
        checkAndUpdateGroupStatus(image.getVehicle().getId());

        return toResponse(savedImage);
    }

    /**
     * Kiểm tra và cập nhật trạng thái group dựa trên trạng thái hình ảnh
     */
    @Transactional
    public void checkAndUpdateGroupStatus(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        if (vehicle.getOwnershipGroup() == null) {
            return;
        }

        Long groupId = vehicle.getOwnershipGroup().getGroupId();

        // Đếm tổng số hình ảnh và số hình ảnh theo từng trạng thái
        long totalImages = vehicleImageRepository.countByVehicleId(vehicleId);
        long approvedImages = vehicleImageRepository.countByVehicleIdAndApprovalStatus(vehicleId, ImageApprovalStatus.APPROVED);
        long rejectedImages = vehicleImageRepository.countByVehicleIdAndApprovalStatus(vehicleId, ImageApprovalStatus.REJECTED);

        OwnershipGroup group = ownershipGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Nếu tất cả hình ảnh đã được duyệt và group đang ở trạng thái PENDING
        if (totalImages > 0 && totalImages == approvedImages && group.getStatus() == GroupStatus.PENDING) {
            group.setStatus(GroupStatus.ACTIVE);
            group.setRejectionReason(null); // Xóa lý do từ chối khi duyệt
            ownershipGroupRepository.save(group);
            logGroupStatusUpdate(groupId, GroupStatus.ACTIVE, null);
        }
        // Nếu có hình ảnh bị từ chối và group đang ở trạng thái PENDING
        else if (rejectedImages > 0 && group.getStatus() == GroupStatus.PENDING) {
            group.setStatus(GroupStatus.INACTIVE);

            // Lấy lý do từ chối từ hình ảnh bị từ chối đầu tiên
            List<VehicleImage> rejectedImagesList = vehicleImageRepository.findByVehicleIdAndApprovalStatus(vehicleId, ImageApprovalStatus.REJECTED);
            String rejectionReason = null;
            if (!rejectedImagesList.isEmpty()) {
                rejectionReason = rejectedImagesList.get(0).getRejectionReason();
                group.setRejectionReason(rejectionReason);
            }
            logGroupStatusUpdate(groupId, GroupStatus.INACTIVE, rejectionReason);

            ownershipGroupRepository.save(group);
        }
    }

    /**
     * Lấy thống kê hình ảnh theo trạng thái
     */
    public long getImageCountByStatus(ImageApprovalStatus status) {
        return vehicleImageRepository.findByApprovalStatus(status).size();
    }

    /**
     * Convert entity to response DTO
     */
    private VehicleImageResponse toResponse(VehicleImage image) {
        return VehicleImageResponse.builder()
                .imageId(image.getImageId())
                .vehicleId(image.getVehicle().getId())
                .imageUrl(image.getImageUrl())
                .imageType(image.getImageType())
                .approvalStatus(image.getApprovalStatus())
                .approvedByName(image.getApprovedBy() != null ? image.getApprovedBy().getFullName() : null)
                .approvedAt(image.getApprovedAt())
                .rejectionReason(image.getRejectionReason())
                .uploadedAt(image.getUploadedAt())
                .build();
    }

    /**
     * Convert OwnershipGroup to GroupImageApprovalSummary
     */
    private GroupImageApprovalSummary toGroupSummary(OwnershipGroup group) {
        List<VehicleImage> groupImages = vehicleImageRepository.findByVehicle_OwnershipGroup_GroupId(group.getGroupId());
        ImageCountsDTO counts = computeCounts(groupImages);

        return GroupImageApprovalSummary.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .totalImages(groupImages.size())
                .pendingImages((int) counts.getPending())
                .approvedImages((int) counts.getApproved())
                .rejectedImages((int) counts.getRejected())
                .groupStatus(group.getStatus())
                .build();
    }

    private void logGroupStatusUpdate(Long groupId, GroupStatus status, String reason) {
        if (reason == null || reason.isBlank()) {
            log.info("Group {} status updated to {}", groupId, status);
        } else {
            log.info("Group {} status updated to {}. Reason: {}", groupId, status, reason);
        }
    }

    private void assertStaffPermissions(User staff) {
        if (staff.getRole().getRoleName() != RoleName.STAFF && staff.getRole().getRoleName() != RoleName.ADMIN) {
            throw new IllegalStateException("Only staff can approve images");
        }
    }

    private List<VehicleImageResponse> mapToResponses(List<VehicleImage> images) {
        return images.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private ImageCountsDTO computeCounts(List<VehicleImage> images) {
        long pending = images.stream().filter(img -> img.getApprovalStatus() == ImageApprovalStatus.PENDING).count();
        long approved = images.stream().filter(img -> img.getApprovalStatus() == ImageApprovalStatus.APPROVED).count();
        long rejected = images.stream().filter(img -> img.getApprovalStatus() == ImageApprovalStatus.REJECTED).count();
        return ImageCountsDTO.builder().pending(pending).approved(approved).rejected(rejected).build();
    }
}
