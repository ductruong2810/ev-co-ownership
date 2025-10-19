package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.GroupWithVehicleResponse;
import com.group8.evcoownership.dto.OwnershipGroupCreateRequest;
import com.group8.evcoownership.dto.OwnershipGroupResponse;
import com.group8.evcoownership.dto.OwnershipGroupStatusUpdateRequest;
import com.group8.evcoownership.dto.OwnershipGroupUpdateRequest;
import com.group8.evcoownership.dto.VehicleCreateRequest;
import com.group8.evcoownership.dto.VehicleResponse;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.exception.InsufficientDocumentsException;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OwnershipGroupService {

    private final OwnershipGroupRepository repo;
    private final OwnershipShareRepository ownershipShareRepository;
    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final VehicleService vehicleService;

    // ---- mapping ----
    private OwnershipGroupResponse toDto(OwnershipGroup e) {
        return new OwnershipGroupResponse(
                e.getGroupId(),
                e.getGroupName(),
                e.getDescription(),
                e.getMemberCapacity(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private void applyMutableFields(OwnershipGroup e, String name, String desc, Integer capacity) {
        e.setGroupName(name);
        e.setDescription(desc);
        e.setMemberCapacity(capacity);
    }

    // ---- validation methods ----
    
    /**
     * Kiểm tra user có đầy đủ giấy tờ cần thiết để tạo group không
     * Cần có: CCCD (cả mặt trước và sau) và GPLX (cả mặt trước và sau) với status APPROVED
     */
    private void validateRequiredDocuments(Long userId) {
        // Kiểm tra CCCD - cả mặt trước và sau
        boolean hasCitizenIdFront = userDocumentRepository.existsByUserIdAndDocumentTypeAndSide(
                userId, "CITIZEN_ID", "FRONT");
        boolean hasCitizenIdBack = userDocumentRepository.existsByUserIdAndDocumentTypeAndSide(
                userId, "CITIZEN_ID", "BACK");
        
        // Kiểm tra GPLX - cả mặt trước và sau  
        boolean hasDriverLicenseFront = userDocumentRepository.existsByUserIdAndDocumentTypeAndSide(
                userId, "DRIVER_LICENSE", "FRONT");
        boolean hasDriverLicenseBack = userDocumentRepository.existsByUserIdAndDocumentTypeAndSide(
                userId, "DRIVER_LICENSE", "BACK");
        
        // Kiểm tra status APPROVED cho tất cả documents
        boolean citizenIdApproved = checkDocumentStatus(userId, "CITIZEN_ID");
        boolean driverLicenseApproved = checkDocumentStatus(userId, "DRIVER_LICENSE");
        
        StringBuilder missingDocs = new StringBuilder();
        
        if (!hasCitizenIdFront || !hasCitizenIdBack) {
            missingDocs.append("CCCD (cả mặt trước và sau), ");
        } else if (!citizenIdApproved) {
            missingDocs.append("CCCD chưa được duyệt, ");
        }
        
        if (!hasDriverLicenseFront || !hasDriverLicenseBack) {
            missingDocs.append("GPLX (cả mặt trước và sau), ");
        } else if (!driverLicenseApproved) {
            missingDocs.append("GPLX chưa được duyệt, ");
        }
        
        if (!missingDocs.isEmpty()) {
            // Xóa dấu phẩy cuối
            String missing = missingDocs.toString().replaceAll(", $", "");
            throw new InsufficientDocumentsException(
                    "Không thể tạo group. Bạn cần upload và được duyệt: " + missing);
        }
    }
    
    /**
     * Kiểm tra tất cả documents của một loại có status APPROVED không
     */
    private boolean checkDocumentStatus(Long userId, String documentType) {
        var documents = userDocumentRepository.findByUserIdAndDocumentType(userId, documentType);
        return documents.stream().allMatch(doc -> "APPROVED".equals(doc.getStatus()));
    }

    // ---- CRUD ----
    @Transactional
    public OwnershipGroupResponse create(OwnershipGroupCreateRequest req, String userEmail) {
        if (repo.existsByGroupNameIgnoreCase(req.groupName())) {
            throw new IllegalStateException("GroupName already exists");
        }
        
        // Tìm user từ email
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));
        
        // Kiểm tra giấy tờ cần thiết
        validateRequiredDocuments(user.getUserId());
        
        // Tạo group
        var entity = OwnershipGroup.builder()
                .groupName(req.groupName())
                .description(req.description())
                .memberCapacity(req.memberCapacity())
                .build(); // status = PENDING at @PrePersist
        
        var savedGroup = repo.save(entity);
        
        // Tự động thêm người tạo group làm ADMIN với 100% ownership
        var shareId = new OwnershipShareId(user.getUserId(), savedGroup.getGroupId());
        var ownershipShare = OwnershipShare.builder()
                .id(shareId)
                .user(user)
                .group(savedGroup)
                .groupRole(GroupRole.ADMIN)
                .ownershipPercentage(java.math.BigDecimal.valueOf(100.00))
                .joinDate(LocalDateTime.now())
                .build();
        
        ownershipShareRepository.save(ownershipShare);
        
        return toDto(savedGroup);
    }

    @Transactional
    public GroupWithVehicleResponse createGroupWithVehicle(
            String groupName, String description, Integer memberCapacity,
            java.math.BigDecimal vehicleValue, String licensePlate, String chassisNumber,
            MultipartFile[] vehicleImages, String[] imageTypes, String userEmail) {
        
        try {
            // Step 1: Create ownership group với userEmail
            OwnershipGroupCreateRequest groupRequest = new OwnershipGroupCreateRequest(
                    groupName, description, memberCapacity);
            OwnershipGroupResponse groupResponse = create(groupRequest, userEmail);

            // Step 2: Create vehicle using VehicleService
            VehicleCreateRequest vehicleRequest = new VehicleCreateRequest(
                    "Unknown", "Unknown", licensePlate, chassisNumber, vehicleValue, groupResponse.groupId());
            VehicleResponse vehicleResponse = vehicleService.create(vehicleRequest);

            // Step 3: Upload multiple vehicle images using VehicleService
            Map<String, Object> uploadedImages = vehicleService.uploadMultipleVehicleImages(
                    vehicleResponse.vehicleId(), vehicleImages, imageTypes);

            // Step 4: Return combined response
            return new GroupWithVehicleResponse(
                    groupResponse.groupId(),
                    groupResponse.groupName(),
                    groupResponse.description(),
                    groupResponse.memberCapacity(),
                    groupResponse.status(),
                    groupResponse.createdAt(),
                    groupResponse.updatedAt(),
                    vehicleResponse.vehicleId(),
                    vehicleResponse.brand(),
                    vehicleResponse.model(),
                    vehicleResponse.licensePlate(),
                    vehicleResponse.chassisNumber(),
                    vehicleResponse.qrCode(),
                    vehicleValue,
                    uploadedImages
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to create group with vehicle: " + e.getMessage(), e);
        }
    }

    @Transactional
    public OwnershipGroupResponse updateByUser(Long groupId, OwnershipGroupUpdateRequest req) {
        var e = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        if (e.getStatus() != GroupStatus.PENDING) {
            throw new AccessDeniedException("Only PENDING group can be updated by user");
        }
        if (!e.getGroupName().equalsIgnoreCase(req.groupName())
                && repo.existsByGroupNameIgnoreCase(req.groupName())) {
            throw new IllegalStateException("GroupName already exists");
        }

        applyMutableFields(e, req.groupName(), req.description(), req.memberCapacity());
        return toDto(repo.save(e));
    }

    @Transactional
    public OwnershipGroupResponse updateStatus(Long groupId, OwnershipGroupStatusUpdateRequest req) {
        var e = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        var target = req.status();
        if (target == null) throw new IllegalArgumentException("Status is required");

        // Rule: cho phép ACTIVE -> INACTIVE; KHÔNG cho ACTIVE -> PENDING
        if (e.getStatus() == GroupStatus.ACTIVE && target == GroupStatus.PENDING) {
            throw new IllegalArgumentException("Cannot revert ACTIVE -> PENDING");
        }
        if (e.getStatus() == target) return toDto(e);

        e.setStatus(target);
        return toDto(repo.save(e));
    }

    public OwnershipGroupResponse getById(Long groupId) {
        return repo.findById(groupId)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
    }

    /**
     * list: dùng các hàm derived query đã có trong Repository (không dùng Specification)
     * - keyword: tìm theo GroupName (contains, ignore case)
     * - status: PENDING/ACTIVE/INACTIVE
     * - fromDate/toDate: lọc theo CreatedAt (inclusive), dùng BETWEEN các method đã khai báo
     */
    public Page<OwnershipGroupResponse> list(String keyword,
                                             GroupStatus status,
                                             LocalDate fromDate,
                                             LocalDate toDate,
                                             Pageable pageable) {

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = status != null;
        boolean hasDate = (fromDate != null || toDate != null);

        // Chuẩn hóa mốc thời gian (bao phủ trọn ngày)
        LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : LocalDateTime.MIN;
        LocalDateTime end = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().minusNanos(1) // inclusive style
                : LocalDateTime.MAX;

        Page<OwnershipGroup> page;

        if (hasDate && hasKeyword && hasStatus) {
            page = repo.findByGroupNameContainingIgnoreCaseAndStatusAndCreatedAtBetween(
                    keyword, status, start, end, pageable);
        } else if (hasDate && hasKeyword) {
            page = repo.findByGroupNameContainingIgnoreCaseAndCreatedAtBetween(
                    keyword, start, end, pageable);
        } else if (hasDate && hasStatus) {
            page = repo.findByStatusAndCreatedAtBetween(
                    status, start, end, pageable);
        } else if (hasDate) {
            page = repo.findByCreatedAtBetween(start, end, pageable);
        } else if (hasKeyword && hasStatus) {
            page = repo.findByGroupNameContainingIgnoreCaseAndStatus(keyword, status, pageable);
        } else if (hasKeyword) {
            page = repo.findByGroupNameContainingIgnoreCase(keyword, pageable);
        } else if (hasStatus) {
            page = repo.findByStatus(status, pageable);
        } else {
            page = repo.findAll(pageable);
        }

        return page.map(this::toDto);
    }

    @Transactional
    public void delete(Long groupId) {
        var e = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
        if (e.getStatus() == GroupStatus.ACTIVE) {
            throw new IllegalStateException("Cannot delete ACTIVE group");
        }
        repo.delete(e);
    }

    // ---- Authorization methods ----

    /**
     * Kiểm tra user có phải là admin của group không
     */
    public boolean isGroupAdmin(String userEmail, Long groupId) {
        try {
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

            var ownershipShare = ownershipShareRepository.findById_UserIdAndGroup_GroupId((user.getUserId()), groupId)
                    .orElse(null);

            return ownershipShare != null && ownershipShare.getGroupRole() == GroupRole.ADMIN;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Kiểm tra user có phải là member của group không
     */
    public boolean isGroupMember(String userEmail, Long groupId) {
        try {
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

            var ownershipShare = ownershipShareRepository.findById_UserIdAndGroup_GroupId(user.getUserId(), groupId)
                    .orElse(null);

            return ownershipShare != null;
        } catch (Exception e) {
            return false;
        }
    }
}
