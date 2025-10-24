package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final OwnershipGroupRepository groupRepository;
    private final OwnershipShareRepository ownershipShareRepository;
    private final DepositCalculationService depositCalculationService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final VehicleRepository vehicleRepository;

    /**
     * Lấy contract của group
     */
    private Contract getContractByGroup(Long groupId) {
        return contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for group: " + groupId));
    }

    /**
     * Tạo contract cho group với thời hạn mặc định 1 năm
     */
    @Transactional
    public Contract createDefaultContract(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        // Kiểm tra đã có contract chưa
        if (contractRepository.findByGroup(group).isPresent()) {
            throw new IllegalStateException("Group already has a contract");
        }

        // Kiểm tra điều kiện tạo contract
        validateContractCreation(groupId);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1); // Mặc định 1 năm

        // Tính requiredDepositAmount dựa trên group
        BigDecimal calculatedDepositAmount = depositCalculationService.calculateRequiredDepositAmount(group);

        Contract contract = Contract.builder()
                .group(group)
                .startDate(startDate)
                .endDate(endDate)
                .terms("Standard EV co-ownership contract for 1 year")
                .requiredDepositAmount(calculatedDepositAmount)
                .isActive(true)
                .approvalStatus(ContractApprovalStatus.PENDING)
                .build();

        Contract savedContract = contractRepository.save(contract);

        // Send notification to group members (skip in unit tests where orchestrator is not injected)
        if (notificationOrchestrator != null) {
            notificationOrchestrator.sendGroupNotification(
                    groupId,
                    NotificationType.CONTRACT_CREATED,
                    "Contract Created",
                    "A new co-ownership contract has been created for your group"
            );
        }

        return savedContract;
    }

    /**
     * Tạo contract với thông tin tùy chỉnh
     */
    @Transactional
    public Contract createCustomContract(Long groupId, LocalDate startDate, LocalDate endDate,
                                         String terms, BigDecimal requiredDepositAmount) {
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        // Kiểm tra đã có contract chưa
        if (contractRepository.findByGroup(group).isPresent()) {
            throw new IllegalStateException("Group already has a contract");
        }

        Contract contract = Contract.builder()
                .group(group)
                .startDate(startDate)
                .endDate(endDate)
                .terms(terms)
                .requiredDepositAmount(requiredDepositAmount)
                .isActive(true)
                .approvalStatus(ContractApprovalStatus.PENDING)
                .build();

        return contractRepository.save(contract);
    }

    /**
     * Cập nhật contract
     */
    @Transactional
    public Contract updateContract(Long groupId, LocalDate startDate, LocalDate endDate,
                                   String terms, BigDecimal requiredDepositAmount, Boolean isActive) {
        Contract contract = getContractByGroup(groupId);

        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setTerms(terms);
        contract.setRequiredDepositAmount(requiredDepositAmount);
        contract.setIsActive(isActive);
        contract.setUpdatedAt(LocalDateTime.now());

        return contractRepository.save(contract);
    }

    /**
     * Lấy requiredDepositAmount của group
     */
    public BigDecimal getRequiredDepositAmount(Long groupId) {
        Contract contract = getContractByGroup(groupId);
        return contract.getRequiredDepositAmount();
    }

    /**
     * Cập nhật requiredDepositAmount của contract
     */
    @Transactional
    public Contract updateRequiredDepositAmount(Long groupId, BigDecimal newAmount) {
        Contract contract = getContractByGroup(groupId);
        contract.setRequiredDepositAmount(newAmount);
        contract.setUpdatedAt(LocalDateTime.now());
        return contractRepository.save(contract);
    }

    /**
     * Tính lại requiredDepositAmount dựa trên Vehicle và memberCapacity
     */
    @Transactional
    public Contract recalculateRequiredDepositAmount(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        Contract contract = getContractByGroup(groupId);
        BigDecimal newAmount = depositCalculationService.calculateRequiredDepositAmount(group);

        contract.setRequiredDepositAmount(newAmount);
        contract.setUpdatedAt(LocalDateTime.now());

        return contractRepository.save(contract);
    }

    /**
     * Hủy contract
     */
    @Transactional
    public void cancelContract(Long groupId) {
        Contract contract = getContractByGroup(groupId);
        contract.setIsActive(false);
        contract.setUpdatedAt(LocalDateTime.now());
        contractRepository.save(contract);
    }

    /**
     * Lấy thông tin contract của group
     */
    public Map<String, Object> getContractInfo(Long groupId) {
        Contract contract = getContractByGroup(groupId);
        OwnershipGroup group = contract.getGroup();

        Map<String, Object> info = new HashMap<>();
        info.put("contractId", contract.getId());
        info.put("groupId", groupId);
        info.put("groupName", group.getGroupName());
        info.put("startDate", contract.getStartDate());
        info.put("endDate", contract.getEndDate());
        info.put("terms", contract.getTerms());
        info.put("requiredDepositAmount", contract.getRequiredDepositAmount());
        info.put("isActive", contract.getIsActive());
        info.put("templateId", null); // Template sẽ được xử lý ở Frontend
        info.put("createdAt", contract.getCreatedAt());
        info.put("updatedAt", contract.getUpdatedAt());

        return info;
    }

    /**
     * Ký hợp đồng (Admin Group ký thay tất cả Members)
     */
    @Transactional
    public Map<String, Object> signContract(Long groupId, Map<String, Object> signRequest) {
        Contract contract = getContractByGroup(groupId);

        // Kiểm tra contract đã được ký chưa
        if (contract.getIsActive() == null || !contract.getIsActive()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Contract is not active");
            return error;
        }

        // Kiểm tra contract đã được ký chưa (tránh ký nhiều lần)
        if (contract.getTerms() != null && contract.getTerms().contains("[ĐÃ KÝ]")) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Contract has already been signed");
            return error;
        }

        // Lấy thông tin Admin Group
        String adminName = (String) signRequest.getOrDefault("adminName", "Admin Group");
        String signatureType = (String) signRequest.getOrDefault("signatureType", "ADMIN_PROXY");

        // Cập nhật thông tin ký với đại diện
        String signatureInfo = buildSignatureInfo(adminName, signatureType, groupId);
        contract.setTerms(contract.getTerms() + "\n\n" + signatureInfo);
        contract.setUpdatedAt(LocalDateTime.now());

        // Chuyển status từ PENDING → SIGNED
        contract.setApprovalStatus(ContractApprovalStatus.SIGNED);

        Contract savedContract = contractRepository.save(contract);

        // Không tự động duyệt ngay sau khi ký
        // Contract sẽ được auto-approve khi tất cả deposit đã được thanh toán
        // Logic này được xử lý trong DepositPaymentService.checkAndActivateContractIfAllDepositsPaid()

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("contractId", savedContract.getId());
        result.put("signedAt", LocalDateTime.now());
        result.put("signedBy", adminName);
        result.put("signatureType", signatureType);
        result.put("message", "Contract signed successfully by Admin Group on behalf of all members");

        return result;
    }

    /**
     * Xây dựng thông tin chữ ký
     */
    private String buildSignatureInfo(String adminName, String signatureType, Long groupId) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        StringBuilder signature = new StringBuilder();
        signature.append("[ĐÃ KÝ] ").append(timestamp);
        signature.append(" - ").append(adminName);

        if ("ADMIN_PROXY".equals(signatureType)) {
            signature.append(" (Đại diện cho tất cả thành viên)");
        }

        // Thêm thông tin pháp lý
        signature.append("\n\n[THÔNG TIN PHÁP LÝ]");
        signature.append("\n- Admin Group có quyền ký thay tất cả thành viên theo quy định nội bộ");
        signature.append("\n- Chữ ký này có giá trị pháp lý tương đương với chữ ký của từng thành viên");
        signature.append("\n- Thời gian ký: ").append(timestamp);
        signature.append("\n- Contract ID: ").append(contractRepository.findByGroupGroupId(groupId)
                .map(Contract::getId)
                .orElse(null));

        return signature.toString();
    }

    // Removed manual approval methods - contracts are now auto-approved after signing

    // ========== CONTRACT GENERATION METHODS ==========

    /**
     * Tự động tạo và lưu contract (không cần input từ frontend)
     */
    @Transactional
    public Map<String, Object> saveContractFromData(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        // Tự động tính toán ngày hiệu lực và ngày kết thúc
        LocalDate startDate = LocalDate.now(); // Ngày ký = hôm nay
        LocalDate endDate = startDate.plusYears(1); // Ngày kết thúc = ngày ký + 1 năm
        
        // Tự động generate nội dung contract
        String terms = generateContractTerms(groupId);

        // Kiểm tra đã có contract chưa
        Contract existingContract = contractRepository.findByGroup(group).orElse(null);
        
        Contract contract;
        if (existingContract != null) {
            // Cập nhật contract hiện có
            existingContract.setStartDate(startDate);
            existingContract.setEndDate(endDate);
            existingContract.setTerms(terms);
            existingContract.setUpdatedAt(LocalDateTime.now());
            contract = contractRepository.save(existingContract);
        } else {
            // Tạo contract mới và lưu xuống database
            ContractGenerationRequest req = new ContractGenerationRequest(
                    startDate,
                    endDate,
                    terms,
                    "HCM",
                    startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    null
            );
            contract = createOrUpdateContract(groupId, req);
        }

        // Chuẩn bị response data
        Map<String, Object> responseData = prepareContractData(groupId, contract);
        
        String contractNumber = contract.getId() != null ?
                generateContractNumber(contract.getId()) :
                "EVS-" + groupId + "-" + System.currentTimeMillis();
        
        responseData.put("contractNumber", contractNumber);
        responseData.put("contractId", contract.getId());
        responseData.put("savedAt", LocalDateTime.now());
        responseData.put("status", "SAVED");
        responseData.put("savedToDatabase", true);

        return responseData;
    }

    /**
     * Lưu contract từ dữ liệu Frontend gửi lên
     */
    /**
     * Lưu contract từ dữ liệu Frontend gửi lên
     */
    @Transactional
    public Map<String, Object> saveContractFromData(Long groupId) {  // Removed SaveContractDataRequest
        OwnershipGroup group = getGroupById(groupId);

        // Tự động tính toán ngày hiệu lực và ngày kết thúc
        LocalDate startDate = LocalDate.now(); // Ngày ký = hôm nay
        LocalDate endDate = startDate.plusYears(1); // Ngày kết thúc = ngày ký + 1 năm

        // Kiểm tra đã có contract chưa
        Contract existingContract = contractRepository.findByGroup(group).orElse(null);

        Contract contract;
        if (existingContract != null) {
            // Cập nhật contract hiện có
            existingContract.setStartDate(startDate);
            existingContract.setEndDate(endDate);
            existingContract.setUpdatedAt(LocalDateTime.now());
            contract = contractRepository.save(existingContract);
        } else {
            // Tạo contract mới và lưu xuống database
            ContractGenerationRequest req = new ContractGenerationRequest(
                    startDate,
                    endDate,
                    "HCM",
                    startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    null
            );
            contract = createOrUpdateContract(groupId, req);
        }

        // Chuẩn bị response data
        Map<String, Object> responseData = prepareContractData(groupId, contract);

        String contractNumber = contract.getId() != null ?
                generateContractNumber(contract.getId()) :
                "EVS-" + groupId + "-" + System.currentTimeMillis();

        responseData.put("contractNumber", contractNumber);
        responseData.put("contractId", contract.getId());
        responseData.put("savedAt", LocalDateTime.now());
        responseData.put("status", "SAVED");
        responseData.put("savedToDatabase", true);

        return responseData;
    }

    /**
     * Tạo hoặc cập nhật contract
     */
    private Contract createOrUpdateContract(Long groupId, ContractGenerationRequest request) {
        OwnershipGroup group = getGroupById(groupId);

        Contract existingContract = contractRepository.findByGroupGroupId(groupId).orElse(null);

        if (existingContract != null) {
            // Cập nhật contract hiện tại
            existingContract.setStartDate(request.startDate());
            existingContract.setEndDate(request.endDate());
            return contractRepository.save(existingContract);
        } else {
            // Kiểm tra điều kiện tạo contract mới
            validateContractCreation(groupId);

            // Tạo contract mới
            // Tính requiredDepositAmount
            BigDecimal requiredDepositAmount = getRequiredDepositAmount(groupId);

            Contract newContract = Contract.builder()
                    .group(group)
                    .startDate(request.startDate())
                    .endDate(request.endDate())
                    .requiredDepositAmount(requiredDepositAmount)
                    .isActive(true)
                    .approvalStatus(ContractApprovalStatus.PENDING) // Luôn bắt đầu với PENDING
                    .build();

            return contractRepository.save(newContract);
        }
    }

    /**
     * Chuẩn bị data cho template
     */
    private Map<String, Object> prepareContractData(Long groupId, Contract contract) {
        OwnershipGroup group = contract.getGroup();
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = getSharesByGroupId(groupId);

        Map<String, Object> data = new HashMap<>();

        // Contract info
        Map<String, Object> contractInfo = new HashMap<>();
        contractInfo.put("number", generateContractNumber(contract.getId()));
        contractInfo.put("effectiveDate", formatDate(contract.getStartDate()));
        contractInfo.put("endDate", formatDate(contract.getEndDate()));
        contractInfo.put("termLabel", calculateTermLabel(contract.getStartDate(), contract.getEndDate()));
        contractInfo.put("location", "Hà Nội"); // Default
        contractInfo.put("signDate", formatDate(LocalDate.now()));
        contractInfo.put("status", "DRAFT");
        data.put("contract", contractInfo);

        // Group info
        Map<String, Object> groupInfo = new HashMap<>();
        groupInfo.put("name", group.getGroupName());
        data.put("group", groupInfo);

        // Vehicle info
        Map<String, Object> vehicleInfo = new HashMap<>();
        if (vehicle != null) {
            vehicleInfo.put("model", vehicle.getBrand() + " " + vehicle.getModel());
            vehicleInfo.put("plate", vehicle.getLicensePlate());
            vehicleInfo.put("vin", vehicle.getChassisNumber());
        } else {
            vehicleInfo.put("model", "—");
            vehicleInfo.put("plate", "—");
            vehicleInfo.put("vin", "—");
        }
        data.put("vehicle", vehicleInfo);

        // Finance info
        Map<String, Object> financeInfo = new HashMap<>();
        financeInfo.put("vehiclePrice", vehicle != null ? vehicle.getVehicleValue() : BigDecimal.ZERO);
        financeInfo.put("depositAmount", contract.getRequiredDepositAmount());
        financeInfo.put("targetAmount", BigDecimal.valueOf(50000000)); // Default target
        financeInfo.put("contributionRule", "Theo tỷ lệ sở hữu");
        data.put("finance", financeInfo);

        // Usage info
        Map<String, Object> usageInfo = new HashMap<>();
        usageInfo.put("rule", "Điểm tín dụng lịch sử & phiên bốc thăm tuần");
        data.put("usage", usageInfo);

        // Maintenance info
        Map<String, Object> maintenanceInfo = new HashMap<>();
        maintenanceInfo.put("approval", "Biểu quyết > 50% theo tỷ lệ sở hữu cho chi > 5 triệu");
        maintenanceInfo.put("insurance", "PVI – Gói vật chất toàn diện");
        data.put("maintenance", maintenanceInfo);

        // Dispute info
        Map<String, Object> disputeInfo = new HashMap<>();
        disputeInfo.put("voting", "Đa số theo tỷ lệ sở hữu; nếu hoà 50/50, ưu tiên lịch sử đóng góp");
        data.put("dispute", disputeInfo);

        // Owners info
        List<Map<String, Object>> owners = shares.stream().map(share -> {
            Map<String, Object> owner = new HashMap<>();
            owner.put("name", share.getUser().getFullName());
            owner.put("phone", share.getUser().getPhoneNumber());
            owner.put("email", share.getUser().getEmail());
            owner.put("idType", "CCCD");
            owner.put("idNo", "—"); // Placeholder
            owner.put("share", share.getOwnershipPercentage());
            return owner;
        }).collect(Collectors.toList());
        data.put("owners", owners);

        return data;
    }

    /**
     * Format date
     */
    private String formatDate(LocalDate date) {
        if (date == null) return "—";
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Format currency to Vietnamese format
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 VND";
        return String.format("%,.0f VND", amount.doubleValue());
    }

    /**
     * Calculate term label
     */
    private String calculateTermLabel(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return "—";

        long months = java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate);
        if (months == 12) return "12 tháng";
        if (months == 6) return "6 tháng";
        return months + " tháng";
    }

    /**
     * Generate contract number
     */
    private String generateContractNumber(Long contractId) {
        return "EVS-" + String.format("%04d", contractId) + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
    }

    /**
     * Tự động generate nội dung contract
     */
    private String generateContractTerms(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = getSharesByGroupId(groupId);
        
        StringBuilder terms = new StringBuilder();
        
        // Header
        terms.append("HỢP ĐỒNG SỞ HỮU XE CHUNG\n");
        terms.append("Nhóm: ").append(group.getGroupName()).append("\n\n");
        
        // Contract info
        terms.append("ĐIỀU 1: THÔNG TIN HỢP ĐỒNG\n");
        terms.append("- Số hợp đồng: ").append(generateContractNumber(groupId)).append("\n");
        terms.append("- Ngày hiệu lực: ").append(formatDate(LocalDate.now())).append("\n");
        terms.append("- Ngày kết thúc: ").append(formatDate(LocalDate.now().plusYears(1))).append("\n");
        terms.append("- Thời hạn: 12 tháng\n\n");
        
        // Vehicle info
        terms.append("ĐIỀU 2: THÔNG TIN XE\n");
        if (vehicle != null) {
            terms.append("- Loại xe: ").append(vehicle.getBrand()).append(" ").append(vehicle.getModel()).append("\n");
            terms.append("- Biển số: ").append(vehicle.getLicensePlate()).append("\n");
            terms.append("- Số khung: ").append(vehicle.getChassisNumber()).append("\n");
            terms.append("- Giá trị xe: ").append(formatCurrency(vehicle.getVehicleValue())).append("\n");
        } else {
            terms.append("- Thông tin xe sẽ được cập nhật sau\n");
        }
        terms.append("\n");
        
        // Members info
        terms.append("ĐIỀU 3: THÀNH VIÊN NHÓM\n");
        terms.append("- Số thành viên: ").append(shares.size()).append("\n");
        terms.append("- Sức chứa tối đa: ").append(group.getMemberCapacity()).append("\n");
        for (OwnershipShare share : shares) {
            terms.append("- ").append(share.getUser().getFullName())
                 .append(" (").append(share.getOwnershipPercentage()).append("%)\n");
        }
        terms.append("\n");
        
        // Financial terms
        terms.append("ĐIỀU 4: ĐIỀU KHOẢN TÀI CHÍNH\n");
        terms.append("- Tiền cọc yêu cầu: ").append(formatCurrency(getRequiredDepositAmount(groupId))).append("\n");
        terms.append("- Quỹ chung mục tiêu: 50,000,000 VND\n");
        terms.append("- Quy tắc đóng góp: Theo tỷ lệ sở hữu\n");
        terms.append("- Tài khoản quỹ: MB Bank 0123456789\n\n");
        
        // Usage terms
        terms.append("ĐIỀU 5: QUY TẮC SỬ DỤNG\n");
        terms.append("- Phương thức phân bổ: Điểm tín dụng lịch sử & phiên bốc thăm tuần\n");
        terms.append("- Ưu tiên: Theo điểm tín dụng và lịch sử sử dụng\n\n");
        
        // Maintenance terms
        terms.append("ĐIỀU 6: BẢO DƯỠNG VÀ SỬA CHỮA\n");
        terms.append("- Phê duyệt: Biểu quyết > 50% theo tỷ lệ sở hữu cho chi phí > 5 triệu\n");
        terms.append("- Bảo hiểm: PVI – Gói vật chất toàn diện\n");
        terms.append("- Chi phí: Chia theo tỷ lệ sở hữu\n\n");
        
        // Dispute resolution
        terms.append("ĐIỀU 7: GIẢI QUYẾT TRANH CHẤP\n");
        terms.append("- Phương thức: Biểu quyết đa số\n");
        terms.append("- Trọng tài: Theo tỷ lệ sở hữu\n");
        terms.append("- Luật áp dụng: Pháp luật Việt Nam\n\n");
        
        // Signatures
        terms.append("ĐIỀU 8: CHỮ KÝ\n");
        terms.append("- Đại diện nhóm: Admin Group\n");
        terms.append("- Ngày ký: ").append(formatDate(LocalDate.now())).append("\n");
        terms.append("- Địa điểm: Hà Nội\n\n");
        
        terms.append("Hợp đồng này có hiệu lực từ ngày ký và được tất cả thành viên nhóm đồng ý.\n");
        
        return terms.toString();
    }

    // ========== HELPER METHODS (từ ContractServiceHelper) ==========

    /**
     * Helper method để lấy group by ID với error handling
     */
    private OwnershipGroup getGroupById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
    }

    /**
     * Helper method để lấy danh sách shares của group
     */
    private List<OwnershipShare> getSharesByGroupId(Long groupId) {
        return ownershipShareRepository.findByGroupGroupId(groupId);
    }

    /**
     * Kiểm tra điều kiện tạo contract
     * Business rule: Số thành viên thực tế phải bằng memberCapacity
     */
    private void validateContractCreation(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        List<OwnershipShare> shares = getSharesByGroupId(groupId);
        Integer memberCapacity = group.getMemberCapacity();

        if (memberCapacity == null) {
            throw new IllegalStateException("Group memberCapacity is not set");
        }

        if (shares.size() != memberCapacity) {
            throw new IllegalStateException(
                String.format("Cannot create contract: Expected %d members, but found %d members",
                    memberCapacity, shares.size())
            );
        }
    }

}
