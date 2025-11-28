package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.*;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.ContractFeedbackRepository;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ContractFeedbackService {

    private final ContractFeedbackRepository feedbackRepository;
    private final ContractRepository contractRepository;
    private final OwnershipShareRepository shareRepository;
    private final UserRepository userRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ContractHelperService contractHelperService;

    public ContractFeedbackService(
            ContractFeedbackRepository feedbackRepository,
            ContractRepository contractRepository,
            OwnershipShareRepository shareRepository,
            UserRepository userRepository,
            NotificationOrchestrator notificationOrchestrator,
            ContractHelperService contractHelperService) {
        this.feedbackRepository = feedbackRepository;
        this.contractRepository = contractRepository;
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.notificationOrchestrator = notificationOrchestrator;
        this.contractHelperService = contractHelperService;
    }

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


    @Transactional
    public ApiResponseDTO<FeedbackActionResponseDTO> approveFeedbackInternal(
            Long feedbackId,
            FeedbackActionRequestDTO request) {

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

        // Approve feedback: Chuyển status thành APPROVED
        feedback.setStatus(MemberFeedbackStatus.APPROVED);
        feedback.setLastAdminAction(FeedbackAdminAction.APPROVE);
        feedback.setLastAdminActionAt(LocalDateTime.now());
        feedback.setAdminNote(adminNote);
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        // Kiểm tra xem tất cả members đã approve chưa (có thể chuyển contract sang SIGNED)
        checkAndAutoSignIfAllApproved(contract);

        FeedbackActionResponseDTO feedbackData = buildFeedbackActionResponseDTO(feedback);

        String message = "Feedback approved by group admin. Feedback status changed to APPROVED.";

        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message(message)
                .data(feedbackData)
                .build();
    }

    @Transactional
    public ApiResponseDTO<FeedbackActionResponseDTO> rejectFeedbackInternal(
            Long feedbackId,
            FeedbackActionRequestDTO request) {

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

        Contract contract = feedback.getContract();

        // Reject feedback: Chuyển status thành REJECTED
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

        String responseMessage = "Feedback rejected by group admin. Admin note has been sent to the member.";

        return ApiResponseDTO.<FeedbackActionResponseDTO>builder()
                .success(true)
                .message(responseMessage)
                .data(feedbackData)
                .build();
    }

    public ContractFeedbacksResponseDTO getContractFeedbacks(Long contractId, MemberFeedbackStatus filterStatus) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));

        // CHỈ LẤY FEEDBACK DISAGREE (reactionType = DISAGREE)
        List<ContractFeedback> allFeedbacks = feedbackRepository.findByContractId(contractId);
        List<ContractFeedback> feedbacks = allFeedbacks.stream()
                .filter(f -> f.getReactionType() == ReactionType.DISAGREE)
                .filter(f -> filterStatus == null || f.getStatus() == filterStatus)
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
     * Kiểm tra và tự động chuyển sang SIGNED nếu tất cả thành viên (bao gồm cả admin) đã approve
     */
    @Transactional
    protected void checkAndAutoSignIfAllApproved(Contract contract) {
        Long groupId = contract.getGroup().getGroupId();
        OwnershipGroup group = contract.getGroup();

        // Lấy memberCapacity từ group (đã tính cả admin)
        Integer memberCapacity = group.getMemberCapacity();
        if (memberCapacity == null || memberCapacity <= 0) {
            return;
        }

        // Lấy danh sách tất cả thành viên để lọc feedbacks
        List<OwnershipShare> allMembers = shareRepository.findByGroup_GroupId(groupId);
        if (allMembers.isEmpty()) {
            return;
        }

        // Kiểm tra admin đã approve trước (contract status = PENDING_MEMBER_APPROVAL)
        // Nếu contract chưa ở PENDING_MEMBER_APPROVAL nghĩa là admin chưa ký
        if (contract.getApprovalStatus() != ContractApprovalStatus.PENDING_MEMBER_APPROVAL) {
            return; // Admin chưa approve, không thể chuyển sang SIGNED
        }

        // Đếm số thành viên đã approve (status = APPROVED) - bao gồm cả admin và members
        // APPROVED có thể là: Member AGREE hoặc Admin đã approve feedback DISAGREE
        // Lưu ý: Logic submit đã hạn chế mỗi user chỉ có 1 feedback APPROVED (trừ khi resubmit sau REJECTED)
        // Khi resubmit sau REJECTED, feedback cũ vẫn là REJECTED, feedback mới là APPROVED
        // Nên đếm số feedbacks APPROVED vẫn đúng với số users đã approve
        // QUAN TRỌNG: Đếm feedbacks của tất cả thành viên (bao gồm cả admin)
        Set<Long> allUserIds = allMembers.stream()
                .map(share -> share.getUser().getUserId())
                .collect(Collectors.toSet());

        List<ContractFeedback> allFeedbacks = feedbackRepository.findByContractId(contract.getId());
        long approvedCount = allFeedbacks.stream()
                .filter(f -> f.getStatus() == MemberFeedbackStatus.APPROVED)
                .filter(f -> allUserIds.contains(f.getUser().getUserId()))
                .count();

        // Nếu tất cả thành viên (bao gồm cả admin) đã approve, chuyển sang SIGNED
        // Sử dụng memberCapacity thay vì allMembers.size() vì memberCapacity đã tính cả admin
        if (approvedCount == memberCapacity) {
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
}

