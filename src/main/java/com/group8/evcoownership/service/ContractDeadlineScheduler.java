package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractDeadlineScheduler {

    private final ContractRepository contractRepository;
    private final OwnershipShareRepository ownershipShareRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ContractDeadlinePolicy deadlinePolicy;
    private final DepositPaymentService depositPaymentService;

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
                            notificationOrchestrator.sendComprehensiveNotification(
                                    share.getUser().getUserId(),
                                    NotificationType.DEPOSIT_REQUIRED,
                                    "Deposit Reminder",
                                    String.format("Please complete your deposit soon! Time remaining: %d minutes. The contract will be rejected if the deadline is missed.", minutesLeft),
                                    Map.of("groupId", groupId, "minutesLeft", minutesLeft)
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

                // REFUND tiền cọc cho các member đã đóng
                depositPaymentService.refundDepositsForGroup(shares, groupId);

                // 1. Gửi thông báo CÁ NHÂN cho các member chưa đóng cọc
                for (OwnershipShare share : shares) {
                    if (share.getDepositStatus() != DepositStatus.PAID) {
                        try {
                            assert notificationOrchestrator != null;
                            notificationOrchestrator.sendComprehensiveNotification(
                                    share.getUser().getUserId(),
                                    NotificationType.DEPOSIT_OVERDUE,
                                    "Deposit Overdue",
                                    "You have missed the deposit deadline. The contract has been rejected and deposits of other members have been refunded.",
                                    Map.of("groupId", groupId, "contractId", contract.getId())
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
}


