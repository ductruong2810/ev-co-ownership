package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
                            long minutesLeft = java.time.Duration.between(now, deadline).toMinutes();
                            notificationOrchestrator.sendComprehensiveNotification(
                                    share.getUser().getUserId(),
                                    com.group8.evcoownership.enums.NotificationType.DEPOSIT_REQUIRED,
                                    "Nhắc nhở đóng tiền cọc",
                                    String.format("Hãy đóng tiền cọc sớm! Thời gian còn lại: %d phút. Hợp đồng sẽ bị từ chối nếu không đóng đúng hạn.", minutesLeft),
                                    java.util.Map.of("groupId", groupId, "minutesLeft", minutesLeft)
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
                contract.setRejectionReason("Hết hạn đóng cọc (quá hạn vào " + deadline + ")");
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
                                    com.group8.evcoownership.enums.NotificationType.DEPOSIT_OVERDUE,
                                    "Hết hạn đóng tiền cọc",
                                    "Bạn đã quá hạn đóng tiền cọc. Hợp đồng đã bị từ chối và tiền cọc của các thành viên khác đã được hoàn lại.",
                                    java.util.Map.of("groupId", groupId, "contractId", contract.getId())
                            );
                        } catch (Exception ex) {
                            log.error("Failed to send individual notification to user {}", share.getUser().getUserId(), ex);
                        }
                    }
                }

                // 2. Gửi thông báo TỚI CẢ NHÓM (tất cả members)
                if (notificationOrchestrator != null) {
                    // Build rich data for email content
                    java.util.Map<String, Object> emailData = new java.util.HashMap<>();
                    emailData.put("groupId", groupId);
                    emailData.put("contractId", contract.getId());
                    emailData.put("groupName", contract.getGroup().getGroupName());
                    emailData.put("startDate", contract.getStartDate());
                    emailData.put("endDate", contract.getEndDate());
                    emailData.put("depositAmount", contract.getRequiredDepositAmount());
                    emailData.put("status", contract.getApprovalStatus());
                    emailData.put("rejectionReason", contract.getRejectionReason());

                    // Send in-app + websocket + email to all group members
                    notificationOrchestrator.sendGroupNotification(
                            groupId,
                            com.group8.evcoownership.enums.NotificationType.CONTRACT_REJECTED,
                            "Hợp đồng bị từ chối do hết hạn đóng cọc",
                            "Hợp đồng đã bị từ chối vì chưa hoàn tất tiền cọc trước hạn. Tiền cọc đã được hoàn lại cho các thành viên đã đóng.",
                            emailData
                    );
                }
            } catch (Exception ex) {
                log.error("Failed to auto-reject expired contract id={}", contract.getId(), ex);
            }
        }
    }

}


