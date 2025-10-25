package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.OwnershipShareId;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.exception.InsufficientDocumentsException;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnershipGroupService {

    private final OwnershipGroupRepository repo;
    private final OwnershipShareRepository ownershipShareRepository;
    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final VehicleService vehicleService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final FundService fundService;
    private final OcrService ocrService;

    @Value("${app.validation.enabled:true}")
    private boolean validationEnabled;

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

    /**
     * Convert OwnershipGroup thành StaffOwnershipGroupResponse với custom vehicle description
     */
    private StaffOwnershipGroupResponse toStaffDto(OwnershipGroup e) {
        String vehicleDescription = getCustomVehicleDescription(e.getStatus());
        String rejectionReason = null;
        
        // Nếu status là INACTIVE, lấy lý do từ chối từ field rejectionReason
        if (e.getStatus() == GroupStatus.INACTIVE) {
            rejectionReason = e.getRejectionReason();
        }
        
        return new StaffOwnershipGroupResponse(
                e.getGroupId(),
                e.getGroupName(),
                e.getDescription(),
                e.getMemberCapacity(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                vehicleDescription,
                rejectionReason
        );
    }

    /**
     * Tạo custom vehicle description theo status
     */
    private String getCustomVehicleDescription(GroupStatus status) {
        return switch (status) {
            case PENDING -> "Nhóm đăng ký và đang chờ duyệt bởi staff";
            case ACTIVE -> "Nhóm đã được duyệt và có thể sử dụng các chức năng trong group";
            case INACTIVE -> "Nhóm đã bị từ chối bởi staff";
        };
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

        // Kiểm tra giấy tờ cần thiết (bỏ qua khi tắt validation)
        if (validationEnabled) {
            validateRequiredDocuments(user.getUserId());
        }

        // Tạo group
        var entity = OwnershipGroup.builder()
                .groupName(req.groupName())
                .description(req.description())
                .memberCapacity(req.memberCapacity())
                .build(); // status = PENDING at @PrePersist

        var savedGroup = repo.save(entity);

        // Tự động thêm người tạo group làm ADMIN với 0% ownership (để phân bổ sau)
        var shareId = new OwnershipShareId(user.getUserId(), savedGroup.getGroupId());
        var ownershipShare = OwnershipShare.builder()
                .id(shareId)
                .user(user)
                .group(savedGroup)
                .groupRole(GroupRole.ADMIN)
                .depositStatus(DepositStatus.PENDING)
                .ownershipPercentage(java.math.BigDecimal.ZERO) // Bắt đầu với 0%
                .joinDate(LocalDateTime.now())
                .build();

        ownershipShareRepository.save(ownershipShare);

        // Tự động tạo quỹ cho nhóm mới
        fundService.createOrGroup(savedGroup.getGroupId());

        // Send notification to group creator
        notificationOrchestrator.sendComprehensiveNotification(
                user.getUserId(),
                NotificationType.GROUP_CREATED,
                "Group Created",
                String.format("You have successfully created co-ownership group: %s", savedGroup.getGroupName()),
                Map.of("groupId", savedGroup.getGroupId(), "groupName", savedGroup.getGroupName())
        );

        return toDto(savedGroup);
    }

    @Transactional
    public GroupWithVehicleResponse createGroupWithVehicle(
            String groupName, String description, Integer memberCapacity,
            java.math.BigDecimal vehicleValue, String licensePlate, String chassisNumber,
            MultipartFile[] vehicleImages, String[] imageTypes, String userEmail,
            String brand, String model, Boolean enableAutoFill) {
        
        long startTime = System.currentTimeMillis();
        GroupWithVehicleResponse.AutoFillInfo autoFillInfo = null;
        
        // Step 1: Process OCR if auto-fill is enabled (default true)
        boolean shouldProcessOcr = enableAutoFill == null || enableAutoFill;
        log.info("OCR processing enabled: {} (enableAutoFill: {})", shouldProcessOcr, enableAutoFill);
        
        if (shouldProcessOcr) {
            log.info("Starting OCR processing for {} images", vehicleImages.length);
            autoFillInfo = processOcrAutoFill(vehicleImages, imageTypes, startTime);
            log.info("OCR processing completed. AutoFillInfo: {}", autoFillInfo);
            
            // Validate user input against OCR extracted information
            validateUserInputAgainstOcr(brand, model, licensePlate, chassisNumber, autoFillInfo);
            
            // Use OCR extracted information if user input is empty or invalid
            if (autoFillInfo.extractedBrand() != null && !autoFillInfo.extractedBrand().isEmpty()) {
                if (brand == null || brand.trim().isEmpty() || brand.equals("Unknown")) {
                    brand = autoFillInfo.extractedBrand();
                    log.info("Using OCR extracted brand: {}", brand);
                }
            }
            if (autoFillInfo.extractedModel() != null && !autoFillInfo.extractedModel().isEmpty()) {
                if (model == null || model.trim().isEmpty() || model.equals("Unknown")) {
                    model = autoFillInfo.extractedModel();
                    log.info("Using OCR extracted model: {}", model);
                }
            }
            if (autoFillInfo.extractedLicensePlate() != null && !autoFillInfo.extractedLicensePlate().isEmpty()) {
                if (licensePlate == null || licensePlate.trim().isEmpty()) {
                    licensePlate = autoFillInfo.extractedLicensePlate();
                    log.info("Using OCR extracted license plate: {}", licensePlate);
                }
            }
            if (autoFillInfo.extractedChassisNumber() != null && !autoFillInfo.extractedChassisNumber().isEmpty()) {
                if (chassisNumber == null || chassisNumber.trim().isEmpty()) {
                    chassisNumber = autoFillInfo.extractedChassisNumber();
                    log.info("Using OCR extracted chassis number: {}", chassisNumber);
                }
            }
        }
        
        // Step 2: Validate vehicle information and document quality
        boolean hasLicensePlateInput = licensePlate != null && !licensePlate.trim().isEmpty();
        boolean hasChassisInput = chassisNumber != null && !chassisNumber.trim().isEmpty();
        boolean hasAnyUserInput = hasLicensePlateInput || hasChassisInput;
        
        if (!hasAnyUserInput) {
            // No user input - must rely on OCR
            if (autoFillInfo != null && isLikelyInvalidDocument(autoFillInfo)) {
                throw new IllegalArgumentException(
                    "Không thể trích xuất thông tin xe từ hình ảnh. Vui lòng kiểm tra lại hình ảnh có phải là giấy đăng ký xe hợp lệ không, hoặc nhập thông tin thủ công.");
            }
            
            throw new IllegalArgumentException(
                "Biển số xe và số khung xe là bắt buộc. Vui lòng nhập thông tin thủ công hoặc đảm bảo OCR có thể trích xuất từ giấy đăng ký xe.");
        } else {
            // User provided some input - STRICT VALIDATION: Must have valid document
            if (autoFillInfo != null && isLikelyInvalidDocument(autoFillInfo)) {
                throw new IllegalArgumentException(
                    "Hình ảnh không phải là giấy đăng ký xe hợp lệ. Vui lòng upload đúng cà vẹt xe để xác minh thông tin.");
            }
        }
        
        // Step 3: Create ownership group với userEmail (quỹ sẽ được tạo tự động)
        OwnershipGroupCreateRequest groupRequest = new OwnershipGroupCreateRequest(
                groupName, description, memberCapacity);
        OwnershipGroupResponse groupResponse = create(groupRequest, userEmail);

        // Step 4: Create vehicle using VehicleService with extracted or provided brand/model
        VehicleCreateRequest vehicleRequest = new VehicleCreateRequest(
                brand != null && !brand.isEmpty() ? brand : "Unknown", 
                model != null && !model.isEmpty() ? model : "Unknown", 
                licensePlate, chassisNumber, vehicleValue, groupResponse.groupId());
        VehicleResponse vehicleResponse = vehicleService.create(vehicleRequest);

        // Step 5: Upload multiple vehicle images using VehicleService
        Map<String, Object> uploadedImages = vehicleService.uploadMultipleVehicleImages(
                vehicleResponse.vehicleId(), vehicleImages, imageTypes);

        // Step 6: Return combined response with auto-fill info
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
                vehicleResponse.vehicleValue(),
                uploadedImages,
                autoFillInfo
        );
    }
    
    /**
     * Check if uploaded document is likely invalid (not a vehicle registration document)
     */
    private boolean isLikelyInvalidDocument(GroupWithVehicleResponse.AutoFillInfo autoFillInfo) {
        if (autoFillInfo == null) {
            return true;
        }
        
        // Check if OCR confidence is very low
        String confidence = autoFillInfo.ocrConfidence();
        if (confidence != null && (confidence.toLowerCase().contains("no text") || 
                                  confidence.toLowerCase().contains("failed") ||
                                  confidence.toLowerCase().contains("low"))) {
            return true;
        }
        
        // Check if no meaningful vehicle information was extracted
        boolean hasLicensePlate = autoFillInfo.extractedLicensePlate() != null && !autoFillInfo.extractedLicensePlate().isEmpty();
        boolean hasChassisNumber = autoFillInfo.extractedChassisNumber() != null && !autoFillInfo.extractedChassisNumber().isEmpty();
        boolean hasValidBrand = autoFillInfo.extractedBrand() != null && !autoFillInfo.extractedBrand().isEmpty() && !autoFillInfo.extractedBrand().equals("Unknown");
        
        // If none of the critical fields were extracted, likely invalid
        return !hasLicensePlate && !hasChassisNumber && !hasValidBrand;
    }
    
    /**
     * Validate user input against OCR extracted information
     * Uses hybrid approach: strict validation for critical fields, flexible for others
     */
    private void validateUserInputAgainstOcr(String brand, String model, String licensePlate, String chassisNumber, 
                                           GroupWithVehicleResponse.AutoFillInfo autoFillInfo) {
        StringBuilder errors = new StringBuilder();
        StringBuilder warnings = new StringBuilder();
        
        // CRITICAL VALIDATION: License plate - must match exactly (block creation)
        if (licensePlate != null && !licensePlate.trim().isEmpty() && 
            autoFillInfo.extractedLicensePlate() != null && !autoFillInfo.extractedLicensePlate().isEmpty()) {
            
            String userPlate = licensePlate.trim().toUpperCase();
            String ocrPlate = autoFillInfo.extractedLicensePlate().trim().toUpperCase();
            
            if (!userPlate.equals(ocrPlate)) {
                errors.append("Biển số xe không khớp với giấy tờ đăng ký. ");
            }
        }
        
        // CRITICAL VALIDATION: Chassis number - must match exactly (block creation)
        if (chassisNumber != null && !chassisNumber.trim().isEmpty() && 
            autoFillInfo.extractedChassisNumber() != null && !autoFillInfo.extractedChassisNumber().isEmpty()) {
            
            String userChassis = chassisNumber.trim().toUpperCase();
            String ocrChassis = autoFillInfo.extractedChassisNumber().trim().toUpperCase();
            
            if (!userChassis.equals(ocrChassis)) {
                errors.append("Số khung xe không khớp với giấy tờ đăng ký. ");
            }
        }
        
        // SOFT VALIDATION: Brand - only warn if user explicitly provided brand
        if (brand != null && !brand.trim().isEmpty() && !brand.equals("Unknown") && 
            autoFillInfo.extractedBrand() != null && !autoFillInfo.extractedBrand().isEmpty()) {
            
            String userBrand = brand.trim().toUpperCase();
            String ocrBrand = autoFillInfo.extractedBrand().trim().toUpperCase();
            
            if (!userBrand.equals(ocrBrand)) {
                warnings.append("Nhãn hiệu xe có thể không khớp với giấy tờ đăng ký. ");
                log.warn("Brand mismatch: user='{}' vs OCR='{}'", brand, autoFillInfo.extractedBrand());
            }
        }
        
        // SOFT VALIDATION: Model - only warn if user explicitly provided model
        if (model != null && !model.trim().isEmpty() && !model.equals("Unknown") && 
            autoFillInfo.extractedModel() != null && !autoFillInfo.extractedModel().isEmpty()) {
            
            String userModel = model.trim().toUpperCase();
            String ocrModel = autoFillInfo.extractedModel().trim().toUpperCase();
            
            if (!userModel.equals(ocrModel)) {
                warnings.append("Dòng xe có thể không khớp với giấy tờ đăng ký. ");
                log.warn("Model mismatch: user='{}' vs OCR='{}'", model, autoFillInfo.extractedModel());
            }
        }
        
        // Handle validation results
        if (errors.length() > 0) {
            // CRITICAL ERRORS: Block creation
            String errorMessage = "Thông tin xe không khớp với giấy tờ đăng ký. " + errors.toString().trim();
            log.warn("User input validation failed: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        
        if (warnings.length() > 0) {
            // SOFT WARNINGS: Log but allow creation
            log.info("Soft validation warnings: {}", warnings.toString().trim());
        }
        
        // Check if OCR detected this as a vehicle registration document
        if (!autoFillInfo.isRegistrationDocument()) {
            log.warn("OCR did not detect this as a vehicle registration document. Staff review recommended.");
        }
        
        // Check if OCR extracted meaningful vehicle information
        boolean hasValidVehicleInfo = (autoFillInfo.extractedLicensePlate() != null && !autoFillInfo.extractedLicensePlate().isEmpty()) ||
                                     (autoFillInfo.extractedChassisNumber() != null && !autoFillInfo.extractedChassisNumber().isEmpty()) ||
                                     (autoFillInfo.extractedBrand() != null && !autoFillInfo.extractedBrand().isEmpty() && !autoFillInfo.extractedBrand().equals("Unknown"));
        
        if (!hasValidVehicleInfo) {
            log.warn("OCR did not extract meaningful vehicle information. Possible invalid document uploaded.");
        }
    }
    
    /**
     * Process OCR auto-fill for vehicle information
     */
    private GroupWithVehicleResponse.AutoFillInfo processOcrAutoFill(
            MultipartFile[] vehicleImages, String[] imageTypes, long startTime) {
        
        try {
            // Find registration document image
            MultipartFile registrationImage = findRegistrationImage(vehicleImages, imageTypes);
            
            if (registrationImage == null) {
                return new GroupWithVehicleResponse.AutoFillInfo(
                    true, "", "", "", "", "", false, "No registration document found", 
                    String.valueOf(System.currentTimeMillis() - startTime) + "ms"
                );
            }
            
            // Use the shared OCR processing method
            return ocrService.processVehicleInfoFromImage(registrationImage, startTime).get();
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            return new GroupWithVehicleResponse.AutoFillInfo(
                true, "", "", "", "", "", false, "OCR processing failed: " + e.getMessage(), 
                processingTime + "ms"
            );
        }
    }
    
    /**
     * Find registration document image from uploaded images
     */
    private MultipartFile findRegistrationImage(MultipartFile[] vehicleImages, String[] imageTypes) {
        for (int i = 0; i < vehicleImages.length; i++) {
            if (i < imageTypes.length && 
                ("LICENSE".equals(imageTypes[i]) || "REGISTRATION".equals(imageTypes[i]) || 
                 "CA_VET".equals(imageTypes[i]) || "GIẤY_ĐĂNG_KÝ".equals(imageTypes[i]))) {
                return vehicleImages[i];
            }
        }
        
        // If no specific registration image type found, return the first image
        return vehicleImages.length > 0 ? vehicleImages[0] : null;
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
        
        // Lưu lý do từ chối nếu status = INACTIVE
        if (target == GroupStatus.INACTIVE) {
            e.setRejectionReason(req.rejectionReason());
        } else {
            // Xóa lý do từ chối nếu status không phải INACTIVE
            e.setRejectionReason(null);
        }
        
        return toDto(repo.save(e));
    }

    public OwnershipGroupResponse getById(Long groupId) {
        return repo.findById(groupId)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));
    }

    /**
     * Lấy thông tin group với role của user hiện tại
     */
    public OwnershipGroupResponse getByIdWithUserRole(Long groupId, String userEmail) {
        var entity = repo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
        
        OwnershipGroupResponse response = toDto(entity);
        
        // Thêm thông tin role của user
        try {
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

            var ownershipShare = ownershipShareRepository.findById_UserIdAndGroup_GroupId(user.getUserId(), groupId)
                    .orElse(null);

            if (ownershipShare != null) {
                response = new OwnershipGroupResponse(
                        response.groupId(),
                        response.groupName(),
                        response.description(),
                        response.memberCapacity(),
                        response.status(),
                        response.createdAt(),
                        response.updatedAt(),
                        ownershipShare.getGroupRole().toString(),
                        ownershipShare.getGroupRole() == GroupRole.ADMIN,
                        true,
                        ownershipShare.getOwnershipPercentage()
                );
            } else {
                response = new OwnershipGroupResponse(
                        response.groupId(),
                        response.groupName(),
                        response.description(),
                        response.memberCapacity(),
                        response.status(),
                        response.createdAt(),
                        response.updatedAt(),
                        null,
                        false,
                        false,
                        null
                );
            }
        } catch (Exception e) {
            // Nếu có lỗi, trả về response không có thông tin user
            response = new OwnershipGroupResponse(
                    response.groupId(),
                    response.groupName(),
                    response.description(),
                    response.memberCapacity(),
                    response.status(),
                    response.createdAt(),
                    response.updatedAt(),
                    null,
                    false,
                    false,
                    null
            );
        }
        
        return response;
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

        // ✅ Giới hạn thời gian trong phạm vi hợp lệ của SQL Server
        LocalDateTime SQLSERVER_MIN = LocalDateTime.of(1753, 1, 1, 0, 0);
        LocalDateTime SQLSERVER_MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000);

        // ✅ Chuẩn hóa mốc thời gian (bao phủ trọn ngày)
        LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : SQLSERVER_MIN;
        LocalDateTime end = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().minusNanos(1)
                : SQLSERVER_MAX;

        // ✅ Gọi native query trong repository
        Page<OwnershipGroup> page = repo.findSortedGroups(
                hasKeyword ? keyword : null,
                hasStatus && status != null ? status.name() : null,
                start,
                end,
                pageable
        );

        return page.map(this::toDto);
    }

    /**
     * listForStaff: API dành cho staff với custom vehicle description
     */
    public Page<StaffOwnershipGroupResponse> listForStaff(String keyword,
                                                         GroupStatus status,
                                                         LocalDate fromDate,
                                                         LocalDate toDate,
                                                         Pageable pageable) {

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = status != null;

        // ✅ Giới hạn thời gian trong phạm vi hợp lệ của SQL Server
        LocalDateTime SQLSERVER_MIN = LocalDateTime.of(1753, 1, 1, 0, 0);
        LocalDateTime SQLSERVER_MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000);

        // ✅ Chuẩn hóa mốc thời gian (bao phủ trọn ngày)
        LocalDateTime start = (fromDate != null) ? fromDate.atStartOfDay() : SQLSERVER_MIN;
        LocalDateTime end = (toDate != null)
                ? toDate.plusDays(1).atStartOfDay().minusNanos(1)
                : SQLSERVER_MAX;

        // ✅ Gọi native query trong repository
        Page<OwnershipGroup> page = repo.findSortedGroups(
                hasKeyword ? keyword : null,
                hasStatus && status != null ? status.name() : null,
                start,
                end,
                pageable
        );

        return page.map(this::toStaffDto);
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
     * Lấy tất cả groups mà user đã tạo và tham gia (bao gồm cả ADMIN và MEMBER)
     */
    public List<OwnershipGroupResponse> getGroupsByUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userEmail));

        List<OwnershipGroup> groups = ownershipShareRepository.findGroupsByUserId(user.getUserId());
        return groups.stream().map(this::toDto).toList();
    }

    /**
     * Kiểm tra user có phải là admin của group không
     */
    public boolean isGroupAdmin(String userEmail, Long groupId) {
        try {
            var user = userRepository.findByEmail(userEmail)
                    .orElse(null);
            
            if (user == null) {
                return false;
            }

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
                    .orElse(null);
            
            if (user == null) {
                return false;
            }

            var ownershipShare = ownershipShareRepository.findById_UserIdAndGroup_GroupId(user.getUserId(), groupId)
                    .orElse(null);

            return ownershipShare != null;
        } catch (Exception e) {
            return false;
        }
    }
}
