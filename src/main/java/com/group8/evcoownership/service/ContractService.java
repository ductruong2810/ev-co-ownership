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
    private final ContractFeedbackHistoryRepository feedbackHistoryRepository;

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
    public ContractDetailResponseDTO getContractInfoDetail(Long groupId) {
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
        List<ContractDetailResponseDTO.MemberInfo> members = shares.stream()
                .map(share -> {
                    User user = userRepository.findById(share.getUser().getUserId()).orElse(null);
                    return ContractDetailResponseDTO.MemberInfo.builder()
                            .userId(share.getUser().getUserId())
                            .fullName(user != null ? user.getFullName() : null)
                            .email(user != null ? user.getEmail() : null)
                            .userRole(share.getGroupRole())
                            .ownershipPercentage(share.getOwnershipPercentage())
                            .depositStatus(share.getDepositStatus())
                            .joinDate(share.getJoinDate())
                            .build();
                })
                .toList();

        ContractDetailResponseDTO.ContractInfo contractInfo = ContractDetailResponseDTO.ContractInfo.builder()
                .contractId(contract.getId())
                .terms(contract.getTerms())
                .requiredDepositAmount(contract.getRequiredDepositAmount())
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .depositDeadline(computeDepositDeadlineSafe(contract))
                .isActive(contract.getIsActive())
                .approvalStatus(contract.getApprovalStatus())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();

        ContractDetailResponseDTO.GroupInfo groupInfo = ContractDetailResponseDTO.GroupInfo.builder()
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .status(group.getStatus())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();

        return ContractDetailResponseDTO.builder()
                .contract(contractInfo)
                .group(groupInfo)
                .members(members)
                .build();
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
    public ApiResponseDTO<ContractUpdateResponseDTO> updateContract(Long groupId, ContractUpdateRequestDTO request) {
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
        
        // Cập nhật terms để phản ánh thay đổi deposit amount và term
        String updatedTerms = updateDepositAmountInTerms(contract.getTerms(), calculatedDepositAmount);
        updatedTerms = updateTermInTerms(updatedTerms, request.startDate(), request.endDate());
        contract.setTerms(updatedTerms);
        
        Contract savedContract = contractRepository.saveAndFlush(contract);

        // Tính toán và trả về term mới
        String termLabel = calculateTermLabel(savedContract.getStartDate(), savedContract.getEndDate());
        String depositCalculationExplanation = "Deposit amount is automatically calculated by the system: " + 
                formatCurrency(calculatedDepositAmount) +
                " (10% of vehicle value or calculated based on number of members).";
        String termExplanation = "Contract term: " + termLabel + 
                " (from " + formatDate(savedContract.getStartDate()) + 
                " to " + formatDate(savedContract.getEndDate()) + ")";

        ContractUpdateResponseDTO contractData = ContractUpdateResponseDTO.builder()
                .contractId(savedContract.getId())
                .startDate(savedContract.getStartDate())
                .endDate(savedContract.getEndDate())
                .requiredDepositAmount(savedContract.getRequiredDepositAmount())
                .calculatedDepositAmount(calculatedDepositAmount)
                .depositCalculationExplanation(depositCalculationExplanation)
                .term(termLabel)
                .termExplanation(termExplanation)
                .approvalStatus(savedContract.getApprovalStatus())
                .updatedAt(savedContract.getUpdatedAt())
                .build();

        return ApiResponseDTO.<ContractUpdateResponseDTO>builder()
                .success(true)
                .message("Contract updated successfully")
                .data(contractData)
                .build();
    }

    /**
     * Cập nhật terms của hợp đồng (chỉ cho phép khi contract ở trạng thái PENDING hoặc PENDING_MEMBER_APPROVAL có rejections)
     */
    @Transactional
    public ApiResponseDTO<ContractUpdateResponseDTO> updateContractTerms(Long groupId, ContractTermsUpdateRequestDTO request) {
        if (request.terms() == null || request.terms().trim().isEmpty()) {
            throw new IllegalArgumentException("Terms cannot be blank");
        }

        Contract contract = getContractByGroup(groupId);

        validateContractEditable(contract, "Cannot update contract terms: Contract is in %s status. Only PENDING contracts or PENDING_MEMBER_APPROVAL contracts with rejections can be updated.");

        contract.setTerms(request.terms().trim());
        contract.setUpdatedAt(LocalDateTime.now());

        Contract savedContract = contractRepository.saveAndFlush(contract);

        ContractUpdateResponseDTO contractData = ContractUpdateResponseDTO.builder()
                .contractId(savedContract.getId())
                .approvalStatus(savedContract.getApprovalStatus())
                .updatedAt(savedContract.getUpdatedAt())
                .build();

        return ApiResponseDTO.<ContractUpdateResponseDTO>builder()
                .success(true)
                .message("Contract terms updated successfully")
                .data(contractData)
                .build();
    }

    /**
     * ADMIN-ONLY: Cập nhật theo contractId (thay vì groupId)
     */
    @Transactional
    public ApiResponseDTO<ContractUpdateResponseDTO> updateContractByAdminByContractId(Long contractId, ContractAdminUpdateRequestDTO request) {
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

    private ApiResponseDTO<ContractUpdateResponseDTO> updateContractAdminCommon(Contract contract, ContractAdminUpdateRequestDTO request) {
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

        Contract saved = contractRepository.saveAndFlush(contract);

        String termLabel = calculateTermLabel(saved.getStartDate(), saved.getEndDate());
        ContractUpdateResponseDTO contractData = ContractUpdateResponseDTO.builder()
                .contractId(saved.getId())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .requiredDepositAmount(saved.getRequiredDepositAmount())
                .approvalStatus(saved.getApprovalStatus())
                .updatedAt(saved.getUpdatedAt())
                .term(termLabel)
                .build();

        return ApiResponseDTO.<ContractUpdateResponseDTO>builder()
                .success(true)
                .message("Contract updated successfully by admin")
                .data(contractData)
                .build();
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
    public ApiResponseDTO<ResubmitMemberApprovalResponseDTO> resubmitMemberApproval(Long contractId, String adminNote) {
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

        ResubmitMemberApprovalResponseDTO responseData = ResubmitMemberApprovalResponseDTO.builder()
                .contractId(saved.getId())
                .groupId(saved.getGroup().getGroupId())
                .approvalStatus(saved.getApprovalStatus())
                .feedbacksInvalidated(true) // Feedbacks set to PENDING, members need to review again
                .build();

        return ApiResponseDTO.<ResubmitMemberApprovalResponseDTO>builder()
                .success(true)
                .message("Resubmitted for member approval")
                .data(responseData)
                .build();
    }
    /**
     * Lấy thông tin tính toán deposit amount cho group admin
     * Bao gồm giá trị tính toán tự động và giải thích công thức
     */
    public DepositCalculationInfoDTO getDepositCalculationInfo(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        
        BigDecimal calculatedAmount = depositCalculationService.calculateRequiredDepositAmount(group);
        
        DepositCalculationInfoDTO.DepositCalculationInfoDTOBuilder builder = DepositCalculationInfoDTO.builder()
                .calculatedDepositAmount(calculatedAmount)
                .formattedAmount(formatCurrency(calculatedAmount));
        
        // Giải thích công thức tính toán
        StringBuilder explanation = new StringBuilder();
        if (vehicle != null && vehicle.getVehicleValue() != null && vehicle.getVehicleValue().compareTo(BigDecimal.ZERO) > 0) {
            explanation.append("Formula: Vehicle value × 10% = ")
                    .append(formatCurrency(vehicle.getVehicleValue()))
                    .append(" × 10% = ")
                    .append(formatCurrency(calculatedAmount));
            builder.calculationMethod("VEHICLE_VALUE_PERCENTAGE")
                    .vehicleValue(vehicle.getVehicleValue())
                    .percentage("10%");
        } else {
            explanation.append("Formula: Base amount + (Number of members × 10% × Base amount) = ")
                    .append(formatCurrency(calculatedAmount));
            builder.calculationMethod("MEMBER_CAPACITY")
                    .memberCapacity(group.getMemberCapacity());
        }
        
        builder.explanation(explanation.toString())
                .note("You can override this value when updating the contract. This value is the total deposit for the entire group.");
        
        return builder.build();
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
    public ContractInfoResponseDTO getContractInfo(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        // Kiểm tra có contract không
        Contract contract = contractRepository.findByGroupGroupId(groupId).orElse(null);

        ContractInfoResponseDTO.ContractInfoResponseDTOBuilder builder = ContractInfoResponseDTO.builder()
                .contractId(contract != null ? contract.getId() : null)
                .groupId(groupId)
                .groupName(group.getGroupName())
                .templateId(null);

        if (contract != null) {
            builder.startDate(contract.getStartDate())
                    .endDate(contract.getEndDate())
                    .terms(contract.getTerms())
                    .requiredDepositAmount(contract.getRequiredDepositAmount())
                    .depositDeadline(computeDepositDeadlineSafe(contract))
                    .isActive(contract.getIsActive())
                    .approvalStatus(contract.getApprovalStatus())
                    .createdAt(contract.getCreatedAt())
                    .updatedAt(contract.getUpdatedAt());
        } else {
            builder.requiredDepositAmount(depositCalculationService.calculateRequiredDepositAmount(group))
                    .isActive(false);
        }

        return builder.build();
    }


    /**
     * Tự động ký contract cho group
     * Điều kiện: Group đã có đủ thành viên và vehicle
     */
    @Transactional
    public AutoSignContractResponseDTO autoSignContract(Long groupId) {
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

        return AutoSignContractResponseDTO.builder()
                .success(true)
                .contractId(savedContract.getId())
                .contractNumber(generateContractNumber(savedContract.getId()))
                .status("PENDING_MEMBER_APPROVAL")
                .signedAt(LocalDateTime.now())
                .message("Contract has been signed by group admin. Waiting for member approvals.")
                .build();
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
    public AutoSignConditionsResponseDTO checkAutoSignConditions(Long groupId) {
        OwnershipGroup group = getGroupById(groupId);

        // Kiểm tra số thành viên
        List<OwnershipShare> shares = getSharesByGroupId(groupId);
        Integer memberCapacity = group.getMemberCapacity();
        boolean hasEnoughMembers = shares.size() == memberCapacity;

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

        // Kiểm tra có vehicle không
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        boolean hasVehicle = vehicle != null && vehicle.getVehicleValue() != null && vehicle.getVehicleValue().compareTo(BigDecimal.ZERO) > 0;

        // Kiểm tra contract status
        Contract contract = contractRepository.findByGroupGroupId(groupId).orElse(null);
        boolean canSign = contract == null || contract.getApprovalStatus() == ContractApprovalStatus.PENDING;

        // Tổng kết
        boolean allConditionsMet = hasEnoughMembers && hasCorrectOwnershipPercentage && hasVehicle && canSign;
        return AutoSignConditionsResponseDTO.builder()
                .hasEnoughMembers(hasEnoughMembers)
                .currentMembers(shares.size())
                .requiredMembers(memberCapacity)
                .hasCorrectOwnershipPercentage(hasCorrectOwnershipPercentage)
                .totalOwnershipPercentage(totalOwnershipPercentage)
                .expectedOwnershipPercentage(expectedTotal)
                .hasValidOwnershipPercentages(hasValidOwnershipPercentages)
                .membersWithZeroOrNullPercentage(membersWithZeroOrNullPercentage)
                .hasVehicle(hasVehicle)
                .vehicleValue(vehicle != null ? vehicle.getVehicleValue() : BigDecimal.ZERO)
                .canSign(canSign)
                .contractStatus(contract != null ? contract.getApprovalStatus().name() : "NO_CONTRACT")
                .allConditionsMet(allConditionsMet)
                .canAutoSign(allConditionsMet)
                .build();
    }

    /**
     * Tự động kiểm tra và ký contract nếu đủ điều kiện
     * Method này có thể được gọi từ scheduler hoặc event listener
     */
    @Transactional
    public AutoSignOutcomeResponseDTO checkAndAutoSignContract(Long groupId) {
        AutoSignConditionsResponseDTO conditions = checkAutoSignConditions(groupId);

        if (Boolean.TRUE.equals(conditions.getCanAutoSign())) {
            AutoSignContractResponseDTO autoSignResult = autoSignContract(groupId);
            return AutoSignOutcomeResponseDTO.builder()
                    .success(autoSignResult.getSuccess())
                    .message(autoSignResult.getMessage())
                    .contractId(autoSignResult.getContractId())
                    .contractNumber(autoSignResult.getContractNumber())
                    .status(autoSignResult.getStatus())
                    .signedAt(autoSignResult.getSignedAt())
                    .build();
        } else {
            return AutoSignOutcomeResponseDTO.builder()
                    .success(false)
                    .message("Contract cannot be auto-signed yet")
                    .conditions(conditions)
                    .build();
        }
    }

    // Removed manual approval methods - contracts are now auto-approved after signing

    // ========== CONTRACT GENERATION METHODS ==========

    /**
     * Generate contract data (chỉ tạo nội dung, không save DB)
     */
    public ContractGenerationResponseDTO generateContractData(Long groupId, Long userId) {
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
        ContractGenerationResponseDTO baseData = prepareContractData(groupId, existingContract);

        ContractGenerationResponseDTO.ContractGenerationResponseDTOBuilder builder = baseData.toBuilder()
                .groupId(groupId)
                .userId(userId)
                .terms(terms)
                .startDate(startDate)
                .endDate(endDate)
                .contractNumber("EVS-" + groupId + "-" + System.currentTimeMillis())
                .generatedAt(LocalDateTime.now());

        if (existingContract.isPresent()) {
            Contract contract = existingContract.get();
            builder.contractId(contract.getId())
                    .status(contract.getApprovalStatus())
                    .isActive(contract.getIsActive())
                    .savedToDatabase(true);

            if (contract.getApprovalStatus() == ContractApprovalStatus.SIGNED ||
                    contract.getApprovalStatus() == ContractApprovalStatus.APPROVED) {
                builder.signed(true)
                        .signedAt(contract.getUpdatedAt());
            }
        } else {
            builder.status(ContractApprovalStatus.PENDING)
                    .isActive(false)
                    .savedToDatabase(false)
                    .signed(false);
        }

        return builder.build();
    }


    /**
     * Chuẩn bị data cho template
     */
    private ContractGenerationResponseDTO prepareContractData(Long groupId, Optional<Contract> existingContract) {
        OwnershipGroup group = getGroupById(groupId);
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);
        List<OwnershipShare> shares = getSharesByGroupId(groupId);

        // Contract info
        ContractGenerationResponseDTO.ContractSection.ContractSectionBuilder contractSectionBuilder =
                ContractGenerationResponseDTO.ContractSection.builder()
                        .number("TBD")
                        .location("HCM")
                        .signDate(formatDate(LocalDate.now()));

        ContractGenerationResponseDTO.ContractGenerationResponseDTOBuilder responseBuilder = ContractGenerationResponseDTO.builder();

        if (existingContract.isPresent()) {
            Contract contract = existingContract.get();
            contractSectionBuilder
                    .effectiveDate(formatDate(contract.getStartDate()))
                    .endDate(formatDate(contract.getEndDate()))
                    .termLabel(calculateTermLabel(contract.getStartDate(), contract.getEndDate()))
                    .status(contract.getApprovalStatus());

            responseBuilder.contractId(contract.getId())
                    .status(contract.getApprovalStatus())
                    .isActive(contract.getIsActive())
                    .savedToDatabase(true);

            if (contract.getApprovalStatus() == ContractApprovalStatus.SIGNED ||
                    contract.getApprovalStatus() == ContractApprovalStatus.APPROVED) {
                responseBuilder.signed(true)
                        .signedAt(contract.getUpdatedAt());
            }
        } else {
            LocalDate defaultStartDate = LocalDate.now();
            LocalDate defaultEndDate = defaultStartDate.plusYears(1);
            contractSectionBuilder
                    .effectiveDate(formatDate(defaultStartDate))
                    .endDate(formatDate(defaultEndDate))
                    .termLabel(calculateTermLabel(defaultStartDate, defaultEndDate))
                    .status(ContractApprovalStatus.PENDING);

            responseBuilder.status(ContractApprovalStatus.PENDING)
                    .isActive(false)
                    .savedToDatabase(false)
                    .signed(false);
        }

        ContractGenerationResponseDTO.ContractSection contractSection = contractSectionBuilder.build();

        ContractGenerationResponseDTO.GroupSection groupSection = ContractGenerationResponseDTO.GroupSection.builder()
                .name(group.getGroupName())
                .build();

        ContractGenerationResponseDTO.VehicleSection vehicleSection = ContractGenerationResponseDTO.VehicleSection.builder()
                .model(vehicle != null ? vehicle.getBrand() + " " + vehicle.getModel() : "—")
                .plate(vehicle != null ? vehicle.getLicensePlate() : "—")
                .vin(vehicle != null ? vehicle.getChassisNumber() : "—")
                .build();

        BigDecimal depositAmount = depositCalculationService.calculateRequiredDepositAmount(group);

        ContractGenerationResponseDTO.FinanceSection financeSection = ContractGenerationResponseDTO.FinanceSection.builder()
                .vehiclePrice(vehicle != null ? vehicle.getVehicleValue() : BigDecimal.ZERO)
                .depositAmount(depositAmount)
                .contributionRule("According to ownership ratio")
                .build();

        ContractGenerationResponseDTO.MaintenanceSection maintenanceSection =
                ContractGenerationResponseDTO.MaintenanceSection.builder()
                        .approval("Approval by >50% vote based on ownership ratio for expenses exceeding 5 million VND")
                        .insurance("PVI – Comprehensive physical damage insurance package")
                        .build();

        ContractGenerationResponseDTO.DisputeSection disputeSection =
                ContractGenerationResponseDTO.DisputeSection.builder()
                        .voting("Majority voting based on ownership ratio; in case of a 50/50 tie, priority is given to contribution history")
                        .build();

        List<ContractGenerationResponseDTO.OwnerInfo> owners = shares.stream()
                .map(share -> ContractGenerationResponseDTO.OwnerInfo.builder()
                        .userId(share.getUser().getUserId())
                        .name(share.getUser().getFullName())
                        .phone(share.getUser().getPhoneNumber())
                        .email(share.getUser().getEmail())
                        .idNo("—")
                        .share(share.getOwnershipPercentage())
                        .userRole(share.getGroupRole().name())
                        .build())
                .collect(Collectors.toList());

        return responseBuilder
                .contract(contractSection)
                .group(groupSection)
                .vehicle(vehicleSection)
                .finance(financeSection)
                .maintenance(maintenanceSection)
                .dispute(disputeSection)
                .owners(owners)
                .build();
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
        Contract existingContract = contractRepository.findByGroupGroupId(groupId).orElse(null);

        LocalDate startDate = existingContract != null ? existingContract.getStartDate() : LocalDate.now();
        LocalDate endDate = existingContract != null ? existingContract.getEndDate()
                : startDate.plusYears(1);
        String termLabel = calculateTermLabel(startDate, endDate);
        String termSuffix = " (minimum 1 month, maximum 5 years)";

        BigDecimal depositAmount = (existingContract != null && existingContract.getRequiredDepositAmount() != null)
                ? existingContract.getRequiredDepositAmount()
                : calculateDepositAmount(group);

        String existingTerms = existingContract != null ? existingContract.getTerms() : null;
        if (existingTerms != null && !existingTerms.trim().isEmpty()) {
            String syncedTerms = updateDepositAmountInTerms(existingTerms, depositAmount);
            if (startDate != null && endDate != null) {
                syncedTerms = updateTermInTerms(syncedTerms, startDate, endDate);
            }
            return syncedTerms;
        }

        StringBuilder terms = new StringBuilder();

// 1. CAPITAL CONTRIBUTION & OPERATING FUND
        terms.append("1. CAPITAL CONTRIBUTION & OPERATING FUND\n");
        if (vehicle != null) {
            terms.append("- Vehicle value: ").append(formatCurrency(vehicle.getVehicleValue())).append("\n");
        } else {
            terms.append("- Vehicle value: To be updated later\n");
        }
        terms.append("- Deposit amount: ").append(formatCurrency(depositAmount)).append("\n");
        if (!"N/A".equalsIgnoreCase(termLabel)) {
            terms.append("Term: ").append(termLabel).append(termSuffix).append("\n");
        } else {
            terms.append("Term: To be updated later").append(termSuffix).append("\n");
        }
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
    public ContractDepositStatusResponseDTO checkDepositStatus(Long groupId) {
        // 1. Lấy hợp đồng
        Contract contract = getContractByGroup(groupId);
        BigDecimal requiredAmount = contract.getRequiredDepositAmount();

        // 2. Lấy danh sách thành viên
        List<OwnershipShare> shares = getSharesByGroupId(groupId);

        // 3. Tính tổng tiền đã đóng (COMPLETED deposits)
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidMembers = 0;

        List<ContractDepositStatusResponseDTO.MemberDepositStatus> memberDetails = new ArrayList<>();

        for (OwnershipShare share : shares) {
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

            memberDetails.add(ContractDepositStatusResponseDTO.MemberDepositStatus.builder()
                    .userId(share.getUser().getUserId())
                    .fullName(share.getUser().getFullName())
                    .ownershipPercentage(share.getOwnershipPercentage())
                    .requiredDeposit(memberRequired)
                    .depositStatus(share.getDepositStatus())
                    .isPaid(isPaid)
                    .build());
        }

        // 4. Tính toán
        boolean isFullyPaid = totalPaid.compareTo(requiredAmount) >= 0;
        BigDecimal remaining = isFullyPaid ? BigDecimal.ZERO : requiredAmount.subtract(totalPaid);
        int totalMembers = shares.size();
        boolean allMembersPaid = paidMembers == totalMembers;

        String paymentProgress = requiredAmount.compareTo(BigDecimal.ZERO) > 0
                ? String.format("%.1f%%",
                totalPaid.multiply(BigDecimal.valueOf(100)).divide(requiredAmount, 1, RoundingMode.HALF_UP).doubleValue())
                : "0.0%";

        return ContractDepositStatusResponseDTO.builder()
                .groupId(groupId)
                .contractId(contract.getId())
                .approvalStatus(contract.getApprovalStatus())
                .requiredDepositAmount(requiredAmount)
                .totalPaid(totalPaid)
                .remaining(remaining)
                .isFullyPaid(isFullyPaid)
                .totalMembers(totalMembers)
                .paidMembers(paidMembers)
                .allMembersPaid(allMembersPaid)
                .paymentProgress(paymentProgress)
                .memberDetails(memberDetails)
                .build();
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

    public ContractFeedbackHistoryResponseDTO getContractFeedbackHistory(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        List<ContractFeedbackHistory> allHistoryEntries =
                feedbackHistoryRepository.findByContractIdOrderByArchivedAtDesc(contractId);

        // Chỉ lấy bản ghi mới nhất của mỗi feedback để tránh hiển thị trùng lặp
        // (một feedback có thể bị reject nhiều lần, mỗi lần tạo một bản ghi history mới)
        Map<Long, ContractFeedbackHistory> latestHistoryByFeedback = allHistoryEntries.stream()
                .collect(Collectors.toMap(
                        entry -> entry.getFeedback().getId(),
                        entry -> entry,
                        (existing, replacement) -> existing // Giữ lại bản ghi đầu tiên (mới nhất vì đã sort DESC)
                ));
        
        List<ContractFeedbackHistory> historyEntries = new ArrayList<>(latestHistoryByFeedback.values());

        // Đếm từ history để có số liệu chính xác (không bị ảnh hưởng bởi việc reset feedback)
        long totalFeedbacks = feedbackHistoryRepository.countDistinctFeedbacksByContractId(contractId);
        long totalMembersSubmitted = feedbackHistoryRepository.countDistinctUsersByContractId(contractId);
        long approvedFeedbacksCount = feedbackHistoryRepository.countByContractIdAndHistoryAction(
                contractId, FeedbackHistoryAction.ADMIN_APPROVE);
        long rejectedFeedbacksCount = feedbackHistoryRepository.countByContractIdAndHistoryAction(
                contractId, FeedbackHistoryAction.ADMIN_REJECT);
        
        // Đếm trạng thái hiện tại từ feedback (không từ history)
        long acceptedCount = feedbackRepository.countByContractIdAndStatusAndReactionType(
                contractId, MemberFeedbackStatus.ACCEPTED, ReactionType.AGREE);
        long pendingDisagreeCount = feedbackRepository.countByContractIdAndStatusAndReactionType(
                contractId, MemberFeedbackStatus.PENDING, ReactionType.DISAGREE);

        // Sort lại theo archivedAt DESC để hiển thị đúng thứ tự
        historyEntries.sort((a, b) -> b.getArchivedAt().compareTo(a.getArchivedAt()));

        List<ContractFeedbackHistoryItemDTO> historyItems = historyEntries.stream()
                .map(entry -> ContractFeedbackHistoryItemDTO.builder()
                        .historyId(entry.getHistoryId())
                        .feedbackId(entry.getFeedback().getId())
                        .userId(entry.getUser().getUserId())
                        .userFullName(entry.getUser().getFullName())
                        .userEmail(entry.getUser().getEmail())
                        .userAvatarUrl(entry.getUser().getAvatarUrl())
                        .isProcessed(
                                entry.getHistoryAction() == FeedbackHistoryAction.ADMIN_APPROVE
                                        || entry.getHistoryAction() == FeedbackHistoryAction.ADMIN_REJECT
                        )
                        .status(entry.getStatus())
                        .reactionType(entry.getReactionType())
                        .reason(entry.getReason())
                        .adminNote(entry.getAdminNote())
                        .lastAdminAction(entry.getLastAdminAction())
                        .lastAdminActionAt(entry.getLastAdminActionAt())
                        .submittedAt(entry.getSubmittedAt())
                        .updatedAt(entry.getUpdatedAt())
                        .historyAction(entry.getHistoryAction())
                        .actionNote(entry.getActionNote())
                        .archivedAt(entry.getArchivedAt())
                        .build())
                .toList();

        return ContractFeedbackHistoryResponseDTO.builder()
                .contractId(contract.getId())
                .contractStatus(contract.getApprovalStatus())
                .totalMembersSubmitted(totalMembersSubmitted)
                .totalFeedbacks(totalFeedbacks)
                .acceptedCount(acceptedCount)
                .pendingDisagreeCount(pendingDisagreeCount)
                .approvedFeedbacksCount(approvedFeedbacksCount)
                .rejectedFeedbacksCount(rejectedFeedbacksCount)
                .totalEntries(historyItems.size())
                .history(historyItems)
                .build();
    }

    /**
     * Admin approve một feedback cụ thể (theo feedbackId)
     * Chỉ có thể approve feedbacks có status = PENDING
     * Invalidate tất cả feedbacks và gửi thông báo cho members để review lại contract
     */
    @Transactional
    public ApiResponseDTO<FeedbackActionResponseDTO> approveFeedback(Long feedbackId, FeedbackActionRequestDTO request) {
        String adminNote = request != null ? request.adminNote() : null;
        adminNote = (adminNote != null && !adminNote.trim().isEmpty()) ? adminNote.trim() : null;
        ContractFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
        
        if (feedback.getStatus() != MemberFeedbackStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING feedbacks can be approved. Current status: " + feedback.getStatus()
            );
        }
        
        // Kiểm tra history entry cuối cùng: chỉ cho phép approve nếu entry cuối cùng là MEMBER_REVIEW
        // (tức là member đã review lại sau khi bị reject/approve)
        Optional<ContractFeedbackHistory> latestHistory = feedbackHistoryRepository.findFirstByFeedbackIdOrderByArchivedAtDesc(feedbackId);
        if (latestHistory.isPresent()) {
            FeedbackHistoryAction lastAction = latestHistory.get().getHistoryAction();
            if (lastAction != FeedbackHistoryAction.MEMBER_REVIEW) {
                throw new IllegalStateException(
                        "Cannot approve this feedback. The member must review and resubmit the feedback first. " +
                        "Last action was: " + lastAction
                );
            }
        }
        
        Contract contract = feedback.getContract();
        
        // Đánh dấu đã xử lý và set về PENDING để coowner làm lại
        feedback.setStatus(MemberFeedbackStatus.PENDING);
        feedback.setLastAdminAction(FeedbackAdminAction.APPROVE);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setAdminNote(adminNote);
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        recordFeedbackHistorySnapshot(
                feedback,
                FeedbackHistoryAction.ADMIN_APPROVE,
                "Admin approved feedback"
        );
        
        // Invalidate tất cả feedbacks về PENDING để members review lại contract mới
        // và gửi thông báo cho tất cả members
        invalidateMemberFeedbacks(contract.getId());
        
        // Notify all group members that the contract has been updated
        if (notificationOrchestrator != null) {
            Long groupId = contract.getGroup().getGroupId();
            Map<String, Object> emailData = notificationOrchestrator.buildContractEmailData(contract);
            
            // Build message với adminNote nếu có
            String message = "The contract has been updated. Please review the updated contract terms and submit your feedback.";
            if (adminNote != null) {
                message += "\n\nAdmin note: " + adminNote;
                emailData = new HashMap<>(emailData);
                emailData.put("adminNote", adminNote);
            }
            
            notificationOrchestrator.sendGroupNotification(
                    groupId,
                    NotificationType.CONTRACT_APPROVAL_PENDING,
                    "Contract Updated - Please Review",
                    message,
                    emailData
            );
        }
        
        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);
        
        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message("Feedback approved. All members need to review the updated contract.")
                .data(feedbackData)
                .build();
    }

    /**
     * Admin reject một feedback cụ thể (theo feedbackId)
     * Chỉ có thể reject feedbacks có status = PENDING
     * Chuyển về PENDING để member làm lại và lưu adminNote vào reason
     * Gửi notification và email cho member kèm adminNote
     */
    @Transactional
    public ApiResponseDTO<FeedbackActionResponseDTO> rejectFeedback(Long feedbackId, FeedbackActionRequestDTO request) {
        String adminNote = request != null ? request.adminNote() : null;
        adminNote = (adminNote != null && !adminNote.trim().isEmpty()) ? adminNote.trim() : null;
        ContractFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));
        
        if (feedback.getStatus() != MemberFeedbackStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING feedbacks can be rejected. Current status: " + feedback.getStatus()
            );
        }
        
        // Kiểm tra history entry cuối cùng: chỉ cho phép reject nếu entry cuối cùng là MEMBER_REVIEW
        // (tức là member đã review lại sau khi bị reject/approve)
        Optional<ContractFeedbackHistory> latestHistory = feedbackHistoryRepository.findFirstByFeedbackIdOrderByArchivedAtDesc(feedbackId);
        if (latestHistory.isPresent()) {
            FeedbackHistoryAction lastAction = latestHistory.get().getHistoryAction();
            if (lastAction != FeedbackHistoryAction.MEMBER_REVIEW) {
                throw new IllegalStateException(
                        "Cannot reject this feedback. The member must review and resubmit the feedback first. " +
                        "Last action was: " + lastAction
                );
            }
        }
        
        // Đánh dấu đã xử lý, chuyển về PENDING để co-owner có thể làm lại
        feedback.setStatus(MemberFeedbackStatus.PENDING);
        feedback.setAdminNote(adminNote);
        feedback.setLastAdminAction(FeedbackAdminAction.REJECT);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);
        
        // Gửi notification và email cho member
        if (notificationOrchestrator != null) {
            Long userId = feedback.getUser().getUserId();
            Contract contract = feedback.getContract();
            
            String title = "Feedback Rejected - Please Resubmit";
            String message = "Your feedback has been rejected by admin. Please review and resubmit your feedback.";
            if (adminNote != null) {
                message += "\n\nAdmin note: " + adminNote;
            }
            
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("feedbackId", feedback.getId());
            notificationData.put("contractId", contract.getId());
            notificationData.put("contractStatus", contract.getApprovalStatus());
            notificationData.put("adminNote", adminNote != null ? adminNote : "");
            notificationData.put("reactionType", feedback.getReactionType());
            
            notificationOrchestrator.sendComprehensiveNotification(
                    userId,
                    NotificationType.CONTRACT_REJECTED,
                    title,
                    message,
                    notificationData
            );
        }

        recordFeedbackHistorySnapshot(
                feedback,
                FeedbackHistoryAction.ADMIN_REJECT,
                "Admin rejected feedback"
        );
        
        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);
        
        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message("Feedback rejected. Member can resubmit.")
                .data(feedbackData)
                .build();
    }

    /**
     * Build FeedbackActionResponseDTO từ ContractFeedback
     */
    private FeedbackActionResponseDTO buildFeedbackActionResponseDTO(ContractFeedback feedback) {
        return FeedbackActionResponseDTO.builder()
                .feedbackId(feedback.getId())
                .status(feedback.getStatus())
                .isProcessed(isFeedbackProcessed(feedback))
                .lastAdminAction(feedback.getLastAdminAction())
                .lastAdminActionAt(feedback.getLastAdminActionAt())
                .reactionType(feedback.getReactionType())
                .userId(feedback.getUser().getUserId())
                .contractId(feedback.getContract().getId())
                .reason(feedback.getReason() != null ? feedback.getReason() : "")
                .adminNote(feedback.getAdminNote() != null ? feedback.getAdminNote() : "")
                .build();
    }

    private boolean isFeedbackProcessed(ContractFeedback feedback) {
        return feedback != null && feedback.getLastAdminAction() != null;
    }

    private void recordFeedbackHistorySnapshot(ContractFeedback feedback,
                                               FeedbackHistoryAction action,
                                               String note) {
        if (feedback == null || feedback.getId() == null) {
            return;
        }

        ContractFeedbackHistory historyEntry = new ContractFeedbackHistory();
        historyEntry.setFeedback(feedback);
        historyEntry.setContract(feedback.getContract());
        historyEntry.setUser(feedback.getUser());
        historyEntry.setStatus(feedback.getStatus());
        historyEntry.setReactionType(feedback.getReactionType());
        historyEntry.setReason(feedback.getReason());
        historyEntry.setAdminNote(feedback.getAdminNote());
        historyEntry.setLastAdminAction(feedback.getLastAdminAction());
        historyEntry.setLastAdminActionAt(feedback.getLastAdminActionAt());
        historyEntry.setSubmittedAt(feedback.getSubmittedAt());
        historyEntry.setUpdatedAt(feedback.getUpdatedAt());
        historyEntry.setHistoryAction(action);
        // actionNote lưu reason của member (nếu có), nếu không thì dùng note được truyền vào
        historyEntry.setActionNote(feedback.getReason() != null && !feedback.getReason().trim().isEmpty() 
                ? feedback.getReason() 
                : note);
        historyEntry.setArchivedAt(LocalDateTime.now());

        feedbackHistoryRepository.save(historyEntry);
    }

    /**
     * Invalidate tất cả feedbacks của contract (set về PENDING và clear data)
     * Sử dụng khi admin update contract - members cần review lại contract mới
     */
    private void invalidateMemberFeedbacks(Long contractId) {
        List<ContractFeedback> feedbacks =
                feedbackRepository.findByContractId(contractId);
        if (!feedbacks.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            feedbacks.forEach(f -> {
                // Set tất cả về PENDING để members review lại contract mới
                f.setStatus(MemberFeedbackStatus.PENDING);
                f.setReactionType(null); // Clear reaction để member submit lại
                f.setReason(null); // Clear reason
                f.setAdminNote(null); // Clear admin note
                f.setLastAdminAction(null);
                f.setLastAdminActionAt(null);
                f.setUpdatedAt(now);
            });
            feedbackRepository.saveAll(feedbacks);
            feedbacks.forEach(f -> recordFeedbackHistorySnapshot(
                    f,
                    FeedbackHistoryAction.MEMBER_REVIEW,
                    "Contract resent for member review"
            ));
        }
    }

    /**
     * Kiểm tra và validate contract có thể chỉnh sửa được không
     * Throw exception nếu không thể chỉnh sửa
     */
    private void validateContractEditable(Contract contract, String errorMessageTemplate) {
        long rejectedFeedbackCount = feedbackRepository.countByContractIdAndLastAdminAction(
                contract.getId(), FeedbackAdminAction.REJECT);
        if (rejectedFeedbackCount > 0) {
            throw new IllegalStateException(
                    "Cannot update contract: At least one member feedback was rejected and is waiting for resubmission.");
        }

        if (contract.getApprovalStatus() == ContractApprovalStatus.PENDING) {
            return;
        }

        if (contract.getApprovalStatus() == ContractApprovalStatus.PENDING_MEMBER_APPROVAL) {
            // Cho phép chỉnh sửa nếu còn feedback DISAGREE đang chờ xử lý
            long rejectionCount = feedbackRepository.countByContractIdAndStatusAndReactionType(
                    contract.getId(), MemberFeedbackStatus.PENDING, ReactionType.DISAGREE);
            if (rejectionCount > 0) {
                return;
            }
        }

        throw new IllegalStateException(String.format(errorMessageTemplate, contract.getApprovalStatus()));
    }

    /**
     * Member approve hoặc reject contract
     */
    @Transactional
    public ApiResponseDTO<SubmitMemberFeedbackResponseDTO> submitMemberFeedback(Long contractId, Long userId, ContractMemberFeedbackRequestDTO request) {
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
        
        // Set status dựa trên reactionType
        MemberFeedbackStatus feedbackStatus = (reactionType == ReactionType.AGREE) 
                ? MemberFeedbackStatus.ACCEPTED  // AGREE → ACCEPTED (không cần xử lý)
                : MemberFeedbackStatus.PENDING;  // DISAGREE → PENDING (cần admin xử lý)
        
        boolean feedbackExisted = existingFeedback.isPresent();
        if (feedbackExisted) {
            feedback = existingFeedback.get();
            feedback.setStatus(feedbackStatus);
            feedback.setReactionType(reactionType);
            feedback.setReason(request.reason());
            feedback.setAdminNote(null);
            feedback.setLastAdminAction(null);
            feedback.setLastAdminActionAt(null);
            feedback.setUpdatedAt(LocalDateTime.now());
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            
            feedback = ContractFeedback.builder()
                    .contract(contract)
                    .user(user)
                    .status(feedbackStatus)
                    .reactionType(reactionType)
                    .reason(request.reason())
                    .adminNote(null)
                    .build();
        }

        feedbackRepository.save(feedback);

        // Ghi lại lịch sử khi member submit feedback
        // Nếu có reason thì dùng reason làm actionNote, nếu không thì dùng message mặc định
        String actionNote = (feedback.getReason() != null && !feedback.getReason().trim().isEmpty())
                ? feedback.getReason()
                : (feedbackExisted ? "Member updated feedback" : "Member submitted feedback");
        recordFeedbackHistorySnapshot(
                feedback,
                FeedbackHistoryAction.MEMBER_REVIEW,
                actionNote
        );

        // Kiểm tra xem tất cả members đã approve chưa
        checkAndAutoSignIfAllApproved(contract);

        SubmitMemberFeedbackResponseDTO feedbackData = SubmitMemberFeedbackResponseDTO.builder()
                .feedbackId(feedback.getId())
                .status(feedback.getStatus())
                .isProcessed(isFeedbackProcessed(feedback))
                .reactionType(feedback.getReactionType())
                .reason(feedback.getReason() != null ? feedback.getReason() : "")
                .submittedAt(feedback.getSubmittedAt())
                .build();

        return ApiResponseDTO.<SubmitMemberFeedbackResponseDTO>builder()
                .success(true)
                .message("Feedback submitted successfully")
                .data(feedbackData)
                .build();
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

        // Đếm số members đã agree (status = ACCEPTED)
        long approvedCount = feedbackRepository.countByContractIdAndStatusAndReactionType(
                contract.getId(), 
                MemberFeedbackStatus.ACCEPTED,
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
    public ContractFeedbacksResponseDTO getContractFeedbacks(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        List<ContractFeedback> feedbacks =
                feedbackRepository.findByContractId(contractId);

        List<OwnershipShare> allMembers = shareRepository.findByGroup_GroupId(contract.getGroup().getGroupId());
        List<OwnershipShare> members = allMembers.stream()
                .filter(share -> share.getGroupRole() != GroupRole.ADMIN)
                .toList();

        List<ContractFeedbackResponseDTO> feedbackList = feedbacks.stream()
                .map(f -> ContractFeedbackResponseDTO.builder()
                        .feedbackId(f.getId())
                        .userId(f.getUser().getUserId())
                        .fullName(f.getUser().getFullName())
                        .email(f.getUser().getEmail())
                        .status(f.getStatus())
                        .isProcessed(isFeedbackProcessed(f))
                        .lastAdminAction(f.getLastAdminAction())
                        .lastAdminActionAt(f.getLastAdminActionAt())
                        .reactionType(f.getReactionType())
                        .reason(f.getReason() != null ? f.getReason() : "")
                        .adminNote(f.getAdminNote() != null ? f.getAdminNote() : "")
                        .submittedAt(f.getSubmittedAt())
                        .build())
                .toList();

        List<PendingMemberDTO> pendingMembers = members.stream()
                .filter(m -> !feedbackRepository.existsByContractIdAndUser_UserId(contractId, m.getUser().getUserId()))
                .map(m -> PendingMemberDTO.builder()
                        .userId(m.getUser().getUserId())
                        .fullName(m.getUser().getFullName())
                        .email(m.getUser().getEmail())
                        .build())
                .toList();

        return ContractFeedbacksResponseDTO.builder()
                .contractId(contractId)
                .contractStatus(contract.getApprovalStatus())
                .totalMembers(members.size())
                .totalFeedbacks(feedbacks.size())
                .acceptedCount(feedbackRepository.countByContractIdAndStatusAndReactionType(
                        contractId, MemberFeedbackStatus.ACCEPTED, ReactionType.AGREE))
                .pendingDisagreeCount(feedbackRepository.countByContractIdAndStatusAndReactionType(
                        contractId, MemberFeedbackStatus.PENDING, ReactionType.DISAGREE))
                .approvedFeedbacksCount(feedbackRepository.countByContractIdAndLastAdminAction(
                        contractId, FeedbackAdminAction.APPROVE))
                .rejectedFeedbacksCount(feedbackRepository.countByContractIdAndLastAdminAction(
                        contractId, FeedbackAdminAction.REJECT))
                .feedbacks(feedbackList)
                .pendingMembers(pendingMembers)
                .build();
    }

    /**
     * Lấy tất cả feedback của members cho contract theo groupId
     * Mỗi group hiện chỉ có 1 contract, nên hàm này ánh xạ groupId -> contractId rồi tái sử dụng logic cũ
     */
    public ContractFeedbacksResponseDTO getContractFeedbacksByGroup(Long groupId) {
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
