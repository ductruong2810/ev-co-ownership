package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.ContractDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final OwnershipShareRepository shareRepository;

    /**
     * Lấy thông tin hợp đồng chi tiết cho một Group
     * ------------------------------------------------------------
     * Bao gồm:
     *  - Thông tin hợp đồng (terms, ngày bắt đầu, ngày kết thúc,...)
     *  - Thông tin nhóm (tên nhóm, trạng thái, ngày tạo)
     *  - Danh sách thành viên (userId, họ tên, email, vai trò, % sở hữu,...)
     */
    public Map<String, Object> getContractInfoDetail(Long groupId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1️⃣ Lấy hợp đồng của group
        // ------------------------------------------------------------
        // Mỗi nhóm chỉ có 1 hợp đồng đang hoạt động.
        // Nếu không tìm thấy -> ném lỗi để controller trả HTTP 404.
        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy hợp đồng cho groupId " + groupId));

        // 2️⃣ Lấy thông tin nhóm sở hữu
        // ------------------------------------------------------------
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy nhóm sở hữu " + groupId));

        // 3️⃣ Lấy danh sách thành viên trong nhóm
        // ------------------------------------------------------------
        // Mỗi bản ghi OwnershipShare đại diện cho 1 thành viên và phần sở hữu của họ trong group.
        List<OwnershipShare> shares = shareRepository.findByGroup_GroupId(groupId);

        // 4️⃣ Chuẩn bị danh sách thành viên (gọn gàng, không trả entity thô)
        List<Map<String, Object>> members = shares.stream()
                .map(share -> {
                    User user = userRepository.findById(share.getUser().getUserId())
                            .orElse(null);

                    Map<String, Object> memberInfo = new LinkedHashMap<>();
                    memberInfo.put("userId", share.getUser().getUserId());
                    memberInfo.put("fullName", user != null ? user.getFullName() : null);
                    memberInfo.put("email", user != null ? user.getEmail() : null);
                    memberInfo.put("groupRole", share.getGroupRole().name());
                    memberInfo.put("ownershipPercentage", share.getOwnershipPercentage());
                    memberInfo.put("depositStatus", share.getDepositStatus().name());
                    memberInfo.put("joinDate", share.getJoinDate());
                    return memberInfo;
                })
                .toList();

        // 5️⃣ Gộp toàn bộ dữ liệu trả về client
        // ------------------------------------------------------------
        result.put("contract", Map.of(
                "contractId", contract.getId(),
                "terms", contract.getTerms(),
                "requiredDepositAmount", contract.getRequiredDepositAmount(),
                "startDate", contract.getStartDate(),
                "endDate", contract.getEndDate(),
                "isActive", contract.getIsActive(),
                "approvalStatus", contract.getApprovalStatus(),
                "createdAt", contract.getCreatedAt(),
                "updatedAt", contract.getUpdatedAt()
        ));

        result.put("group", Map.of(
                "groupId", group.getGroupId(),
                "groupName", group.getGroupName(),
                "status", group.getStatus(),
                "createdAt", group.getCreatedAt(),
                "updatedAt", group.getUpdatedAt()
        ));

        result.put("members", members);

        return result;
    }

    private final VehicleRepository vehicleRepository;

    /**
     * Lấy contract của group
     */
    private Contract getContractByGroup(Long groupId) {
        return contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for group: " + groupId));
    }




    /**
     * Lấy requiredDepositAmount của group
     * Nếu có contract thì lấy từ contract, nếu không thì tính từ group
     */
    public BigDecimal getRequiredDepositAmount(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        
        // Kiểm tra có contract không
        Contract contract = contractRepository.findByGroupGroupId(groupId).orElse(null);
        
        if (contract != null) {
            return contract.getRequiredDepositAmount();
        } else {
            // Tính deposit amount cho group khi chưa có contract
            return depositCalculationService.calculateRequiredDepositAmount(group);
        }
    }



    /**
     * Hủy contract với lý do
     */
    @Transactional
    public void cancelContract(Long groupId, String reason) {
        Contract contract = getContractByGroup(groupId);

        // Kiểm tra contract có thể hủy không
        if (contract.getApprovalStatus() == ContractApprovalStatus.APPROVED) {
            throw new IllegalStateException("Cannot cancel an approved contract");
        }

        contract.setIsActive(false);
        contract.setApprovalStatus(ContractApprovalStatus.REJECTED);
        contract.setRejectionReason(reason);
        contract.setUpdatedAt(LocalDateTime.now());
        contractRepository.save(contract);
    }

    /**
     * Lấy thông tin contract của group
     * Nếu có contract thì lấy từ contract, nếu không thì tính từ group
     */
    public Map<String, Object> getContractInfo(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        
        // Kiểm tra có contract không
        Contract contract = contractRepository.findByGroupGroupId(groupId).orElse(null);
        
        Map<String, Object> info = new HashMap<>();
        info.put("contractId", contract != null ? contract.getId() : null);
        info.put("groupId", groupId);
        info.put("groupName", group.getGroupName());
        
        if (contract != null) {
            // Có contract
            info.put("startDate", contract.getStartDate());
            info.put("endDate", contract.getEndDate());
            info.put("terms", contract.getTerms());
            info.put("requiredDepositAmount", contract.getRequiredDepositAmount());
            info.put("isActive", contract.getIsActive());
            info.put("approvalStatus", contract.getApprovalStatus());
            info.put("createdAt", contract.getCreatedAt());
            info.put("updatedAt", contract.getUpdatedAt());
        } else {
            // Chưa có contract, tính toán từ group
            info.put("startDate", null);
            info.put("endDate", null);
            info.put("terms", null);
            info.put("requiredDepositAmount", depositCalculationService.calculateRequiredDepositAmount(group));
            info.put("isActive", false);
            info.put("approvalStatus", null);
            info.put("createdAt", null);
            info.put("updatedAt", null);
        }
        
        info.put("templateId", null); // Template sẽ được xử lý ở Frontend

        return info;
    }



    /**
     * Tự động ký contract cho group
     * Điều kiện: Group đã có đủ thành viên và vehicle
     */
    @Transactional
    public Map<String, Object> autoSignContract(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        
        // Kiểm tra đã có contract chưa
        Contract existingContract = contractRepository.findByGroup(group).orElse(null);
        
        // Kiểm tra contract đã được ký chưa (kiểm tra trước để tránh validate không cần thiết)
        if (existingContract != null) {
            if (existingContract.getApprovalStatus() == ContractApprovalStatus.SIGNED || 
                existingContract.getApprovalStatus() == ContractApprovalStatus.APPROVED) {
                throw new IllegalStateException("Contract has already been signed and cannot be auto-signed again");
            }
        }
        
        // Kiểm tra điều kiện để ký tự động
        validateContractGeneration(groupId);
        
        Contract contract;
        if (existingContract != null) {
            // Cập nhật contract hiện có (chỉ khi chưa ký)
            contract = existingContract;
        } else {
            // Tạo contract mới
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = startDate.plusYears(1);
            
            BigDecimal requiredDepositAmount = depositCalculationService.calculateRequiredDepositAmount(group);
            String terms = generateContractTerms(groupId);
            
            contract = Contract.builder()
                    .group(group)
                    .startDate(startDate)
                    .endDate(endDate)
                    .terms(terms)
                    .requiredDepositAmount(requiredDepositAmount)
                    .isActive(true)
                    .approvalStatus(ContractApprovalStatus.PENDING)
                    .build();
        }
        
        // Tự động ký contract
        String signatureInfo = buildAutoSignatureInfo(groupId);
        contract.setTerms(contract.getTerms() + "\n\n" + signatureInfo);
        contract.setApprovalStatus(ContractApprovalStatus.SIGNED);
        contract.setUpdatedAt(LocalDateTime.now());
        
        Contract savedContract = contractRepository.save(contract);
        
        // Gửi notification cho tất cả thành viên
        if (notificationOrchestrator != null) {
            notificationOrchestrator.sendGroupNotification(
                    groupId,
                    NotificationType.CONTRACT_CREATED,
                    "Contract Auto-Signed",
                    "Your co-ownership contract has been automatically signed and is ready for deposit payments."
            );
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("contractId", savedContract.getId());
        result.put("contractNumber", generateContractNumber(savedContract.getId()));
        result.put("status", "AUTO_SIGNED");
        result.put("signedAt", LocalDateTime.now());
        result.put("message", "Contract has been automatically signed");
        
        return result;
    }
    
    /**
     * Xây dựng thông tin chữ ký tự động
     */
    private String buildAutoSignatureInfo(Long groupId) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return "[ĐÃ KÝ TỰ ĐỘNG] " + timestamp +
                " - Hệ thống EV Co-Ownership" +

                // Thêm thông tin pháp lý
                "\n\n[THÔNG TIN PHÁP LÝ]" +
                "\n- Contract được ký tự động khi đủ điều kiện" +
                "\n- Tất cả thành viên đã đồng ý với điều khoản hợp đồng" +
                "\n- Chữ ký này có giá trị pháp lý đầy đủ" +
                "\n- Thời gian ký: " + timestamp +
                "\n- Contract ID: " + contractRepository.findByGroupGroupId(groupId)
                .map(Contract::getId)
                .orElse(null);
    }

    /**
     * Kiểm tra điều kiện để ký tự động contract
     */
    public Map<String, Object> checkAutoSignConditions(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        
        Map<String, Object> conditions = new HashMap<>();
        
        // Kiểm tra số thành viên
        List<OwnershipShare> shares = getSharesByGroupId(groupId);
        Integer memberCapacity = group.getMemberCapacity();
        boolean hasEnoughMembers = shares.size() == memberCapacity;
        conditions.put("hasEnoughMembers", hasEnoughMembers);
        conditions.put("currentMembers", shares.size());
        conditions.put("requiredMembers", memberCapacity);
        
        // Kiểm tra tổng tỷ lệ sở hữu
        BigDecimal totalOwnershipPercentage = BigDecimal.ZERO;
        for (OwnershipShare share : shares) {
            if (share.getOwnershipPercentage() != null) {
                totalOwnershipPercentage = totalOwnershipPercentage.add(share.getOwnershipPercentage());
            }
        }
        BigDecimal expectedTotal = new BigDecimal("100.00");
        boolean hasCorrectOwnershipPercentage = totalOwnershipPercentage.compareTo(expectedTotal) == 0;
        conditions.put("hasCorrectOwnershipPercentage", hasCorrectOwnershipPercentage);
        conditions.put("totalOwnershipPercentage", totalOwnershipPercentage);
        conditions.put("expectedOwnershipPercentage", expectedTotal);
        
        // Kiểm tra có vehicle không
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        boolean hasVehicle = vehicle != null && vehicle.getVehicleValue() != null && vehicle.getVehicleValue().compareTo(BigDecimal.ZERO) > 0;
        conditions.put("hasVehicle", hasVehicle);
        conditions.put("vehicleValue", vehicle != null ? vehicle.getVehicleValue() : BigDecimal.ZERO);
        
        // Kiểm tra contract status
        Contract contract = contractRepository.findByGroupGroupId(groupId).orElse(null);
        boolean canSign = contract == null || contract.getApprovalStatus() == ContractApprovalStatus.PENDING;
        conditions.put("canSign", canSign);
        conditions.put("contractStatus", contract != null ? contract.getApprovalStatus() : "NO_CONTRACT");
        
        // Tổng kết
        boolean allConditionsMet = hasEnoughMembers && hasCorrectOwnershipPercentage && hasVehicle && canSign;
        conditions.put("allConditionsMet", allConditionsMet);
        conditions.put("canAutoSign", allConditionsMet);
        
        return conditions;
    }
    
    /**
     * Tự động kiểm tra và ký contract nếu đủ điều kiện
     * Method này có thể được gọi từ scheduler hoặc event listener
     */
    @Transactional
    public Map<String, Object> checkAndAutoSignContract(Long groupId) {
        Map<String, Object> conditions = checkAutoSignConditions(groupId);
        
        if ((Boolean) conditions.get("canAutoSign")) {
            return autoSignContract(groupId);
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Contract cannot be auto-signed yet");
            result.put("conditions", conditions);
            return result;
        }
    }

    // Removed manual approval methods - contracts are now auto-approved after signing

    // ========== CONTRACT GENERATION METHODS ==========

    /**
     * Generate contract data (chỉ tạo nội dung, không save DB)
     */
    public Map<String, Object> generateContractData(Long groupId, Long userId) {
        // Kiểm tra điều kiện generate contract
        validateContractGeneration(groupId);

        // Tự động tính toán ngày hiệu lực và ngày kết thúc
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1);

        // Tự động generate nội dung contract
        String terms = generateContractTerms(groupId);

        // Chuẩn bị response data (không save DB)
        Map<String, Object> responseData = prepareContractData(groupId);

        // Thêm thông tin contract được generate
        responseData.put("groupId", groupId);
        responseData.put("userId", userId);
        responseData.put("terms", terms);
        responseData.put("startDate", startDate);
        responseData.put("endDate", endDate);
        responseData.put("contractNumber", "EVS-" + groupId + "-" + System.currentTimeMillis());
        responseData.put("generatedAt", LocalDateTime.now());
        responseData.put("status", "GENERATED");
        responseData.put("savedToDatabase", false);

        return responseData;
    }




    /**
     * Chuẩn bị data cho template
     */
    private Map<String, Object> prepareContractData(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = getSharesByGroupId(groupId);

        Map<String, Object> data = new HashMap<>();

        // Contract info
        Map<String, Object> contractInfo = new HashMap<>();
        // For generate contract data (no contract exists yet)
        contractInfo.put("number", "TBD");
        contractInfo.put("effectiveDate", formatDate(LocalDate.now()));
        contractInfo.put("endDate", formatDate(LocalDate.now().plusYears(1)));
        contractInfo.put("termLabel", "1 năm");
        contractInfo.put("location", "HCM"); // Default
        contractInfo.put("signDate", formatDate(LocalDate.now()));
        contractInfo.put("status", "GENERATED");
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
        
        // Tính deposit amount: nếu có contract thì lấy từ contract, nếu không thì tính từ group
        BigDecimal depositAmount;
        // Tính deposit amount cho group khi chưa có contract
        depositAmount = depositCalculationService.calculateRequiredDepositAmount(group);
        financeInfo.put("depositAmount", depositAmount);
        
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
            owner.put("userId", share.getUser().getUserId());
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
     * Generate contract number
     */
    private String generateContractNumber(Long contractId) {
        return "EVS-" + String.format("%04d", contractId) + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
    }

    /**
     * Tự động generate nội dung contract dựa trên template HTML
     */
    private String generateContractTerms(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = getSharesByGroupId(groupId);
        
        StringBuilder terms = new StringBuilder();
        
        // Bắt đầu từ phần 2. GÓP VỐN & QUỸ VẬN HÀNH
        terms.append("2. GÓP VỐN & QUỸ VẬN HÀNH\n");
        if (vehicle != null) {
            terms.append("- Giá trị xe: ").append(formatCurrency(vehicle.getVehicleValue())).append("\n");
        } else {
            terms.append("- Giá trị xe: Sẽ được cập nhật sau\n");
        }
        terms.append("- Tiền cọc: ").append(formatCurrency(calculateDepositAmount(group))).append("\n");
        terms.append("- Mục tiêu quỹ: 50,000,000 VND\n");
        terms.append("- Nguyên tắc góp: Theo tỷ lệ sở hữu\n");
        terms.append("- Tài khoản quỹ: MB Bank 0123456789\n");
        terms.append("\nCác khoản chi bảo dưỡng, sạc, vệ sinh… được thanh toán từ Quỹ chung; khoản cá nhân (nếu có) do cá nhân chi trả theo bút toán bù trừ.\n");
        terms.append("\nLưu ý: Tiền cọc phải được đóng đầy đủ trước khi hợp đồng được kích hoạt và có hiệu lực.\n\n");
        
        // 1. Quyền sử dụng & Lịch đặt
        terms.append("1. QUYỀN SỬ DỤNG & LỊCH ĐẶT\n");
        terms.append("Việc sử dụng xe được thực hiện thông qua hệ thống đặt lịch. Quy tắc ưu tiên: Điểm tín dụng lịch sử & phiên bốc thăm tuần. Mỗi Bên cam kết tuân thủ lịch đặt và hoàn trả đúng hẹn.\n\n");
        
        // 2. Bảo dưỡng, Sửa chữa & Bảo hiểm
        terms.append("2. BẢO DƯỠNG, SỬA CHỮA & BẢO HIỂM\n");
        terms.append("Xe được bảo dưỡng định kỳ theo khuyến nghị của hãng. Trách nhiệm phê duyệt chi phí: Biểu quyết > 50% theo tỷ lệ sở hữu cho chi > 5 triệu. Hợp đồng bảo hiểm: PVI – Gói vật chất toàn diện.\n\n");
        
        // 3. Giải quyết tranh chấp
        terms.append("3. GIẢI QUYẾT TRANH CHẤP\n");
        terms.append("Tranh chấp phát sinh được ghi nhận trên hệ thống và ưu tiên hòa giải trong Nhóm. Cơ chế biểu quyết: Đa số theo tỷ lệ sở hữu; nếu hoà 50/50, ưu tiên lịch sử đóng góp. Thẩm quyền cuối cùng theo pháp luật hiện hành.\n\n");
        
        // 4. Điều khoản chung
        terms.append("4. ĐIỀU KHOẢN CHUNG\n");
        terms.append("- Hợp đồng có hiệu lực khi tất cả Bên đồng sở hữu đồng ý và ký.\n");
        terms.append("- Kích hoạt hợp đồng: Sau khi ký, hợp đồng chỉ được kích hoạt khi tất cả thành viên đã đóng đủ tiền cọc theo quy định.\n");
        terms.append("- Duyệt hợp đồng: Hệ thống sẽ duyệt lại hợp đồng sau khi nhận đủ tiền cọc từ tất cả thành viên.\n");
        terms.append("- Các phụ lục (nếu có) là bộ phận không tách rời của Hợp đồng.\n");
        terms.append("- Hệ thống lưu vết phiên bản, thời điểm ký và danh tính người ký.\n\n");
        
        // 5. Chữ ký các Bên
        terms.append("5. CHỮ KÝ CÁC BÊN\n");
        terms.append("Đại diện nhóm: Admin Group\n");
        terms.append("Ngày ký: ").append(formatDate(LocalDate.now())).append("\n");
        terms.append("Địa điểm: Hà Nội\n\n");
        
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

    /**
     * Kiểm tra điều kiện generate contract
     * Business rule: 
     * 1. Số thành viên thực tế phải bằng memberCapacity
     * 2. Tổng tỷ lệ sở hữu phải bằng 100%
     */
    private void validateContractGeneration(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        List<OwnershipShare> shares = getSharesByGroupId(groupId);
        Integer memberCapacity = group.getMemberCapacity();

        if (memberCapacity == null) {
            throw new IllegalStateException("Group memberCapacity is not set");
        }

        if (shares.size() != memberCapacity) {
            throw new IllegalStateException(
                String.format("Cannot generate contract: Expected %d members, but found %d members. Please add more members to the group.",
                    memberCapacity, shares.size())
            );
        }

        // Kiểm tra tổng tỷ lệ sở hữu phải bằng 100%
        BigDecimal totalOwnershipPercentage = BigDecimal.ZERO;
        for (OwnershipShare share : shares) {
            if (share.getOwnershipPercentage() != null) {
                totalOwnershipPercentage = totalOwnershipPercentage.add(share.getOwnershipPercentage());
            }
        }

        BigDecimal expectedTotal = new BigDecimal("100.00");
        if (totalOwnershipPercentage.compareTo(expectedTotal) != 0) {
            throw new IllegalStateException(
                String.format("Cannot generate contract: Total ownership percentage must be exactly 100%%, but found %s%%. Please adjust ownership percentages.",
                    totalOwnershipPercentage.toString())
            );
        }
    }

    /**
     * Tính toán deposit amount cho group mà không cần contract tồn tại
     */
    private BigDecimal calculateDepositAmount(OwnershipGroup group) {
        return depositCalculationService.calculateRequiredDepositAmount(group);
    }

    @Transactional
    public ContractDTO approveContract(Long contractId, User admin) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        if (contract.getApprovalStatus() != ContractApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending contracts can be approved");
        }

        contract.setApprovalStatus(ContractApprovalStatus.APPROVED);
        contract.setApprovedBy(admin);
        contract.setApprovedAt(LocalDateTime.now());
        contract.setIsActive(true);

        return convertToDTO(contractRepository.save(contract));
    }

    @Transactional
    public ContractDTO rejectContract(Long contractId, String reason, User admin) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        if (contract.getApprovalStatus() != ContractApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending contracts can be rejected");
        }

        contract.setApprovalStatus(ContractApprovalStatus.REJECTED);
        contract.setApprovedBy(admin);
        contract.setApprovedAt(LocalDateTime.now());
        contract.setRejectionReason(reason);
        contract.setIsActive(false);

        return convertToDTO(contractRepository.save(contract));

    }

    private ContractDTO convertToDTO(Contract contract) {
        ContractDTO dto = new ContractDTO();
        dto.setId(contract.getId());
        dto.setGroupId(contract.getGroup().getGroupId());
        dto.setStartDate(contract.getStartDate());
        dto.setEndDate(contract.getEndDate());
        dto.setRequiredDepositAmount(contract.getRequiredDepositAmount());
        dto.setIsActive(contract.getIsActive());
        dto.setApprovalStatus(contract.getApprovalStatus());
        dto.setApprovedById(contract.getApprovedBy() != null ? contract.getApprovedBy().getUserId() : null);
        dto.setApprovedAt(contract.getApprovedAt());
        dto.setRejectionReason(contract.getRejectionReason());
        dto.setCreatedAt(contract.getCreatedAt());
        dto.setUpdatedAt(contract.getUpdatedAt());
        return dto;
    }

    /**
     * Kiểm tra hợp đồng đã đóng đủ tiền cọc chưa (Admin only)
     */
    public Map<String, Object> checkDepositStatus(Long groupId) {
        // 1. Lấy hợp đồng
        Contract contract = getContractByGroup(groupId);
        BigDecimal requiredAmount = contract.getRequiredDepositAmount();

        // 2. Lấy danh sách thành viên
        List<OwnershipShare> shares = getSharesByGroupId(groupId);

        // 3. Tính tổng tiền đã đóng (COMPLETED deposits)
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidMembers = 0;

        List<Map<String, Object>> memberDetails = new java.util.ArrayList<>();

        for (OwnershipShare share : shares) {
            Map<String, Object> memberInfo = new LinkedHashMap<>();

            // Tính tiền cọc cần đóng của từng thành viên
            BigDecimal memberRequired = requiredAmount
                    .multiply(share.getOwnershipPercentage())
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

            // Kiểm tra trạng thái đóng cọc
            boolean isPaid = share.getDepositStatus() == com.group8.evcoownership.enums.DepositStatus.PAID;

            if (isPaid) {
                totalPaid = totalPaid.add(memberRequired);
                paidMembers++;
            }

            memberInfo.put("userId", share.getUser().getUserId());
            memberInfo.put("fullName", share.getUser().getFullName());
            memberInfo.put("ownershipPercentage", share.getOwnershipPercentage());
            memberInfo.put("requiredDeposit", memberRequired);
            memberInfo.put("depositStatus", share.getDepositStatus().name());
            memberInfo.put("isPaid", isPaid);

            memberDetails.add(memberInfo);
        }

        // 4. Tính toán
        boolean isFullyPaid = totalPaid.compareTo(requiredAmount) >= 0;
        BigDecimal remaining = isFullyPaid ? BigDecimal.ZERO : requiredAmount.subtract(totalPaid);
        int totalMembers = shares.size();
        boolean allMembersPaid = paidMembers == totalMembers;

        // 5. Response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", groupId);
        result.put("contractId", contract.getId());
        result.put("approvalStatus", contract.getApprovalStatus());
        result.put("requiredDepositAmount", requiredAmount);
        result.put("totalPaid", totalPaid);
        result.put("remaining", remaining);
        result.put("isFullyPaid", isFullyPaid);
        result.put("totalMembers", totalMembers);
        result.put("paidMembers", paidMembers);
        result.put("allMembersPaid", allMembersPaid);
        result.put("paymentProgress", String.format("%.1f%%",
                totalPaid.multiply(BigDecimal.valueOf(100)).divide(requiredAmount, 1, java.math.RoundingMode.HALF_UP).doubleValue()));
        result.put("memberDetails", memberDetails);

        return result;
    }


    public List<ContractDTO> getAllContracts() {
        return contractRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    public ContractDTO getContractById(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
        return convertToDTO(contract);
    }

    public List<ContractDTO> getContractsByStatus(ContractApprovalStatus status) {
        return contractRepository.findByApprovalStatus(status).stream()
                .map(this::convertToDTO)
                .toList();
    }

    public boolean contractExists(Long contractId) {
        return contractRepository.existsById(contractId);
    }

    /**
     * Lấy userId từ email
     */
    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
        return user.getUserId();
    }
}
