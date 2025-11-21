package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.*;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContractFeedbackService {

    private final ContractFeedbackRepository feedbackRepository;
    private final ContractRepository contractRepository;
    private final OwnershipShareRepository shareRepository;
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ContractService contractService; // For checkAndAutoSignIfAllApproved
    private final ContractHelperService contractHelperService;

    /**
     * Member approve hoặc reject contract
     */
    @Transactional
    public ApiResponseDTO<SubmitMemberFeedbackResponseDTO> submitMemberFeedback(
            Long contractId, Long userId, ContractMemberFeedbackRequestDTO request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // Cho phép gửi feedback khi contract ở PENDING hoặc PENDING_MEMBER_APPROVAL
        if (contract.getApprovalStatus() != ContractApprovalStatus.PENDING
                && contract.getApprovalStatus() != ContractApprovalStatus.PENDING_MEMBER_APPROVAL) {
            throw new IllegalStateException(
                    "Contract is not in PENDING or PENDING_MEMBER_APPROVAL status. Current status: " + contract.getApprovalStatus()
            );
        }

        // Kiểm tra user là member của group
        Long groupId = contract.getGroup().getGroupId();
        OwnershipShare share = shareRepository.findById(
                new OwnershipShareId(userId, groupId)
        ).orElseThrow(() -> new IllegalStateException("User is not a member of this group"));

        boolean isAdmin = share.getGroupRole() == GroupRole.ADMIN;
        boolean isPending = contract.getApprovalStatus() == ContractApprovalStatus.PENDING;
        boolean isPendingMemberApproval = contract.getApprovalStatus() == ContractApprovalStatus.PENDING_MEMBER_APPROVAL;

        // Nếu contract ở PENDING, chỉ cho phép admin group gửi feedback
        if (isPending && !isAdmin) {
            throw new IllegalStateException(
                    "Contract is in PENDING status. Only admin group can submit feedback. Please wait for admin to sign the contract."
            );
        }

        // Nếu contract ở PENDING_MEMBER_APPROVAL, KHÔNG cho phép admin group gửi feedback
        if (isPendingMemberApproval && isAdmin) {
            throw new IllegalStateException(
                    "Group admin cannot submit feedback when contract is in PENDING_MEMBER_APPROVAL status. Admin has already signed the contract."
            );
        }

        // Validate request
        if (!request.isValid()) {
            throw new IllegalArgumentException("Invalid feedback. If DISAGREE, reason must be at least 10 characters.");
        }

        ReactionType reactionType =
                "AGREE".equalsIgnoreCase(request.reactionType())
                        ? ReactionType.AGREE
                        : ReactionType.DISAGREE;

        // Kiểm tra đã submit feedback cho contract chưa
        var existingFeedback = feedbackRepository
                .findTopByContractIdAndUser_UserIdOrderBySubmittedAtDesc(contractId, userId);

        // Set status dựa trên reactionType
        MemberFeedbackStatus feedbackStatus = (reactionType == ReactionType.AGREE)
                ? MemberFeedbackStatus.APPROVED  // AGREE → APPROVED (đã approve luôn)
                : MemberFeedbackStatus.PENDING;  // DISAGREE → PENDING (cần admin xử lý)

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Kiểm tra nếu đã có feedback và không bị REJECTED thì không cho phép tạo mới
        if (existingFeedback.isPresent()) {
            ContractFeedback latestFeedback = existingFeedback.get();
            boolean isRejected = latestFeedback.getStatus() == MemberFeedbackStatus.REJECTED;

            // Chỉ cho phép tạo feedback mới khi feedback bị REJECTED
            if (!isRejected) {
                throw new IllegalStateException(
                        "You have already submitted feedback for this contract. Only REJECTED feedbacks can be resubmitted."
                );
            }
        }

        // Tạo feedback mới (nếu chưa có feedback hoặc feedback cũ bị REJECTED)
        ContractFeedback feedback = ContractFeedback.builder()
                .contract(contract)
                .user(user)
                .status(feedbackStatus)
                .reactionType(reactionType)
                .reason(request.reason())
                .adminNote(null)
                .build();

        feedbackRepository.save(feedback);

        // Kiểm tra xem tất cả members đã approve chưa
        contractService.checkAndAutoSignIfAllApproved(contract);

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
     * Admin group approve một feedback cụ thể (theo feedbackId)
     * CHỈ ADMIN GROUP của contract mới có quyền approve feedback
     * Chỉ có thể approve feedbacks có status = PENDING
     */
    @Transactional
    public ApiResponseDTO<FeedbackActionResponseDTO> approveFeedbackByGroupAdmin(
            Long feedbackId,
            FeedbackActionRequestDTO request,
            Long userId) {

        String adminNote = request != null ? request.adminNote() : null;
        adminNote = (adminNote != null && !adminNote.trim().isEmpty()) ? adminNote.trim() : null;

        ContractFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));

        if (feedback.getStatus() != MemberFeedbackStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING feedbacks can be approved. Current status: " + feedback.getStatus()
            );
        }

        Contract contract = feedback.getContract();

        // Kiểm tra user có phải là admin group không
        validateGroupAdmin(userId, contract.getId());

        // Admin group approve feedback DISAGREE: Chuyển status thành APPROVED
        // Sau khi admin group approve, system admin có thể sửa contract terms
        feedback.setStatus(MemberFeedbackStatus.APPROVED);
        feedback.setLastAdminAction(FeedbackAdminAction.APPROVE);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setAdminNote(adminNote);
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Kiểm tra xem tất cả members đã approve chưa (có thể chuyển contract sang SIGNED)
        contractService.checkAndAutoSignIfAllApproved(contract);

        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);

        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message("Feedback approved by group admin. Feedback status changed to APPROVED.")
                .data(feedbackData)
                .build();
    }

    /**
     * Admin group reject một feedback cụ thể (theo feedbackId)
     * CHỈ ADMIN GROUP của contract mới có quyền reject feedback
     * Chỉ có thể reject feedbacks có status = PENDING và isProcessed = false
     */
    @Transactional
    public ApiResponseDTO<FeedbackActionResponseDTO> rejectFeedbackByGroupAdmin(
            Long feedbackId,
            FeedbackActionRequestDTO request,
            Long userId) {

        String adminNote = request != null ? request.adminNote() : null;
        adminNote = (adminNote != null && !adminNote.trim().isEmpty()) ? adminNote.trim() : null;

        ContractFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));

        if (feedback.getStatus() != MemberFeedbackStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING feedbacks can be rejected. Current status: " + feedback.getStatus()
            );
        }

        // Không cho phép reject feedback đã được processed
        if (isFeedbackProcessed(feedback)) {
            throw new IllegalStateException(
                    "Cannot reject feedback that has already been processed. Feedback is already processed."
            );
        }

        Contract contract = feedback.getContract();

        // Kiểm tra user có phải là admin group không
        validateGroupAdmin(userId, contract.getId());

        // Admin group reject feedback: Chuyển status thành REJECTED
        feedback.setStatus(MemberFeedbackStatus.REJECTED);
        feedback.setAdminNote(adminNote);
        feedback.setLastAdminAction(FeedbackAdminAction.REJECT);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Gửi notification và email cho member
        if (notificationOrchestrator != null) {
            Long memberUserId = feedback.getUser().getUserId();

            String title = "Feedback Rejected";
            String message = "Your feedback has been rejected by group admin.";
            if (adminNote != null) {
                message += "\n\nAdmin note: " + adminNote;
            }

            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("feedbackId", feedback.getId());
            notificationData.put("contractId", contract.getId());
            notificationData.put("contractNumber", contractHelperService.generateContractNumber(contract.getId()));
            notificationData.put("reactionType", feedback.getReactionType());
            notificationData.put("adminNote", adminNote != null ? adminNote : "");

            notificationOrchestrator.sendComprehensiveNotification(
                    memberUserId,
                    NotificationType.CONTRACT_REJECTED,
                    title,
                    message,
                    notificationData
            );
        }

        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);

        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message("Feedback rejected by group admin. Admin note has been sent to the member.")
                .data(feedbackData)
                .build();
    }

    /**
     * Admin approve một feedback cụ thể (theo feedbackId)
     * Chỉ có thể approve feedbacks có status = PENDING
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

        Contract contract = feedback.getContract();

        // Admin approve feedback DISAGREE: Chuyển status thành APPROVED
        // (Admin đã xem lý do và đồng ý sửa contract nếu cần)
        feedback.setStatus(MemberFeedbackStatus.APPROVED);
        feedback.setLastAdminAction(FeedbackAdminAction.APPROVE);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setAdminNote(adminNote);
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Kiểm tra xem tất cả members đã approve chưa (có thể chuyển contract sang SIGNED)
        contractService.checkAndAutoSignIfAllApproved(contract);

        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);

        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message("Feedback approved. Feedback status changed to APPROVED.")
                .data(feedbackData)
                .build();
    }

    /**
     * Admin reject một feedback cụ thể (theo feedbackId)
     * Chỉ có thể reject feedbacks có status = PENDING và isProcessed = false
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

        // Không cho phép reject feedback đã được processed (isProcessed = true)
        if (isFeedbackProcessed(feedback)) {
            throw new IllegalStateException(
                    "Cannot reject feedback that has already been processed. Feedback is already processed."
            );
        }

        // Admin reject feedback DISAGREE: Chuyển status thành REJECTED
        // Ghi adminNote và gửi notification cho member - workflow kết thúc
        feedback.setStatus(MemberFeedbackStatus.REJECTED);
        feedback.setAdminNote(adminNote);
        feedback.setLastAdminAction(FeedbackAdminAction.REJECT);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Gửi notification và email cho member
        if (notificationOrchestrator != null) {
            Long userId = feedback.getUser().getUserId();
            Contract contract = feedback.getContract();

            String title = "Feedback Rejected";
            String message = "Your feedback has been rejected by admin.";
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

        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);

        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message("Feedback rejected. Admin note has been sent to the member.")
                .data(feedbackData)
                .build();
    }

    /**
     * Lấy tất cả feedback của members cho contract
     * CHỈ TRẢ VỀ FEEDBACK DISAGREE (để admin group xem và xử lý)
     */
    public ContractFeedbacksResponseDTO getContractFeedbacks(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // CHỈ LẤY FEEDBACK DISAGREE (reactionType = DISAGREE)
        List<ContractFeedback> allFeedbacks = feedbackRepository.findByContractId(contractId);
        List<ContractFeedback> feedbacks = allFeedbacks.stream()
                .filter(f -> f.getReactionType() == ReactionType.DISAGREE)
                .toList();

        List<OwnershipShare> allMembers = shareRepository.findByGroup_GroupId(contract.getGroup().getGroupId());
        List<OwnershipShare> members = allMembers.stream()
                .filter(share -> share.getGroupRole() != GroupRole.ADMIN)
                .toList();

        // Tạo Map để lookup groupRole nhanh theo userId
        Map<Long, GroupRole> userRoleMap = allMembers.stream()
                .collect(Collectors.toMap(
                        share -> share.getUser().getUserId(),
                        OwnershipShare::getGroupRole
                ));

        List<ContractFeedbackResponseDTO> feedbackList = feedbacks.stream()
                .map(f -> ContractFeedbackResponseDTO.builder()
                        .feedbackId(f.getId())
                        .userId(f.getUser().getUserId())
                        .fullName(f.getUser().getFullName())
                        .email(f.getUser().getEmail())
                        .groupRole(userRoleMap.getOrDefault(f.getUser().getUserId(), GroupRole.MEMBER))
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

        // Đếm các status khác nhau - CHỈ ĐẾM FEEDBACK DISAGREE
        long approvedCount = feedbacks.stream()
                .filter(f -> f.getStatus() == MemberFeedbackStatus.APPROVED)
                .count();
        long pendingDisagreeCount = feedbacks.stream()
                .filter(f -> f.getStatus() == MemberFeedbackStatus.PENDING)
                .count();
        long rejectedCount = feedbacks.stream()
                .filter(f -> f.getStatus() == MemberFeedbackStatus.REJECTED)
                .count();

        return ContractFeedbacksResponseDTO.builder()
                .contractId(contractId)
                .contractStatus(contract.getApprovalStatus())
                .totalMembers(members.size())
                .totalFeedbacks(feedbacks.size())
                .acceptedCount(approvedCount)
                .pendingDisagreeCount(pendingDisagreeCount)
                .rejectedCount(rejectedCount)
                .approvedFeedbacksCount(feedbacks.stream()
                        .filter(f -> f.getLastAdminAction() == FeedbackAdminAction.APPROVE)
                        .count())
                .rejectedFeedbacksCount(feedbacks.stream()
                        .filter(f -> f.getLastAdminAction() == FeedbackAdminAction.REJECT)
                        .count())
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

    /**
     * Kiểm tra user có phải là admin group của contract không
     */
    private void validateGroupAdmin(Long userId, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        Long groupId = contract.getGroup().getGroupId();
        OwnershipShare share = shareRepository.findById(
                new OwnershipShareId(userId, groupId)
        ).orElseThrow(() -> new IllegalStateException("User is not a member of this group"));

        if (share.getGroupRole() != GroupRole.ADMIN) {
            throw new IllegalStateException("Only group admin can perform this action. You are not the admin of this group.");
        }
    }
}

