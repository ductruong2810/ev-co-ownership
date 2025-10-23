package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.ContractGenerationRequest;
import com.group8.evcoownership.dto.ContractGenerationResponse;
import com.group8.evcoownership.dto.SaveContractDataRequest;
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
    private final OwnershipShareRepository ownershipShareRepository;

    /**
     * Generate contract data only - Backend chỉ đẩy dữ liệu lên Frontend
     * Frontend không cần gửi template xuống
     * Contract CHƯA được lưu xuống database (chỉ tạo để hiển thị)
     */
    public Map<String, Object> generateContractDataOnly(Long groupId) {
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusYears(1);
        String terms = "Điều khoản hợp đồng sở hữu xe chung EVShare"; // Default terms

        // Tạo contract object tạm thời (không lưu xuống database)
        Contract tempContract = Contract.builder()
                .group(group)
                .startDate(start)
                .endDate(end)
                .terms(terms)
                .isActive(true)
                .approvalStatus(com.group8.evcoownership.enums.ContractApprovalStatus.PENDING)
                .build();

        Map<String, Object> contractData = prepareContractData(groupId, tempContract);

        // Thêm contract number vào response
        String contractNumber = "EVS-" + groupId + "-" + System.currentTimeMillis();
        
        contractData.put("contractNumber", contractNumber);
        contractData.put("contractId", null); // Chưa có ID vì chưa lưu
        contractData.put("generatedAt", LocalDateTime.now());
        contractData.put("status", "GENERATED");
        contractData.put("savedToDatabase", false); // Chưa lưu xuống database

        return contractData;
    }


    /**
     * Lưu contract từ dữ liệu Frontend gửi lên
     */
    @Transactional
    public Map<String, Object> saveContractFromData(Long groupId, SaveContractDataRequest request) {
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        // Tự động tính toán ngày hiệu lực và ngày kết thúc
        LocalDate startDate = LocalDate.now(); // Ngày ký = hôm nay
        LocalDate endDate = startDate.plusYears(1); // Ngày kết thúc = ngày ký + 1 năm
        String terms = request.terms();

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
     * Lấy contract data đã lưu từ database
     */
    public Map<String, Object> getContractDataFromDatabase(Long groupId) {
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        Contract contract = contractRepository.findByGroup(group)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for group: " + groupId));

        Map<String, Object> contractData = prepareContractData(groupId, contract);
        
        String contractNumber = contract.getId() != null ?
                generateContractNumber(contract.getId()) :
                "EVS-" + groupId + "-" + System.currentTimeMillis();
        
        contractData.put("contractNumber", contractNumber);
        contractData.put("contractId", contract.getId());
        contractData.put("approvalStatus", contract.getApprovalStatus());
        contractData.put("isActive", contract.getIsActive());
        contractData.put("createdAt", contract.getCreatedAt());
        contractData.put("updatedAt", contract.getUpdatedAt());
        contractData.put("savedToDatabase", true);

        return contractData;
    }

    /**
     * Export PDF từ dữ liệu - Backend tự tạo PDF không cần template từ Frontend
     */
    public byte[] exportToPdfFromData(Long groupId) {
        // Lấy dữ liệu contract
        Map<String, Object> contractData = generateContractDataOnly(groupId);
        
        // Tạo HTML content từ dữ liệu
        String htmlContent = generateHtmlFromData(contractData);
        
        // Convert HTML to PDF (placeholder implementation)
        // TODO: Implement actual PDF generation
        return htmlContent.getBytes();
    }

    /**
     * Generate HTML content từ dữ liệu contract
     */
    private String generateHtmlFromData(Map<String, Object> contractData) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Contract</title></head><body>");
        html.append("<h1>Hợp đồng sở hữu xe chung</h1>");
        
        // Contract info
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) contractData.get("contract");
        if (contract != null) {
            html.append("<h2>Thông tin hợp đồng</h2>");
            html.append("<p>Số hợp đồng: ").append(contract.get("number")).append("</p>");
            html.append("<p>Ngày hiệu lực: ").append(contract.get("effectiveDate")).append("</p>");
            html.append("<p>Ngày kết thúc: ").append(contract.get("endDate")).append("</p>");
        }
        
        // Group info
        @SuppressWarnings("unchecked")
        Map<String, Object> group = (Map<String, Object>) contractData.get("group");
        if (group != null) {
            html.append("<h2>Thông tin nhóm</h2>");
            html.append("<p>Tên nhóm: ").append(group.get("name")).append("</p>");
        }
        
        // Vehicle info
        @SuppressWarnings("unchecked")
        Map<String, Object> vehicle = (Map<String, Object>) contractData.get("vehicle");
        if (vehicle != null) {
            html.append("<h2>Thông tin xe</h2>");
            html.append("<p>Model: ").append(vehicle.get("model")).append("</p>");
            html.append("<p>Biển số: ").append(vehicle.get("plate")).append("</p>");
        }
        
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Extract terms section from a React TSX template.
     * Simple approach: find between a heading like 'ĐIỀU KHOẢN HỢP ĐỒNG' and next section or end.
     */
    public String extractTermsFromTemplate(String tsx) {
        if (tsx == null) return null;
        String marker = "ĐIỀU KHOẢN HỢP ĐỒNG";
        int idx = tsx.indexOf(marker);
        if (idx < 0) return null;
        String after = tsx.substring(idx + marker.length());
        // Stop at next major heading tag or end
        int stop = after.indexOf("</");
        String raw = stop > 0 ? after.substring(0, stop) : after;
        return raw.trim();
    }

    /**
     * Generate contract cho group với template từ Frontend
     */
    @Transactional
    public ContractGenerationResponse generateContract(Long groupId, ContractGenerationRequest request) {
        // Kiểm tra group tồn tại
        groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        // Tạo hoặc cập nhật contract
        Contract contract = createOrUpdateContract(groupId, request);

        // Chuẩn bị dữ liệu props cho Frontend render
        Map<String, Object> props = prepareContractData(groupId, contract);

        // Generate contract number nếu chưa có
        String contractNumber = contract.getId() != null ?
                generateContractNumber(contract.getId()) :
                "EVS-" + groupId + "-" + System.currentTimeMillis();

        return new ContractGenerationResponse(
                contract.getId(),
                contractNumber,
                props,
                LocalDateTime.now(),
                "GENERATED"
        );
    }

    // Removed HTML preview; TSX-only flow uses templateContent directly

    /**
     * Export to PDF từ React TSX template content (placeholder implementation)
     */
    public byte[] exportToPdf(Long groupId, String templateContent) {
        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for group: " + groupId));
        String htmlContent = generateHtmlContent(groupId, contract, templateContent);
        return htmlContent.getBytes();
    }

    // Removed flexible multi-format handler: TSX-only

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
     * Generate HTML content từ template được truyền từ Frontend
     */
    private String generateHtmlContent(Long groupId, Contract contract, String htmlTemplate) {
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
        List<OwnershipShare> shares = ownershipShareRepository.findByGroupGroupId(groupId);

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
                    result = result.replace("{{" + placeholder + "}}", replacement);
                }
            } else {
                String placeholder = "data." + key;
                String replacement = formatValue(value);
                result = result.replace("{{" + placeholder + "}}", replacement);
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
