package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.dto.ContractGenerationResponse;
import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class ContractGenerationService {

    private final ContractService contractService;
    private final ContractRepository contractRepository;
    private final OwnershipGroupRepository groupRepository;
    private final VehicleRepository vehicleRepository;
    private final OwnershipShareRepository shareRepository;
    private final TemplateService templateService;

    /**
     * Generate contract cho group
     */
    @Transactional
    public ContractGenerationResponse generateContract(Long groupId, ContractGenerationRequest request) {
        // Kiểm tra group tồn tại
        groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        // Tạo hoặc cập nhật contract
        Contract contract = createOrUpdateContract(groupId, request);

        // Generate HTML content
        String htmlContent = generateHtmlContent(groupId, contract);

        // Generate contract number nếu chưa có
        String contractNumber = contract.getId() != null ?
                generateContractNumber(contract.getId()) :
                "EVS-" + groupId + "-" + System.currentTimeMillis();

        return new ContractGenerationResponse(
                contract.getId(),
                contractNumber,
                htmlContent,
                "/api/contracts/export/" + groupId + "/pdf",
                LocalDateTime.now(),
                "GENERATED"
        );
    }

    /**
     * Generate HTML preview
     */
    public String generateHtmlPreview(Long groupId) {
        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for group: " + groupId));

        return generateHtmlContent(groupId, contract);
    }

    /**
     * Export to PDF
     */
    public byte[] exportToPdf(Long groupId) {
        String htmlContent = generateHtmlPreview(groupId);

        // TODO: Implement HTML to PDF conversion
        // Có thể dùng iText, Flying Saucer, hoặc external service
        return htmlContent.getBytes(); // Placeholder
    }

    /**
     * Tạo hoặc cập nhật contract
     */
    private Contract createOrUpdateContract(Long groupId, ContractGenerationRequest request) {
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        Contract existingContract = contractRepository.findByGroupGroupId(groupId).orElse(null);

        if (existingContract != null) {
            // Cập nhật contract hiện tại
            existingContract.setStartDate(request.startDate());
            existingContract.setEndDate(request.endDate());
            existingContract.setTerms(request.terms());
            return contractRepository.save(existingContract);
        } else {
            // Tạo contract mới
            // Tính requiredDepositAmount
            BigDecimal requiredDepositAmount = contractService.getRequiredDepositAmount(groupId);

            Contract newContract = Contract.builder()
                    .group(group)
                    .startDate(request.startDate())
                    .endDate(request.endDate())
                    .terms(request.terms())
                    .requiredDepositAmount(requiredDepositAmount)
                    .isActive(true)
                    .build();

            return contractRepository.save(newContract);
        }
    }

    /**
     * Generate HTML content từ template
     */
    private String generateHtmlContent(Long groupId, Contract contract) {
        // Lấy template HTML từ Azure Blob Storage
        String htmlTemplate = templateService.getTemplateContent();

        // Chuẩn bị data
        Map<String, Object> data = prepareContractData(groupId, contract);

        // Replace placeholders trong template
        String htmlContent = replaceTemplatePlaceholders(htmlTemplate, data);

        return htmlContent;
    }

    /**
     * Chuẩn bị data cho template
     */
    private Map<String, Object> prepareContractData(Long groupId, Contract contract) {
        OwnershipGroup group = contract.getGroup();
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = shareRepository.findByGroupGroupId(groupId);

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
     * Replace placeholders trong template với data
     */
    private String replaceTemplatePlaceholders(String template, Map<String, Object> data) {
        String result = template;

        // Replace simple placeholders
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                for (Map.Entry<String, Object> subEntry : subMap.entrySet()) {
                    String placeholder = "data." + key + "." + subEntry.getKey();
                    String replacement = formatValue(subEntry.getValue());
                    result = result.replaceAll("\\{\\{" + placeholder + "\\}\\}", replacement);
                }
            } else {
                String placeholder = "data." + key;
                String replacement = formatValue(value);
                result = result.replaceAll("\\{\\{" + placeholder + "\\}\\}", replacement);
            }
        }

        return result;
    }

    /**
     * Format value cho template
     */
    private String formatValue(Object value) {
        if (value == null) return "—";
        if (value instanceof BigDecimal) {
            return formatCurrency((BigDecimal) value);
        }
        return value.toString();
    }

    /**
     * Format currency
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format("%,.0f VND", amount.doubleValue());
    }

    /**
     * Format date
     */
    private String formatDate(LocalDate date) {
        if (date == null) return "—";
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
}
