package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.ContractFeedback;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.FeedbackHistoryAction;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.repository.ContractFeedbackRepository;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractDeadlineScheduler {

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContractRepository contractRepository;
    private final OwnershipShareRepository ownershipShareRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ContractDeadlinePolicy deadlinePolicy;
    private final DepositPaymentService depositPaymentService;
    private final PaymentRepository paymentRepository;
    private final ContractFeedbackRepository feedbackRepository;
    private final ContractService contractService;

    /**
     * Gửi thông báo nhắc nhở cho các member chưa đóng cọc khi gần hết hạn
     */
    @Scheduled(cron = "0 * * * * *")
    public void sendDepositReminderNotifications() {
        List<Contract> signed = contractRepository.findByApprovalStatus(ContractApprovalStatus.SIGNED);
        if (signed.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        for (Contract contract : signed) {
            try {
                Long groupId = contract.getGroup().getGroupId();
                LocalDateTime deadline = deadlinePolicy.computeDepositDeadline(contract);

                if (deadline == null || deadline.isBefore(now) || deadline.isAfter(now.plusMinutes(2))) {
                    continue; // đã qua hạn hoặc chưa đến 2 phút trước hạn
                }

                // Gửi thông báo cho các member chưa đóng cọc
                List<OwnershipShare> shares = ownershipShareRepository.findByGroupGroupId(groupId);
                for (OwnershipShare share : shares) {
                    if (share.getDepositStatus() != DepositStatus.PAID) {
                        try {
                            long minutesLeft = Duration.between(now, deadline).toMinutes();
                            Map<String, Object> data = buildDepositNotificationData(
                                    contract,
                                    groupId,
                                    share,
                                    "PENDING",
                                    deadline,
                                    minutesLeft
                            );

                            notificationOrchestrator.sendComprehensiveNotification(
                                    share.getUser().getUserId(),
                                    NotificationType.DEPOSIT_REQUIRED,
                                    "Deposit Reminder",
                                    String.format("Please complete your deposit soon! Time remaining: %d minutes. The contract will be rejected if the deadline is missed.", minutesLeft),
                                    data
                            );
                        } catch (Exception ex) {
                            log.error("Failed to send reminder to user {}", share.getUser().getUserId(), ex);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to send deposit reminder for contract id={}", contract.getId(), ex);
            }
        }
    }

    // Chạy mỗi phút để demo: kiểm tra hợp đồng đã ký nhưng quá hạn đóng cọc
    @Scheduled(cron = "0 * * * * *")
    public void autoRejectExpiredSignedContracts() {
        List<Contract> signed = contractRepository.findByApprovalStatus(ContractApprovalStatus.SIGNED);
        if (signed.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        for (Contract contract : signed) {
            try {
                Long groupId = contract.getGroup().getGroupId();

                // Tính deadline động theo policy
                LocalDateTime deadline = deadlinePolicy.computeDepositDeadline(contract);

                if (deadline == null || !deadline.isBefore(now)) {
                    continue; // chưa quá hạn
                }

                // Nếu tất cả thành viên đã đóng cọc thì bỏ qua (có thể vừa đóng xong trước khi scheduler chạy)
                List<OwnershipShare> shares = ownershipShareRepository.findByGroupGroupId(groupId);
                boolean allPaid = shares.stream().allMatch(s -> s.getDepositStatus() == DepositStatus.PAID);
                if (allPaid) {
                    continue;
                }

                // Reject hợp đồng do quá hạn
                contract.setApprovalStatus(ContractApprovalStatus.PENDING);
                contract.setIsActive(false);
                contract.setRejectionReason("Deposit deadline missed (expired at " + deadline + ")");
                contract.setUpdatedAt(LocalDateTime.now());
                contractRepository.save(contract);

                // Xóa tất cả feedbacks cũ để có thể tạo lại feedback mới
                // Ghi lại lịch sử trước khi xóa
                List<ContractFeedback> feedbacks = feedbackRepository.findByContractId(contract.getId());
                if (!feedbacks.isEmpty()) {
                    for (ContractFeedback feedback : feedbacks) {
                        try {
                            contractService.recordFeedbackHistorySnapshot(
                                    feedback,
                                    FeedbackHistoryAction.MEMBER_REVIEW,
                                    "Contract auto-rejected due to deposit deadline expiration - feedbacks cleared"
                            );
                        } catch (Exception ex) {
                            log.error("Failed to record feedback history for feedback {}", feedback.getId(), ex);
                        }
                    }
                    
                    // Xóa tất cả feedbacks
                    feedbackRepository.deleteAll(feedbacks);
                    feedbackRepository.flush();
                    log.info("Deleted {} feedbacks for contract {} due to deposit deadline expiration", 
                            feedbacks.size(), contract.getId());
                }

                // REFUND tiền cọc cho các member đã đóng
                depositPaymentService.refundDepositsForGroup(shares, groupId);

                // 1. Gửi thông báo CÁ NHÂN cho các member chưa đóng cọc
                for (OwnershipShare share : shares) {
                    if (share.getDepositStatus() != DepositStatus.PAID) {
                        try {
                            Map<String, Object> data = buildDepositNotificationData(
                                    contract,
                                    groupId,
                                    share,
                                    "OVERDUE",
                                    deadline,
                                    null
                            );

                            assert notificationOrchestrator != null;
                            notificationOrchestrator.sendComprehensiveNotification(
                                    share.getUser().getUserId(),
                                    NotificationType.DEPOSIT_OVERDUE,
                                    "Deposit Overdue",
                                    "You have missed the deposit deadline. The contract has been rejected and deposits of other members have been refunded.",
                                    data
                            );
                        } catch (Exception ex) {
                            log.error("Failed to send individual notification to user {}", share.getUser().getUserId(), ex);
                        }
                    }
                }

                // 2. Gửi thông báo TỚI CẢ NHÓM (tất cả members)
                if (notificationOrchestrator != null) {
                    // Build rich data for email content
                    Map<String, Object> emailData = notificationOrchestrator.buildContractEmailData(contract);
                    // Send in-app + websocket + email to all group members
                    notificationOrchestrator.sendGroupNotification(
                            groupId,
                            NotificationType.CONTRACT_REJECTED,
                            "Contract Rejected due to Deposit Deadline",
                            "The contract has been rejected because the deposit was not completed before the deadline. Deposits have been refunded to members who paid.",
                            emailData
                    );
                }
            } catch (Exception ex) {
                log.error("Failed to auto-reject expired contract id={}", contract.getId(), ex);
            }
        }
    }

    private Map<String, Object> buildDepositNotificationData(Contract contract,
                                                             Long groupId,
                                                             OwnershipShare share,
                                                             String defaultStatus,
                                                             LocalDateTime referenceTime,
                                                             Long minutesLeft) {
        Map<String, Object> data = new HashMap<>();
        data.put("groupId", groupId);
        data.put("contractId", contract.getId());
        if (minutesLeft != null) {
            data.put("minutesLeft", minutesLeft);
        }

        Long userId = share.getUser().getUserId();

        Payment payment = paymentRepository
                .findTopByPayer_UserIdAndFund_Group_GroupIdAndPaymentTypeAndStatusOrderByPaymentDateDesc(
                        userId, groupId, PaymentType.DEPOSIT, PaymentStatus.PENDING)
                .or(() -> paymentRepository
                        .findTopByPayer_UserIdAndFund_Group_GroupIdAndPaymentTypeOrderByPaymentDateDesc(
                                userId, groupId, PaymentType.DEPOSIT))
                .orElse(null);

        if (payment != null) {
            data.put("transactionCode", defaultString(payment.getTransactionCode()));
            data.put("amount", payment.getAmount() != null
                    ? payment.getAmount()
                    : calculateMemberRequiredDeposit(contract, share));
            data.put("paymentMethod", defaultString(payment.getPaymentMethod(), "VNPay"));
            data.put("paymentType", payment.getPaymentType() != null ? payment.getPaymentType().name() : "DEPOSIT");
            data.put("paymentDate", payment.getPaymentDate() != null
                    ? DEADLINE_FORMATTER.format(payment.getPaymentDate())
                    : referenceTime != null ? DEADLINE_FORMATTER.format(referenceTime) : "N/A");
            data.put("status", payment.getStatus() != null ? payment.getStatus().name() : defaultStatus);
        } else {
            data.put("transactionCode", "N/A");
            data.put("amount", calculateMemberRequiredDeposit(contract, share));
            data.put("paymentMethod", "VNPay");
            data.put("paymentType", "DEPOSIT");
            data.put("paymentDate", referenceTime != null ? DEADLINE_FORMATTER.format(referenceTime) : "N/A");
            data.put("status", defaultStatus);
        }

        return data;
    }

    private BigDecimal calculateMemberRequiredDeposit(Contract contract, OwnershipShare share) {
        if (contract.getRequiredDepositAmount() == null || share.getOwnershipPercentage() == null) {
            return BigDecimal.ZERO;
        }

        return contract.getRequiredDepositAmount()
                .multiply(share.getOwnershipPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String defaultString(String value) {
        return defaultString(value, "N/A");
    }

    private String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}


