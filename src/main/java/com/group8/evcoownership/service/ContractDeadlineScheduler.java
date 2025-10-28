package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import com.group8.evcoownership.entity.OwnershipShare;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.PaymentRepository;
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
    private final PaymentRepository paymentRepository;
    private final FundService fundService;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ContractDeadlinePolicy deadlinePolicy;

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
                contract.setApprovalStatus(ContractApprovalStatus.REJECTED);
                contract.setIsActive(false);
                contract.setRejectionReason("Hết hạn đóng cọc (quá hạn vào " + deadline + ")");
                contract.setUpdatedAt(LocalDateTime.now());
                contractRepository.save(contract);

                // REFUND tiền cọc cho các member đã đóng
                refundDepositsForShares(shares, groupId);

                // Gửi thông báo tới group
                if (notificationOrchestrator != null) {
                    notificationOrchestrator.sendGroupNotification(
                            groupId,
                            com.group8.evcoownership.enums.NotificationType.CONTRACT_REJECTED,
                            "Hợp đồng bị từ chối do hết hạn đóng cọc",
                            "Hợp đồng đã bị từ chối vì chưa hoàn tất tiền cọc trước hạn. Tiền cọc đã được hoàn lại cho các thành viên đã đóng."
                    );
                }
            } catch (Exception ex) {
                log.error("Failed to auto-reject expired contract id={}", contract.getId(), ex);
            }
        }
    }

    /**
     * Hoàn tiền cọc cho các member đã đóng khi contract bị reject
     */
    private void refundDepositsForShares(List<OwnershipShare> shares, Long groupId) {
        for (OwnershipShare share : shares) {
            if (share.getDepositStatus() == DepositStatus.PAID) {
                try {
                    // Tìm payment deposits cho user này
                    List<Payment> deposits = paymentRepository.findAllByPayer_UserIdAndStatusAndPaymentType(
                            share.getUser().getUserId(),
                            PaymentStatus.COMPLETED,
                            com.group8.evcoownership.enums.PaymentType.DEPOSIT,
                            null
                    ).stream()
                            .filter(p -> p.getFund().getGroup().getGroupId().equals(groupId))
                            .toList();

                    // Đánh dấu các payment này là REFUNDED và trừ quỹ
                    for (Payment payment : deposits) {
                        payment.setStatus(PaymentStatus.REFUNDED);
                        paymentRepository.save(payment);
                        
                        // Trừ quỹ khi refund (quan trọng!)
                        try {
                            fundService.decreaseBalance(payment.getFund().getFundId(), payment.getAmount());
                            log.info("Refunded deposit for user {} in group {} - Fund decreased by {}", 
                                    share.getUser().getUserId(), groupId, payment.getAmount());
                        } catch (Exception fundEx) {
                            log.error("Failed to decrease fund for refunded payment {}", payment.getId(), fundEx);
                            // Rollback payment status nếu không trừ được quỹ
                            payment.setStatus(PaymentStatus.COMPLETED);
                            paymentRepository.save(payment);
                            throw fundEx;
                        }
                    }

                    // Reset deposit status về PENDING
                    share.setDepositStatus(DepositStatus.PENDING);
                    ownershipShareRepository.save(share);

                } catch (Exception ex) {
                    log.error("Failed to refund deposit for user {} in group {}", 
                            share.getUser().getUserId(), groupId, ex);
                }
            }
        }
    }
}


