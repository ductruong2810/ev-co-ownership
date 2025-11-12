package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.*;
import com.group8.evcoownership.exception.InvalidContractActionException;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractService {

    private final ContractRepository contractRepository;
    private final OwnershipGroupRepository groupRepository;
    private final OwnershipShareRepository ownershipShareRepository;
    private final DepositCalculationService depositCalculationService;
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final OwnershipShareRepository shareRepository;
    private final ContractDeadlinePolicy deadlinePolicy;
    private final DepositPaymentService depositPaymentService;
    private final ContractFeedbackRepository feedbackRepository;

    @Value("${contract.deposit.deadline.minutes:5}")
    private long depositDeadlineMinutes;

    /**
     * Lấy thông tin hợp đồng chi tiết cho một Group
     * ------------------------------------------------------------
     * Bao gồm:
     * - Thông tin hợp đồng (terms, ngày bắt đầu, ngày kết thúc,...)
     * - Thông tin nhóm (tên nhóm, trạng thái, ngày tạo)
     * - Danh sách thành viên (userId, họ tên, email, vai trò, % sở hữu,...)
     */
    public Map<String, Object> getContractInfoDetail(Long groupId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Lấy hợp đồng của group
        // ------------------------------------------------------------
        // Mỗi nhóm chỉ có 1 hợp đồng đang hoạt động.
        // Nếu không tìm thấy -> ném lỗi để controller trả HTTP 404.
        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy hợp đồng cho groupId " + groupId));

        //  Lấy thông tin nhóm sở hữu
        // ------------------------------------------------------------
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Không tìm thấy nhóm sở hữu " + groupId));

        //  Lấy danh sách thành viên trong nhóm
        // ------------------------------------------------------------
        // Mỗi bản ghi OwnershipShare đại diện cho 1 thành viên và phần sở hữu của họ trong group.
        List<OwnershipShare> shares = shareRepository.findByGroup_GroupId(groupId);

        // Chuẩn bị danh sách thành viên (gọn gàng, không trả entity thô)
        List<Map<String, Object>> members = shares.stream()
                .map(share -> {
                    User user = userRepository.findById(share.getUser().getUserId())
                            .orElse(null);

                    Map<String, Object> memberInfo = new LinkedHashMap<>();
                    memberInfo.put("userId", share.getUser().getUserId());
                    memberInfo.put("fullName", user != null ? user.getFullName() : null);
                    memberInfo.put("email", user != null ? user.getEmail() : null);
                    memberInfo.put("userRole", share.getGroupRole().name());
                    memberInfo.put("ownershipPercentage", share.getOwnershipPercentage());
                    memberInfo.put("depositStatus", share.getDepositStatus().name());
                    memberInfo.put("joinDate", share.getJoinDate());
                    return memberInfo;
                })
                .toList();

        // Gộp toàn bộ dữ liệu trả về client
        // ------------------------------------------------------------
        result.put("contract", Map.of(
                "contractId", contract.getId(),
                "terms", contract.getTerms(),
                "requiredDepositAmount", contract.getRequiredDepositAmount(),
                "startDate", contract.getStartDate(),
                "endDate", contract.getEndDate(),
                "depositDeadline", computeDepositDeadlineSafe(contract),
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
     * Cập nhật hợp đồng (chỉ cho phép khi contract ở trạng thái PENDING hoặc PENDING_MEMBER_APPROVAL có rejections)
     * Chỉ được sửa: StartDate, EndDate
     * RequiredDepositAmount được tính tự động bởi hệ thống
     */
    @Transactional
    public Map<String, Object> updateContract(Long groupId, ContractUpdateRequestDTO request) {
        Contract contract = getContractByGroup(groupId);
        OwnershipGroup group = contract.getGroup();

        validateContractEditable(contract, "Cannot update contract: Contract is in %s status. Only PENDING contracts or PENDING_MEMBER_APPROVAL contracts with rejections can be updated.");
        validateContractDatesOrThrow(request.startDate(), request.endDate());

        // Tính toán lại deposit amount tự động (theo quy định của hệ thống)
        BigDecimal calculatedDepositAmount = depositCalculationService.calculateRequiredDepositAmount(group);
        
        // Cập nhật contract
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setRequiredDepositAmount(calculatedDepositAmount); // Tự động tính toán, không cho phép override
        contract.setUpdatedAt(LocalDateTime.now());
        
        // Approve tất cả feedbacks PENDING trước (admin đã chỉnh sửa contract)
        approveMemberFeedbacks(contract.getId());

        // Cập nhật terms để phản ánh thay đổi deposit amount và term
        String updatedTerms = updateDepositAmountInTerms(contract.getTerms(), calculatedDepositAmount);
        updatedTerms = updateTermInTerms(updatedTerms, request.startDate(), request.endDate());
        contract.setTerms(updatedTerms);
        
        Contract savedContract = contractRepository.saveAndFlush(contract);

        // Sau đó invalidate để members review lại contract mới
        invalidateMemberFeedbacks(contract.getId());

        // Trả về thông tin
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Contract updated successfully");
        result.put("contract", Map.of(
                "contractId", savedContract.getId(),
                "startDate", savedContract.getStartDate(),
                "endDate", savedContract.getEndDate(),
                "requiredDepositAmount", savedContract.getRequiredDepositAmount(),
                "approvalStatus", savedContract.getApprovalStatus(),
                "updatedAt", savedContract.getUpdatedAt()
        ));
        result.put("calculatedDepositAmount", calculatedDepositAmount);
        result.put("depositCalculationExplanation",
                "Deposit amount is automatically calculated by the system: " + formatCurrency(calculatedDepositAmount) +
                " (10% of vehicle value or calculated based on number of members).");
        
        // Tính toán và trả về term mới
        String termLabel = calculateTermLabel(savedContract.getStartDate(), savedContract.getEndDate());
        result.put("term", termLabel);
        result.put("termExplanation", 
                "Contract term: " + termLabel + 
                " (from " + formatDate(savedContract.getStartDate()) + 
                " to " + formatDate(savedContract.getEndDate()) + ")");

        return result;
    }

    /**
     * Cập nhật terms của hợp đồng (chỉ cho phép khi contract ở trạng thái PENDING hoặc PENDING_MEMBER_APPROVAL có rejections)
     */
    @Transactional
    public Map<String, Object> updateContractTerms(Long groupId, ContractTermsUpdateRequestDTO request) {
        if (request.terms() == null || request.terms().trim().isEmpty()) {
            throw new IllegalArgumentException("Terms cannot be blank");
        }

        Contract contract = getContractByGroup(groupId);

        validateContractEditable(contract, "Cannot update contract terms: Contract is in %s status. Only PENDING contracts or PENDING_MEMBER_APPROVAL contracts with rejections can be updated.");

        // Approve tất cả feedbacks PENDING trước (admin đã chỉnh sửa contract)
        approveMemberFeedbacks(contract.getId());

        contract.setTerms(request.terms().trim());
        contract.setUpdatedAt(LocalDateTime.now());

        Contract savedContract = contractRepository.saveAndFlush(contract);

        // Sau đó invalidate để members review lại contract mới
        invalidateMemberFeedbacks(contract.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Contract terms updated successfully");
        result.put("contract", Map.of(
                "contractId", savedContract.getId(),
                "approvalStatus", savedContract.getApprovalStatus(),
                "updatedAt", savedContract.getUpdatedAt()
        ));

        return result;
    }

    /**
     * ADMIN-ONLY: Cập nhật theo contractId (thay vì groupId)
     */
    @Transactional
    public Map<String, Object> updateContractByAdminByContractId(Long contractId, ContractAdminUpdateRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.isInvalidDateRange()) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        return updateContractAdminCommon(contract, request);
    }

    private Map<String, Object> updateContractAdminCommon(Contract contract, ContractAdminUpdateRequestDTO request) {
        validateContractEditable(contract, "Cannot update contract: Contract is in %s status. Only PENDING contracts or PENDING_MEMBER_APPROVAL contracts with rejections can be updated.");
        validateContractDatesOrThrow(request.startDate(), request.endDate());

        OwnershipGroup group = contract.getGroup();
        BigDecimal calculatedDepositAmount = depositCalculationService.calculateRequiredDepositAmount(group);

        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setRequiredDepositAmount(calculatedDepositAmount);
        contract.setUpdatedAt(LocalDateTime.now());

        String baseTerms = request.terms() != null && !request.terms().trim().isEmpty()
                ? request.terms().trim()
                : (contract.getTerms() != null ? contract.getTerms() : "");

        String syncedTerms = updateDepositAmountInTerms(baseTerms, calculatedDepositAmount);
        syncedTerms = updateTermInTerms(syncedTerms, request.startDate(), request.endDate());
        contract.setTerms(syncedTerms);

        // Approve tất cả feedbacks PENDING trước (admin đã chỉnh sửa contract)
        approveMemberFeedbacks(contract.getId());
        
        Contract saved = contractRepository.saveAndFlush(contract);

        // Sau đó invalidate để members review lại contract mới
        invalidateMemberFeedbacks(contract.getId());

        // Notify all group members that the contract has been updated by admin
        if (notificationOrchestrator != null) {
            Map<String, Object> emailData = notificationOrchestrator.buildContractEmailData(saved);
            notificationOrchestrator.sendGroupNotification(
                    saved.getGroup().getGroupId(),
                    NotificationType.CONTRACT_APPROVAL_PENDING,
                    "Contract Updated by Admin",
                    "The system administrator has updated the contract timeline and terms. Please review the changes.",
                    emailData
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Contract updated successfully by admin");
        result.put("contract", Map.of(
                "contractId", saved.getId(),
                "startDate", saved.getStartDate(),
                "endDate", saved.getEndDate(),
                "requiredDepositAmount", saved.getRequiredDepositAmount(),
                "approvalStatus", saved.getApprovalStatus(),
                "updatedAt", saved.getUpdatedAt()
        ));
        result.put("term", calculateTermLabel(saved.getStartDate(), saved.getEndDate()));
        return result;
    }
    
    private void validateContractDatesOrThrow(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        LocalDate today = LocalDate.now();
        if (startDate.isBefore(today)) {
            throw new IllegalArgumentException("Start date cannot be in the past");
        }
        LocalDate minimumEndDate = startDate.plusMonths(1);
        if (endDate.isBefore(minimumEndDate)) {
            throw new IllegalArgumentException("Contract term must be at least 1 month");
        }
        LocalDate maximumEndDate = startDate.plusYears(5);
        if (endDate.isAfter(maximumEndDate)) {
            throw new IllegalArgumentException("Contract term cannot exceed 5 years");
        }
    }

    /**
     * Admin (system) resubmits the contract for member approval without changing content.
     * Invalidates all existing member feedbacks (sets to PENDING) and notifies all members to review again.
     */
    @Transactional
    public Map<String, Object> resubmitMemberApproval(Long contractId, String adminNote) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // Only allow resubmitting when the contract is in member approval stage
        if (contract.getApprovalStatus() != ContractApprovalStatus.PENDING_MEMBER_APPROVAL) {
            throw new IllegalStateException("Resubmit is only allowed when contract is in PENDING_MEMBER_APPROVAL");
        }

        // Invalidate all member feedbacks (set to PENDING) - members need to review again
        invalidateMemberFeedbacks(contractId);

        contract.setUpdatedAt(LocalDateTime.now());
        Contract saved = contractRepository.saveAndFlush(contract);

        // Notify all members to review again
        if (notificationOrchestrator != null) {
            Map<String, Object> data = notificationOrchestrator.buildContractEmailData(saved);
            if (adminNote != null && !adminNote.isBlank()) {
                data = new HashMap<>(data);
                data.put("adminNote", adminNote.trim());
            }
            notificationOrchestrator.sendGroupNotification(
                    saved.getGroup().getGroupId(),
                    NotificationType.CONTRACT_APPROVAL_PENDING,
                    "Contract Resubmitted for Member Approval",
                    (adminNote != null && !adminNote.isBlank())
                            ? adminNote.trim()
                            : "The system administrator has resubmitted the contract for member approval. Please review and confirm.",
                    data
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Resubmitted for member approval");
        result.put("contractId", saved.getId());
        result.put("groupId", saved.getGroup().getGroupId());
        result.put("approvalStatus", saved.getApprovalStatus());
        result.put("feedbacksInvalidated", true); // Feedbacks set to PENDING, members need to review again
        return result;
    }
    /**
     * Lấy thông tin tính toán deposit amount cho group admin
     * Bao gồm giá trị tính toán tự động và giải thích công thức
     */
    public Map<String, Object> getDepositCalculationInfo(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        
        BigDecimal calculatedAmount = depositCalculationService.calculateRequiredDepositAmount(group);
        
        Map<String, Object> info = new HashMap<>();
        info.put("calculatedDepositAmount", calculatedAmount);
        info.put("formattedAmount", formatCurrency(calculatedAmount));
        
        // Giải thích công thức tính toán
        StringBuilder explanation = new StringBuilder();
        if (vehicle != null && vehicle.getVehicleValue() != null && vehicle.getVehicleValue().compareTo(BigDecimal.ZERO) > 0) {
            explanation.append("Formula: Vehicle value × 10% = ")
                    .append(formatCurrency(vehicle.getVehicleValue()))
                    .append(" × 10% = ")
                    .append(formatCurrency(calculatedAmount));
            info.put("calculationMethod", "VEHICLE_VALUE_PERCENTAGE");
            info.put("vehicleValue", vehicle.getVehicleValue());
            info.put("percentage", "10%");
        } else {
            explanation.append("Formula: Base amount + (Number of members × 10% × Base amount) = ")
                    .append(formatCurrency(calculatedAmount));
            info.put("calculationMethod", "MEMBER_CAPACITY");
            info.put("memberCapacity", group.getMemberCapacity());
        }
        
        info.put("explanation", explanation.toString());
        info.put("note", "You can override this value when updating the contract. This value is the total deposit for the entire group.");
        
        return info;
    }

    /**
     * Tính toán kỳ hạn hợp đồng từ startDate và endDate
     * Trả về string mô tả kỳ hạn (ví dụ: "1 year", "6 months", "2 years 3 months", "90 days")
     */
    private String calculateTermLabel(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return "N/A";
        }
        
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        long months = java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate);
        long years = java.time.temporal.ChronoUnit.YEARS.between(startDate, endDate);
        
        if (years > 0) {
            long remainingMonths = months % 12;
            if (remainingMonths == 0) {
                return years + (years == 1 ? " year" : " years");
            } else {
                return years + (years == 1 ? " year" : " years") + " " + remainingMonths + (remainingMonths == 1 ? " month" : " months");
            }
        } else if (months > 0) {
            return months + (months == 1 ? " month" : " months");
        } else {
            return days + (days == 1 ? " day" : " days");
        }
    }

    /**
     * Cập nhật term trong contract terms (nếu có)
     */
    private String updateTermInTerms(String terms, LocalDate startDate, LocalDate endDate) {
        if (terms == null || terms.isEmpty() || startDate == null || endDate == null) {
            return terms;
        }
        
        // Tính toán term mới
        String newTermLabel = calculateTermLabel(startDate, endDate);

        String noteSuffix = " (minimum 1 month, maximum 5 years)";
        Pattern pattern = Pattern.compile("(?i)(Kỳ hạn|Term|Period):\\s*[^\\n]+");
        Matcher matcher = pattern.matcher(terms);

        if (matcher.find()) {
            String label = matcher.group(1);
            String replacement = label + ": " + newTermLabel + noteSuffix;
            return matcher.replaceAll(replacement);
        }

        // Nếu không tìm thấy pattern, append term line vào đầu
        return "Term: " + newTermLabel + noteSuffix + "\n" + terms;
    }

    /**
     * Cập nhật deposit amount trong contract terms
     */
    private String updateDepositAmountInTerms(String terms, BigDecimal newDepositAmount) {
        if (terms == null || terms.isEmpty()) {
            return terms;
        }
        
        // Tìm và thay thế dòng "- Deposit amount: ..."
        String depositPattern = "- Deposit amount:.*";
        String replacement = "- Deposit amount: " + formatCurrency(newDepositAmount);
        
        return terms.replaceAll(depositPattern, replacement);
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
        contractRepository.saveAndFlush(contract);
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
            info.put("depositDeadline", computeDepositDeadlineSafe(contract));
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

        // Tự động ký contract bởi admin group
        String signatureInfo = buildAutoSignatureInfo(groupId);
        contract.setTerms(contract.getTerms() + "\n\n" + signatureInfo);
        contract.setApprovalStatus(ContractApprovalStatus.PENDING_MEMBER_APPROVAL); // Chờ các member approve
        contract.setUpdatedAt(LocalDateTime.now());

        Contract savedContract = contractRepository.saveAndFlush(contract);

        // Gửi notification cho tất cả thành viên (trừ admin group) để approve/reject contract
        if (notificationOrchestrator != null) {
            Map<String, Object> emailData = notificationOrchestrator.buildContractEmailData(savedContract);
            notificationOrchestrator.sendGroupNotification(
                    groupId,
                    NotificationType.CONTRACT_CREATED,
                    "Contract Pending Member Approval",
                    "The group admin has signed the contract. Please review and approve or reject it.",
                    emailData
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("contractId", savedContract.getId());
        result.put("contractNumber", generateContractNumber(savedContract.getId()));
        result.put("status", "PENDING_MEMBER_APPROVAL");
        result.put("signedAt", LocalDateTime.now());
        result.put("message", "Contract has been signed by group admin. Waiting for member approvals.");

        return result;
    }

    private LocalDateTime computeDepositDeadlineSafe(Contract contract) {
        if (deadlinePolicy != null) {
            return deadlinePolicy.computeDepositDeadline(contract);
        }
        LocalDateTime reference = contract.getUpdatedAt() != null
                ? contract.getUpdatedAt()
                : (contract.getCreatedAt() != null ? contract.getCreatedAt() : LocalDateTime.now());
        return reference.plusMinutes(depositDeadlineMinutes);
    }

    /**
     * Xây dựng thông tin chữ ký tự động
     */
    private String buildAutoSignatureInfo(Long groupId) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return "[AUTO-SIGNED] " + timestamp +
                " - EV Co-Ownership System" +

                // Legal information
                "\n\n[LEGAL INFORMATION]" +
                "\n- This contract was automatically signed once all required conditions were met." +
                "\n- All co-owners have agreed to the contract terms." +
                "\n- This electronic signature holds full legal validity under applicable laws." +
                "\n- Signing time: " + timestamp +
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
        boolean hasValidOwnershipPercentages = true;
        int membersWithZeroOrNullPercentage = 0;
        BigDecimal minimumOwnership = new BigDecimal("1.00"); // Tối thiểu 1%

        for (OwnershipShare share : shares) {
            if (share.getOwnershipPercentage() == null) {
                hasValidOwnershipPercentages = false;
                membersWithZeroOrNullPercentage++;
            } else if (share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0) {
                hasValidOwnershipPercentages = false;
                membersWithZeroOrNullPercentage++;
            } else if (share.getOwnershipPercentage().compareTo(minimumOwnership) < 0) {
                hasValidOwnershipPercentages = false;
            } else {
                totalOwnershipPercentage = totalOwnershipPercentage.add(share.getOwnershipPercentage());
            }
        }

        BigDecimal expectedTotal = new BigDecimal("100.00");
        boolean hasCorrectOwnershipPercentage = hasValidOwnershipPercentages &&
                totalOwnershipPercentage.compareTo(expectedTotal) == 0;
        conditions.put("hasCorrectOwnershipPercentage", hasCorrectOwnershipPercentage);
        conditions.put("totalOwnershipPercentage", totalOwnershipPercentage);
        conditions.put("expectedOwnershipPercentage", expectedTotal);
        conditions.put("hasValidOwnershipPercentages", hasValidOwnershipPercentages);
        conditions.put("membersWithZeroOrNullPercentage", membersWithZeroOrNullPercentage);

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

        // Kiểm tra xem contract đã tồn tại trong database chưa
        Optional<Contract> existingContract = contractRepository.findByGroupGroupId(groupId);

        // Tự động tính toán ngày hiệu lực và ngày kết thúc
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusYears(1);

        // Tự động generate nội dung contract
        String terms = generateContractTerms(groupId);

        // Chuẩn bị response data (không save DB)
        Map<String, Object> responseData = prepareContractData(groupId, existingContract);

        // Thêm thông tin contract được generate
        responseData.put("groupId", groupId);
        responseData.put("userId", userId);
        responseData.put("terms", terms);
        responseData.put("startDate", startDate);
        responseData.put("endDate", endDate);
        responseData.put("contractNumber", "EVS-" + groupId + "-" + System.currentTimeMillis());
        responseData.put("generatedAt", LocalDateTime.now());

        // Trả về status từ database nếu contract đã tồn tại
        if (existingContract.isPresent()) {
            Contract contract = existingContract.get();
            responseData.put("contractId", contract.getId());
            responseData.put("status", contract.getApprovalStatus());
            responseData.put("isActive", contract.getIsActive());
            responseData.put("savedToDatabase", true);

            // Nếu contract đã ký, thêm thông tin ký
            if (contract.getApprovalStatus() == ContractApprovalStatus.SIGNED ||
                    contract.getApprovalStatus() == ContractApprovalStatus.APPROVED) {
                responseData.put("signedAt", contract.getUpdatedAt());
                responseData.put("signed", true);
            }
        } else {
            // Contract chưa tồn tại, status mặc định là PENDING
            responseData.put("status", ContractApprovalStatus.PENDING);
            responseData.put("isActive", false);
            responseData.put("savedToDatabase", false);
        }

        return responseData;
    }


    /**
     * Chuẩn bị data cho template
     */
    private Map<String, Object> prepareContractData(Long groupId, Optional<Contract> existingContract) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = getSharesByGroupId(groupId);

        Map<String, Object> data = new HashMap<>();

        // Contract info
        Map<String, Object> contractInfo = new HashMap<>();
        
        // Set status từ contract nếu có, nếu không thì PENDING
        if (existingContract.isPresent()) {
            Contract contract = existingContract.get();
            contractInfo.put("number", "TBD");
            contractInfo.put("effectiveDate", formatDate(contract.getStartDate()));
            contractInfo.put("endDate", formatDate(contract.getEndDate()));
            // Tính toán term từ startDate và endDate thực tế
            String termLabel = calculateTermLabel(contract.getStartDate(), contract.getEndDate());
            contractInfo.put("termLabel", termLabel);
            contractInfo.put("status", contract.getApprovalStatus());
        } else {
            // For generate contract data (no contract exists yet)
            LocalDate defaultStartDate = LocalDate.now();
            LocalDate defaultEndDate = defaultStartDate.plusYears(1);
            contractInfo.put("number", "TBD");
            contractInfo.put("effectiveDate", formatDate(defaultStartDate));
            contractInfo.put("endDate", formatDate(defaultEndDate));
            contractInfo.put("termLabel", calculateTermLabel(defaultStartDate, defaultEndDate));
            contractInfo.put("status", ContractApprovalStatus.PENDING);
        }
        
        contractInfo.put("location", "HCM"); // Default
        contractInfo.put("signDate", formatDate(LocalDate.now()));

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

        financeInfo.put("contributionRule", "According to ownership ratio");
        data.put("finance", financeInfo);

        // Usage info
//        Map<String, Object> usageInfo = new HashMap<>();
//        usageInfo.put("rule", "Điểm tín dụng lịch sử & phiên bốc thăm tuần");
//        data.put("usage", usageInfo);

        // Maintenance info
        Map<String, Object> maintenanceInfo = new HashMap<>();
        maintenanceInfo.put("approval", "Approval by >50% vote based on ownership ratio for expenses exceeding 5 million VND");
        maintenanceInfo.put("insurance", "PVI – Comprehensive physical damage insurance package");
        data.put("maintenance", maintenanceInfo);


        // Dispute info
        Map<String, Object> disputeInfo = new HashMap<>();
        disputeInfo.put("voting", "Majority voting based on ownership ratio; in case of a 50/50 tie, priority is given to contribution history");
        data.put("dispute", disputeInfo);


        // Owner info
        List<Map<String, Object>> owners = shares.stream().map(share -> {
            Map<String, Object> owner = new HashMap<>();
            owner.put("userId", share.getUser().getUserId());
            owner.put("name", share.getUser().getFullName());
            owner.put("phone", share.getUser().getPhoneNumber());
            owner.put("email", share.getUser().getEmail());
            owner.put("idNo", "—"); // Placeholder
            owner.put("share", share.getOwnershipPercentage());
            owner.put("userRole", share.getGroupRole().name()); // Thêm userRole
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

        StringBuilder terms = new StringBuilder();

// 1. CAPITAL CONTRIBUTION & OPERATING FUND
        terms.append("1. CAPITAL CONTRIBUTION & OPERATING FUND\n");
        if (vehicle != null) {
            terms.append("- Vehicle value: ").append(formatCurrency(vehicle.getVehicleValue())).append("\n");
        } else {
            terms.append("- Vehicle value: To be updated later\n");
        }
        terms.append("- Deposit amount: ").append(formatCurrency(calculateDepositAmount(group))).append("\n");
        terms.append("Term: 1 year (minimum 1 month, maximum 5 years)\n");
        terms.append("- Contribution principle: According to ownership ratio\n");
        terms.append(
                """
                        
                        All expenses for maintenance, charging, and cleaning are paid from the Group Fund; \
                        any personal expenses (if applicable) are settled individually via offset transactions.
                        """
        );
        terms.append(
                "\nNote: The full deposit must be paid before the contract becomes active and legally effective.\n\n"
        );

// 2. USAGE RIGHTS & BOOKING SCHEDULE
        terms.append("2. USAGE RIGHTS & BOOKING SCHEDULE\n");
        terms.append(
                """
                        Vehicle usage must be booked through the system. \
                        Each member agrees to follow the confirmed schedule and return the vehicle on time.
                        Booking rules: Each co-owner’s weekly quota is 164 hours × ownership ratio; \
                        minimum booking duration is 1 hour and maximum is 3 days (72 hours); \
                        maximum 3 bookings per week per member; \
                        rest time between bookings: +2h if previous trip > 4h, +1h if ≤ 4h; \
                        no overlapping bookings (First Come, First Served); \
                        up to 2 weeks advance booking allowed; \
                        the system reserves 4 hours every Sunday for maintenance.
                        
                        """
        );

// 3. MAINTENANCE, REPAIR & INSURANCE
        terms.append("3. MAINTENANCE, REPAIR & INSURANCE\n");
        terms.append(
                """
                        The vehicle shall be maintained periodically according to the manufacturer’s recommendations. \
                        Expense approval requires a majority vote (>50%) based on ownership ratio for costs exceeding 5 million VND. \
                        Insurance provider: PVI – Comprehensive physical damage coverage.
                        
                        """
        );

// 4. DISPUTE RESOLUTION
        terms.append("4. DISPUTE RESOLUTION\n");
        terms.append(
                """
                        All disputes shall be recorded in the system and prioritized for internal mediation within the group. \
                        Voting mechanism: Majority decision based on ownership ratio; \
                        in case of a 50/50 tie, priority is given to contribution history. \
                        Final authority follows applicable Vietnamese law.
                        
                        """
        );

// 5. GENERAL TERMS
        terms.append("5. GENERAL TERMS\n");
        terms.append("- The contract takes effect once all co-owners have agreed and signed.\n");
        terms.append("- Activation: The contract becomes active only after all members have paid the full deposit as required.\n");
        terms.append("- System verification: The system will revalidate and officially activate the contract after confirming all deposits.\n");
        terms.append("- Appendices (if any) are integral parts of this contract.\n");
        terms.append("- The system records version history, signing timestamp, and digital identities of signatories.\n\n");

// 6. SIGNATURES
        terms.append("6. SIGNATURES\n");
        terms.append("Group Representative: Admin Group\n");
        terms.append("Date of Signing: ").append(formatDate(LocalDate.now())).append("\n");
        terms.append(
                "This contract takes effect on the signing date and is acknowledged by all members of the co-ownership group.\n"
        );

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
     * Kiểm tra điều kiện generate contract
     * Business rule:
     * 1. Số thành viên thực tế phải bằng memberCapacity
     * 2. Tổng tỷ lệ sở hữu phải bằng 100%
     * 3. Không có thành viên nào có tỷ lệ sở hữu 0% hoặc null
     * 4. Mỗi thành viên phải có tỷ lệ sở hữu tối thiểu 1%
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

        // Kiểm tra tổng tỷ lệ sở hữu phải bằng 100% và không có thành viên nào có 0% hoặc null
        BigDecimal totalOwnershipPercentage = getBigDecimal(shares);

        BigDecimal expectedTotal = new BigDecimal("100.00");
        if (totalOwnershipPercentage.compareTo(expectedTotal) != 0) {
            throw new IllegalStateException(
                    String.format("Cannot generate contract: Total ownership percentage must be exactly 100%%, but found %s%%. Please adjust ownership percentages.",
                            totalOwnershipPercentage)
            );
        }
    }



    private static BigDecimal getBigDecimal(List<OwnershipShare> shares) {
        BigDecimal totalOwnershipPercentage = BigDecimal.ZERO;
        BigDecimal minimumOwnership = new BigDecimal("1.00"); // Tối thiểu 1%

        for (OwnershipShare share : shares) {
            if (share.getOwnershipPercentage() == null) {
                throw new IllegalStateException(
                        "Cannot generate contract: Member " + share.getUser().getFullName() +
                                " has null ownership percentage. All members must have ownership percentage set.");
            }

            if (share.getOwnershipPercentage().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalStateException(
                        "Cannot generate contract: Member " + share.getUser().getFullName() +
                                " has 0% ownership. All members must have ownership percentage > 0%.");
            }

            if (share.getOwnershipPercentage().compareTo(minimumOwnership) < 0) {
                throw new IllegalStateException(
                        "Cannot generate contract: Member " + share.getUser().getFullName() +
                                " has ownership percentage " + share.getOwnershipPercentage() +
                                "% which is below minimum 1%. Each member must own at least 1%.");
            }

            totalOwnershipPercentage = totalOwnershipPercentage.add(share.getOwnershipPercentage());
        }
        return totalOwnershipPercentage;
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

        // Chỉ duyệt contract ở trạng thái SIGNED
        if (contract.getApprovalStatus() != ContractApprovalStatus.SIGNED) {
            throw new IllegalStateException("Only signed contracts can be approved");
        }

        List<OwnershipShare> shares = getSharesByGroupId(contract.getGroup().getGroupId());
        List<OwnershipShare> unpaidShares = shares.stream()
                .filter(share -> share.getDepositStatus() != DepositStatus.PAID)
                .toList();

        if (!unpaidShares.isEmpty()) {
            String unpaidMembers = unpaidShares.stream()
                    .map(share -> Optional.ofNullable(share.getUser())
                            .map(User::getFullName)
                            .orElse("User " + share.getId().getUserId()))
                    .collect(Collectors.joining(", "));

            throw new IllegalStateException(String.format(
                    "Cannot approve contract: deposit pending for %s",
                    unpaidMembers.isBlank() ? "one or more members" : unpaidMembers
            ));
        }

        contract.setApprovalStatus(ContractApprovalStatus.APPROVED);
        contract.setApprovedBy(admin);
        contract.setApprovedAt(LocalDateTime.now());
        contract.setIsActive(true);

        Contract savedContract = contractRepository.saveAndFlush(contract);

        // Notify all members that the contract has been approved by system admin
        if (notificationOrchestrator != null) {
            Long groupId = savedContract.getGroup().getGroupId();
            Map<String, Object> emailData = notificationOrchestrator.buildContractEmailData(savedContract);
            notificationOrchestrator.sendGroupNotification(
                    groupId,
                    NotificationType.CONTRACT_APPROVED,
                    "Contract Approved by System Admin",
                    "The group's contract has been approved by the system administrator. The contract is now active.",
                    emailData
            );
        }

        return convertToDTO(savedContract);
    }

    @Transactional
    public ContractDTO rejectContract(Long contractId, String reason, User admin) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // Chỉ reject contract ở trạng thái SIGNED
        if (contract.getApprovalStatus() != ContractApprovalStatus.SIGNED) {
            throw new IllegalStateException("Only signed contracts can be rejected");
        }

        // Sau khi reject, contract quay về trạng thái PENDING
        contract.setApprovalStatus(ContractApprovalStatus.PENDING);
        contract.setApprovedBy(admin);
        contract.setApprovedAt(LocalDateTime.now());
        contract.setRejectionReason(reason);
        contract.setIsActive(false);

        Contract savedContract = contractRepository.saveAndFlush(contract);

        // Hoàn tiền cọc cho các thành viên đã đóng khi contract bị reject
        Long groupId = savedContract.getGroup().getGroupId();
        List<OwnershipShare> shares = ownershipShareRepository.findByGroupGroupId(groupId);
        depositPaymentService.refundDepositsForGroup(shares, groupId);

        // Gửi notification cho tất cả thành viên với lý do reject
        if (notificationOrchestrator != null) {
            Map<String, Object> emailData = notificationOrchestrator.buildContractEmailData(savedContract);
            notificationOrchestrator.sendGroupNotification(
                    groupId,
                    NotificationType.CONTRACT_REJECTED,
                    "Contract Rejected",
                    "The group's contract has been rejected by the administrator. Deposits have been refunded to members who paid.",
                    emailData
            );
        }

        return convertToDTO(savedContract);
    }

    /**
     * Xử lý duyệt/từ chối hợp đồng với validation đầy đủ
     * Dùng cho AdminController để giảm logic phức tạp
     */
    @Transactional
    public ContractDTO processContractApproval(Long contractId, String action, String reason, User admin) {
        // Validate contract exists
        if (!contractExists(contractId)) {
            throw new ResourceNotFoundException("Contract not found with ID: " + contractId);
        }

        // Xử lý theo action
        return switch (action.toUpperCase()) {
            case "APPROVE" -> approveContract(contractId, admin);
            case "REJECT" -> {
                validateRejectionReason(reason);
                yield rejectContract(contractId, reason.trim(), admin);
            }
            default -> throw new InvalidContractActionException(
                    "Invalid action. Only 'APPROVE' or 'REJECT' are allowed"
            );
        };
    }

    /**
     * Validate lý do từ chối hợp đồng
     */
    private void validateRejectionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidContractActionException(
                    "Rejection reason is required when rejecting a contract"
            );
        }

        if (reason.trim().length() < 10) {
            throw new InvalidContractActionException(
                    "Rejection reason must be at least 10 characters"
            );
        }
    }


    private ContractDTO convertToDTO(Contract contract) {
        ContractDTO dto = new ContractDTO();
        dto.setId(contract.getId());
        dto.setGroupId(contract.getGroup().getGroupId());
        dto.setGroupName(contract.getGroup().getGroupName());
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

        List<Map<String, Object>> memberDetails = new ArrayList<>();

        for (OwnershipShare share : shares) {
            Map<String, Object> memberInfo = new LinkedHashMap<>();

            // Tính tiền cọc cần đóng của từng thành viên
            BigDecimal memberRequired = requiredAmount
                    .multiply(share.getOwnershipPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Kiểm tra trạng thái đóng cọc
            boolean isPaid = share.getDepositStatus() == DepositStatus.PAID;

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
                totalPaid.multiply(BigDecimal.valueOf(100)).divide(requiredAmount, 1, RoundingMode.HALF_UP).doubleValue()));
        result.put("memberDetails", memberDetails);

        return result;
    }


    public List<ContractDTO> getAllContracts() {
        return contractRepository.findAllSortedByStatus().stream()
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

    /**
     * ADMIN: Lấy tất cả contracts của một group
     */
    public List<ContractDTO> getContractsByGroupForAdmin(Long groupId) {
        return contractRepository.findAllByGroup_GroupId(groupId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Approve tất cả feedbacks PENDING của contract (set về APPROVED)
     * Sử dụng khi admin đã chỉnh sửa contract dựa trên feedbacks
     */
    private void approveMemberFeedbacks(Long contractId) {
        List<ContractFeedback> feedbacks = feedbackRepository.findByContractIdAndStatus(
                contractId, MemberFeedbackStatus.PENDING);
        if (!feedbacks.isEmpty()) {
            feedbacks.forEach(f -> {
                f.setStatus(MemberFeedbackStatus.APPROVED); // Admin đã chỉnh sửa contract
                f.setUpdatedAt(LocalDateTime.now());
            });
            feedbackRepository.saveAll(feedbacks);
        }
    }

    /**
     * Reject tất cả feedbacks PENDING của contract (set về REJECTED)
     * Sử dụng khi admin quyết định không chỉnh sửa contract (trả về không sửa được)
     */
    public void rejectMemberFeedbacks(Long contractId) {
        List<ContractFeedback> feedbacks = feedbackRepository.findByContractIdAndStatus(
                contractId, MemberFeedbackStatus.PENDING);
        if (!feedbacks.isEmpty()) {
            feedbacks.forEach(f -> {
                f.setStatus(MemberFeedbackStatus.REJECTED); // Admin không chỉnh sửa contract
                f.setUpdatedAt(LocalDateTime.now());
            });
            feedbackRepository.saveAll(feedbacks);
        }
    }

    /**
     * Admin approve một feedback cụ thể (theo feedbackId)
     * Chỉ có thể approve feedbacks có status = PENDING
     */
    @Transactional
    public Map<String, Object> approveFeedback(Long feedbackId) {
        ContractFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
        
        if (feedback.getStatus() != MemberFeedbackStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING feedbacks can be approved. Current status: " + feedback.getStatus()
            );
        }
        
        feedback.setStatus(MemberFeedbackStatus.APPROVED);
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Feedback approved successfully");
        result.put("feedback", Map.of(
                "feedbackId", feedback.getId(),
                "status", feedback.getStatus(),
                "reactionType", feedback.getReactionType(),
                "userId", feedback.getUser().getUserId(),
                "contractId", feedback.getContract().getId()
        ));
        
        return result;
    }

    /**
     * Admin reject một feedback cụ thể (theo feedbackId)
     * Chỉ có thể reject feedbacks có status = PENDING
     */
    @Transactional
    public Map<String, Object> rejectFeedback(Long feedbackId, String adminNote) {
        ContractFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
        
        if (feedback.getStatus() != MemberFeedbackStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING feedbacks can be rejected. Current status: " + feedback.getStatus()
            );
        }
        
        feedback.setStatus(MemberFeedbackStatus.REJECTED);
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Feedback rejected successfully");
        result.put("feedback", Map.of(
                "feedbackId", feedback.getId(),
                "status", feedback.getStatus(),
                "reactionType", feedback.getReactionType(),
                "userId", feedback.getUser().getUserId(),
                "contractId", feedback.getContract().getId(),
                "adminNote", adminNote != null ? adminNote : ""
        ));
        
        return result;
    }

    /**
     * Invalidate tất cả feedbacks của contract (set về PENDING)
     * Sử dụng khi admin update contract - members cần review lại contract mới
     */
    private void invalidateMemberFeedbacks(Long contractId) {
        List<ContractFeedback> feedbacks =
                feedbackRepository.findByContractId(contractId);
        if (!feedbacks.isEmpty()) {
            feedbacks.forEach(f -> {
                f.setStatus(MemberFeedbackStatus.PENDING);
                f.setReactionType(null); // Clear reaction
                f.setReason(null); // Clear reason
                f.setUpdatedAt(LocalDateTime.now());
            });
            feedbackRepository.saveAll(feedbacks);
        }
    }

    /**
     * Kiểm tra và validate contract có thể chỉnh sửa được không
     * Throw exception nếu không thể chỉnh sửa
     */
    private void validateContractEditable(Contract contract, String errorMessageTemplate) {
        if (contract.getApprovalStatus() == ContractApprovalStatus.PENDING) {
            return;
        }

        if (contract.getApprovalStatus() == ContractApprovalStatus.PENDING_MEMBER_APPROVAL) {
            // Chỉ đếm rejections chưa được admin xử lý (status = PENDING)
            long rejectionCount = feedbackRepository.countByContractIdAndStatusAndReactionType(
                    contract.getId(), MemberFeedbackStatus.PENDING, ReactionType.DISAGREE);
            if (rejectionCount > 0) {
                // Invalidate feedbacks when admin updates contract after rejections
                invalidateMemberFeedbacks(contract.getId());
                return;
            }
        }

        throw new IllegalStateException(String.format(errorMessageTemplate, contract.getApprovalStatus()));
    }

    /**
     * Member approve hoặc reject contract
     */
    @Transactional
    public Map<String, Object> submitMemberFeedback(Long contractId, Long userId, ContractMemberFeedbackRequestDTO request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // Kiểm tra contract phải ở trạng thái PENDING_MEMBER_APPROVAL
        if (contract.getApprovalStatus() != ContractApprovalStatus.PENDING_MEMBER_APPROVAL) {
            throw new IllegalStateException(
                    "Contract is not in PENDING_MEMBER_APPROVAL status. Current status: " + contract.getApprovalStatus()
            );
        }

        // Kiểm tra user là member của group
        Long groupId = contract.getGroup().getGroupId();
        OwnershipShare share = shareRepository.findById(
                new OwnershipShareId(userId, groupId)
        ).orElseThrow(() -> new IllegalStateException("User is not a member of this group"));

        // Không cho phép admin group submit feedback (admin đã ký rồi)
        if (share.getGroupRole() == GroupRole.ADMIN) {
            throw new IllegalStateException("Group admin cannot submit feedback. Admin has already signed the contract.");
        }

        // Validate request
        if (!request.isValid()) {
            throw new IllegalArgumentException("Invalid feedback. If DISAGREE, reason must be at least 10 characters.");
        }

        ReactionType reactionType =
                "AGREE".equalsIgnoreCase(request.reactionType()) 
                    ? ReactionType.AGREE
                    : ReactionType.DISAGREE;

        // Kiểm tra đã submit feedback chưa
        var existingFeedback = feedbackRepository.findByContractIdAndUser_UserId(contractId, userId);
        ContractFeedback feedback;
        
        if (existingFeedback.isPresent()) {
            feedback = existingFeedback.get();
            feedback.setStatus(MemberFeedbackStatus.PENDING); // Member mới nộp feedback
            feedback.setReactionType(reactionType);
            feedback.setReason(request.reason());
            feedback.setUpdatedAt(LocalDateTime.now());
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            
            feedback = ContractFeedback.builder()
                    .contract(contract)
                    .user(user)
                    .status(MemberFeedbackStatus.PENDING) // Member mới nộp feedback
                    .reactionType(reactionType)
                    .reason(request.reason())
                    .build();
        }

        feedbackRepository.save(feedback);

        // Kiểm tra xem tất cả members đã approve chưa
        checkAndAutoSignIfAllApproved(contract);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Feedback submitted successfully");
        result.put("feedback", Map.of(
                "feedbackId", feedback.getId(),
                "status", feedback.getStatus(),
                "reactionType", feedback.getReactionType(),
                "reason", feedback.getReason() != null ? feedback.getReason() : "",
                "submittedAt", feedback.getSubmittedAt()
        ));

        return result;
    }

    /**
     * Kiểm tra và tự động chuyển sang SIGNED nếu tất cả members đã approve
     */
    @Transactional
    protected void checkAndAutoSignIfAllApproved(Contract contract) {
        Long groupId = contract.getGroup().getGroupId();
        List<OwnershipShare> allMembers = shareRepository.findByGroup_GroupId(groupId);
        
        // Lọc ra members (không tính admin)
        List<OwnershipShare> members = allMembers.stream()
                .filter(share -> share.getGroupRole() != GroupRole.ADMIN)
                .toList();

        if (members.isEmpty()) {
            return;
        }

        // Đếm số members đã agree và chưa được admin xử lý (status = PENDING)
        long approvedCount = feedbackRepository.countByContractIdAndStatusAndReactionType(
                contract.getId(), 
                MemberFeedbackStatus.PENDING,
                ReactionType.AGREE
        );

        // Nếu tất cả members đã agree, chuyển sang SIGNED
        if (approvedCount == members.size()) {
            contract.setApprovalStatus(ContractApprovalStatus.SIGNED);
            contract.setUpdatedAt(LocalDateTime.now());
            contractRepository.saveAndFlush(contract);

            // Gửi notification
            if (notificationOrchestrator != null) {
                notificationOrchestrator.sendGroupNotification(
                        groupId,
                        NotificationType.CONTRACT_CREATED,
                        "All Members Approved Contract",
                        "All members have approved the contract. The contract is now pending system admin approval.",
                        null
                );
            }
        }
    }

    /**
     * Lấy tất cả feedback của members cho contract
     */
    public Map<String, Object> getContractFeedbacks(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        List<ContractFeedback> feedbacks =
                feedbackRepository.findByContractId(contractId);

        List<OwnershipShare> allMembers = shareRepository.findByGroup_GroupId(contract.getGroup().getGroupId());
        List<OwnershipShare> members = allMembers.stream()
                .filter(share -> share.getGroupRole() != GroupRole.ADMIN)
                .toList();

        List<Map<String, Object>> feedbackList = feedbacks.stream()
                .map(f -> {
                    Map<String, Object> fb = new HashMap<>();
                    fb.put("userId", f.getUser().getUserId());
                    fb.put("fullName", f.getUser().getFullName());
                    fb.put("email", f.getUser().getEmail());
                    fb.put("status", f.getStatus());
                    fb.put("reactionType", f.getReactionType());
                    fb.put("reason", f.getReason() != null ? f.getReason() : "");
                    fb.put("submittedAt", f.getSubmittedAt());
                    return fb;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("contractId", contractId);
        result.put("contractStatus", contract.getApprovalStatus());
        result.put("totalMembers", members.size());
        result.put("totalFeedbacks", feedbacks.size());
        // Đếm feedbacks theo status và reactionType
        result.put("pendingAgreeCount", feedbackRepository.countByContractIdAndStatusAndReactionType(
                contractId, MemberFeedbackStatus.PENDING, ReactionType.AGREE));
        result.put("pendingDisagreeCount", feedbackRepository.countByContractIdAndStatusAndReactionType(
                contractId, MemberFeedbackStatus.PENDING, ReactionType.DISAGREE));
        result.put("approvedCount", feedbackRepository.countByContractIdAndStatus(
                contractId, MemberFeedbackStatus.APPROVED));
        result.put("rejectedCount", feedbackRepository.countByContractIdAndStatus(
                contractId, MemberFeedbackStatus.REJECTED));
        result.put("feedbacks", feedbackList);
        result.put("pendingMembers", members.stream()
                .filter(m -> !feedbackRepository.existsByContractIdAndUser_UserId(contractId, m.getUser().getUserId()))
                .map(m -> Map.of(
                        "userId", m.getUser().getUserId(),
                        "fullName", m.getUser().getFullName(),
                        "email", m.getUser().getEmail()
                ))
                .toList());

        return result;
    }

    /**
     * Lấy tất cả feedback của members cho contract theo groupId
     * Mỗi group hiện chỉ có 1 contract, nên hàm này ánh xạ groupId -> contractId rồi tái sử dụng logic cũ
     */
    public Map<String, Object> getContractFeedbacksByGroup(Long groupId) {
        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found for group: " + groupId));
        return getContractFeedbacks(contract.getId());
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
