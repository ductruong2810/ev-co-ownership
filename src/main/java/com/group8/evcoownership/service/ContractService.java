package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final OwnershipGroupRepository groupRepository;
    private final DepositCalculationService depositCalculationService;

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
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        // Kiểm tra đã có contract chưa
        if (contractRepository.findByGroup(group).isPresent()) {
            throw new IllegalStateException("Group already has a contract");
        }

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
                .build();

        return contractRepository.save(contract);
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
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

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

        Contract savedContract = contractRepository.save(contract);

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
}
