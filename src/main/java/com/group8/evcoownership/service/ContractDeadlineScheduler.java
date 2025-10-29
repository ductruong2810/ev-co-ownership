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
    private final VnPay_PaymentService vnPayPaymentService; 

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
                refundDepositsForShares(shares, groupId);

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

                    // Hoàn tiền cọc qua VNPay API
                    for (Payment payment : deposits) {
                        try {
                            // Parse vnp_TransactionNo và vnp_TransactionDate từ providerResponse
                            String vnpTransactionNo = VnPay_PaymentService.extractTransactionNo(payment.getProviderResponse());
                            if (vnpTransactionNo == null) {
                                log.warn("Cannot extract vnp_TransactionNo for payment {}", payment.getId());
                                continue;  // Skip payment này nếu không có VNPay transaction number
                            }

                            // Format vnp_TransactionDate từ payment.paymentDate
                            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
                            formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                            String vnpTransactionDate = formatter.format(
                                java.util.Date.from(payment.getPaymentDate().atZone(java.time.ZoneId.systemDefault()).toInstant())
                            );

                            // Gọi VNPay API để hoàn tiền
                            String refundUrl = vnPayPaymentService.createRefundRequest(
                                payment.getAmount().longValue(),
                                payment.getTransactionCode(),
                                vnpTransactionNo,
                                vnpTransactionDate
                            );
                            
                            log.info("VNPay Refund URL created: {}", refundUrl);
                            
                            // Gọi HTTP GET đến VNPay để thực hiện refund
                            java.net.URL url = new java.net.URL(refundUrl);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(10000);
                            
                            int responseCode = conn.getResponseCode();
                            log.info("📡 VNPay Refund Response Code: {}", responseCode);
                            
                            // Đọc response
                            try (java.io.BufferedReader in = new java.io.BufferedReader(
                                new java.io.InputStreamReader(conn.getInputStream()))) {
                                String inputLine;
                                StringBuilder response = new StringBuilder();
                                while ((inputLine = in.readLine()) != null) {
                                    response.append(inputLine);
                                }
                                log.info("📄 VNPay Refund Response: {}", response);
                            }

                            // Đánh dấu payment REFUNDED và trừ quỹ
                            payment.setStatus(PaymentStatus.REFUNDED);
                            paymentRepository.save(payment);
                            
                            fundService.decreaseBalance(payment.getFund().getFundId(), payment.getAmount());
                            
                            log.info("Refunded payment {} - Amount: {} VND",
                                    payment.getId(), payment.getAmount());
                            
                        } catch (Exception ex) {
                            log.error("Failed to refund payment {}", payment.getId(), ex);
                        }
                    }

                    // Hoàn tiền cọc - đánh dấu deposit đã được refund
                    share.setDepositStatus(DepositStatus.REFUNDED);
                    ownershipShareRepository.save(share);

                } catch (Exception ex) {
                    log.error("Failed to refund deposit for user {} in group {}", 
                            share.getUser().getUserId(), groupId, ex);
                }
            }
        }
    }
}


