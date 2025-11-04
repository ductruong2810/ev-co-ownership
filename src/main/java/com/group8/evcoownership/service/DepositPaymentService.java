package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DepositPaymentRequestDTO;
import com.group8.evcoownership.dto.DepositPaymentResponseDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.ContractApprovalStatus;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.exception.DepositPaymentException;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepositPaymentService {

    private final FundService fundService;

    private final PaymentRepository paymentRepository;
    private final OwnershipShareRepository shareRepository;
    private final ContractRepository contractRepository;
    private final SharedFundRepository sharedFundRepository;
    private final UserRepository userRepository;
    private final OwnershipGroupRepository groupRepository;
    private final VnPay_PaymentService vnPayPaymentService;
    private final DepositCalculationService depositCalculationService;
    private final VehicleRepository vehicleRepository;


    private Long parseId(String id, String fieldName) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new DepositPaymentException(fieldName + " must be a valid number");
        }
    }

    /**
     * Tạo payment mới → sinh URL thanh toán VNPay
     */
    @Transactional
    public DepositPaymentResponseDTO createDepositPayment(DepositPaymentRequestDTO request,
                                                          HttpServletRequest servletRequest,
                                                          Authentication authentication) {

        Long userId = parseId(request.userId(), "userId");
        Long groupId = parseId(request.groupId(), "groupId");

        //  Xác thực user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        String authenticatedEmail = authentication.getName();
        if (!user.getEmail().equals(authenticatedEmail)) {
            throw new DepositPaymentException("You can only create deposit payment for your own account");
        }

        // Kiểm tra group, membership, contract, fund
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        OwnershipShare share = shareRepository.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        // Kiểm tra nếu người dùng đã đóng tiền cọc rồi (PAID) → chặn
        if (share.getDepositStatus() == DepositStatus.PAID) {
            throw new DepositPaymentException("Deposit has already been paid for this user in this group.");
        }

        // Kiểm tra contract tồn tại
        contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for this group"));

        SharedFund fund = sharedFundRepository.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Fund not found for group: " + groupId));

        // Tính toán số tiền cần đặt cọc
        BigDecimal requiredAmount;
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);

        if (vehicle != null && vehicle.getVehicleValue() != null) {
            requiredAmount = depositCalculationService.calculateRequiredDepositAmount(
                    vehicle.getVehicleValue(),
                    share.getOwnershipPercentage()
            );
        } else {
            requiredAmount = depositCalculationService.calculateRequiredDepositAmount(
                    group.getMemberCapacity()
            );
        }
        // Tạo record Payment trong DB với mã giao dịch đảm bảo duy nhất
        String txnRef = generateUniqueTxnRef();

        Payment payment = Payment.builder()
                .payer(user)
                .fund(fund)
                .amount(requiredAmount)
                .paymentMethod("VNPAY")
                .paymentType(PaymentType.DEPOSIT)
                .status(PaymentStatus.PENDING)
                .paymentCategory("GROUP")
                .transactionCode(txnRef)
                .build();
        payment = paymentRepository.save(payment);

        // Sinh link thanh toán VNPay
        //String paymentUrl = vnPayPaymentService.createDepositPaymentUrl(requiredAmount.longValue(), servletRequest);
        String paymentUrl = vnPayPaymentService.createDepositPaymentUrl(requiredAmount.longValue(), servletRequest, txnRef, groupId);


        return DepositPaymentResponseDTO.builder()
                .paymentId(payment.getId())
                .userId(user.getUserId())
                .groupId(group != null ? group.getGroupId() : null)
                .amount(requiredAmount) // hoặc BigDecimal.valueOf(requiredAmount.longValue())
                .requiredAmount(requiredAmount)
                .paymentMethod("VNPAY")
                .status(PaymentStatus.PENDING)
                .transactionCode(txnRef)
                .vnpayUrl(paymentUrl)
                .message("Deposit payment created successfully. Please complete the payment via VNPay.")
                .build();
    }


    /**
     * Xác nhận callback từ VNPay → cập nhật Payment COMPLETED
     */
    @Transactional
    public DepositPaymentResponseDTO confirmDepositPayment(String txnRef, String transactionNo) {
        Payment payment = getLatestPaymentByTxnRef(txnRef);

        // Nếu đã COMPLETED thì bỏ qua
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return convertToResponse(payment);
        }

        String providerResponse = String.format(
                "{\"vnp_TransactionNo\":\"%s\",\"vnp_TxnRef\":\"%s\"}",
                transactionNo, txnRef
        );

        // 1. Cập nhật trạng thái Payment
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setProviderResponse(providerResponse);
        paymentRepository.save(payment);

        // 2. Cập nhật quỹ (Fund)
        fundService.increaseBalance(payment.getFund().getFundId(), payment.getAmount());

        // 3. Cập nhật trạng thái tiền cọc trong OwnershipShare
        OwnershipShare share = shareRepository.findByUserIdAndFundId(
                payment.getPayer().getUserId(),
                payment.getFund().getFundId()
        ).orElseThrow(() -> new EntityNotFoundException(
                String.format("OwnershipShare not found for user %d in fund %d",
                        payment.getPayer().getUserId(), payment.getFund().getFundId())
        ));

        share.setDepositStatus(DepositStatus.PAID);
        shareRepository.save(share);

        // 4. Trả response
        return convertToResponse(payment);
    }
//    public DepositPaymentResponse confirmDepositPayment(String txnRef, String transactionNo) {
//        Payment payment = paymentRepository.findByTransactionCode(txnRef)
//                .orElseThrow(() -> new RuntimeException("Payment not found for txnRef: " + txnRef));
//
//        if (payment.getStatus() == PaymentStatus.COMPLETED) {
//            return convertToResponse(payment);
//        }
//
//        payment.setStatus(PaymentStatus.COMPLETED);
//        payment.setPaymentDate(LocalDateTime.now());
//        paymentRepository.save(payment);
//
//        return convertToResponse(payment);
//    }


    /**
     * API cho frontend kiểm tra trạng thái thanh toán
     */
    public DepositPaymentResponseDTO getByTxnRef(String txnRef) {
        Payment payment = getLatestPaymentByTxnRef(txnRef);
        return convertToResponse(payment);
    }

    private DepositPaymentResponseDTO convertToResponse(Payment p) {
        return DepositPaymentResponseDTO.builder()
                .paymentId(p.getId())
                .userId(p.getPayer() != null ? p.getPayer().getUserId() : null)
                .groupId(p.getFund() != null && p.getFund().getGroup() != null ? p.getFund().getGroup().getGroupId() : null)
                .amount(p.getAmount())
                .paymentMethod("VNPAY")
                .status(PaymentStatus.valueOf(p.getStatus().name()))
                .transactionCode(p.getTransactionCode())
                .paidAt(p.getPaymentDate())
                .build();
    }


    /**
     * Lấy thông tin deposit của user trong group
     */
    public Map<String, Object> getDepositInfo(Long userId, Long groupId) {
        OwnershipShare share = shareRepository.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found"));

        // Tính toán số tiền cọc dựa trên tỷ lệ sở hữu
        BigDecimal requiredAmount = calculateDepositAmountForUser(share.getGroup(), share);

        Map<String, Object> info = new HashMap<>();
        info.put("userId", userId);
        info.put("groupId", groupId);
        info.put("depositStatus", share.getDepositStatus());
        info.put("requiredAmount", requiredAmount);
        info.put("ownershipPercentage", share.getOwnershipPercentage());
        boolean isSignedOrApproved = contract.getApprovalStatus() == ContractApprovalStatus.SIGNED
                || contract.getApprovalStatus() == ContractApprovalStatus.APPROVED;
        info.put("contractSigned", isSignedOrApproved);
        info.put("canPay", isSignedOrApproved && share.getDepositStatus() == DepositStatus.PENDING);

        // Thêm contract status
        info.put("contractStatus", getContractStatus(groupId));


        return info;
    }

    /**
     * Lấy danh sách deposit status của tất cả members trong group
     */
    public List<Map<String, Object>> getGroupDepositStatus(Long groupId) {
        List<OwnershipShare> shares = shareRepository.findByGroupGroupId(groupId);
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Lấy contract status 1 lần cho hiệu quả
        String contractStatus = getContractStatus(groupId);

        return shares.stream().map(share -> {
            Map<String, Object> status = new HashMap<>();
            status.put("userId", share.getUser().getUserId());
            status.put("userName", share.getUser().getFullName());
            status.put("userEmail", share.getUser().getEmail());
            status.put("depositStatus", share.getDepositStatus());
            status.put("ownershipPercentage", share.getOwnershipPercentage());
            status.put("joinDate", share.getJoinDate());

            // Tính toán số tiền cọc cho user này
            BigDecimal depositAmount = calculateDepositAmountForUser(group, share);
            status.put("requiredDepositAmount", depositAmount);

            // Thêm contract status
            status.put("contractStatus", contractStatus);

            return status;
        }).toList();
    }

    /**
     * Tính toán số tiền cọc cho user dựa trên tỷ lệ sở hữu
     */
    private BigDecimal calculateDepositAmountForUser(OwnershipGroup group, OwnershipShare share) {
        // Tìm Vehicle của group để lấy giá trị xe (sử dụng VehicleRepository)
        Vehicle vehicle = vehicleRepository.findByOwnershipGroup(group).orElse(null);

        if (vehicle != null) {
            // Sử dụng công thức mới: vehicleValue * 10% * ownershipPercentage / 100
            return depositCalculationService.calculateRequiredDepositAmount(
                    vehicle.getVehicleValue(),
                    share.getOwnershipPercentage()
            );
        } else {
            // Fallback về công thức cũ nếu không có vehicleValue
            return depositCalculationService.calculateRequiredDepositAmount(group.getMemberCapacity());
        }
    }

    @SuppressWarnings("unused")
    private boolean canAutoApproveContract(List<OwnershipShare> shares) {
        if (shares.isEmpty()) {
            return false;
        }

        // Lấy group để kiểm tra memberCapacity
        OwnershipGroup group = shares.get(0).getGroup();
        Integer memberCapacity = group.getMemberCapacity();

        // Kiểm tra số lượng thành viên thực tế = memberCapacity
        return memberCapacity != null && shares.size() == memberCapacity;
    }

    private String getContractStatus(Long groupId) {
        Optional<Contract> contract = contractRepository.findByGroupGroupId(groupId);

        return contract.map(value -> value.getApprovalStatus().name()).orElse("NO_CONTRACT");

    }

    /**
     * Lấy thông tin chi tiết của thanh toán dựa theo mã giao dịch (txnRef)
     */
    public DepositPaymentResponseDTO getDepositInfoByTxn(String txnRef) {
        Payment payment = getLatestPaymentByTxnRef(txnRef);

        return convertToResponse(payment);
    }

    private Payment getLatestPaymentByTxnRef(String txnRef) {
        List<Payment> payments = paymentRepository.findAllByTransactionCodeOrderByIdDesc(txnRef);

        if (payments.isEmpty()) {
            throw new ResourceNotFoundException("Payment not found for txnRef: " + txnRef);
        }

        if (payments.size() > 1) {
            log.warn("Multiple payments ({}) found for txnRef {}. Using the most recent entry with id {}.",
                    payments.size(), txnRef, payments.get(0).getId());
        }

        return payments.get(0);
    }

    private String generateUniqueTxnRef() {
        final int maxAttempts = 10;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long randomNumber = ThreadLocalRandom.current().nextLong(1_000_000_000_000L);
            String candidate = String.format("%012d", randomNumber);

            if (!paymentRepository.existsByTransactionCode(candidate)) {
                return candidate;
            }
        }

        throw new DepositPaymentException("Unable to generate a unique transaction reference. Please try again later.");
    }

    // ========== DEPOSIT REFUND METHODS ==========

    /**
     * Hoàn tiền cọc cho tất cả members đã đóng khi contract bị reject
     *
     * @param shares  Danh sách OwnershipShare của group
     * @param groupId ID của ownership group
     */
    @Transactional
    public void refundDepositsForGroup(List<OwnershipShare> shares, Long groupId) {
        for (OwnershipShare share : shares) {
            if (share.getDepositStatus() == DepositStatus.PAID) {
                try {
                    // Tìm payment deposits cho user này
                    List<Payment> deposits = paymentRepository.findAllByPayer_UserIdAndStatusAndPaymentType(
                                    share.getUser().getUserId(),
                                    PaymentStatus.COMPLETED,
                                    PaymentType.DEPOSIT,
                                    null
                            ).stream()
                            .filter(p -> p.getFund().getGroup().getGroupId().equals(groupId))
                            .toList();

                    // Hoàn tiền cọc qua VNPay API cho từng payment
                    for (Payment payment : deposits) {
                        try {
                            refundSinglePayment(payment);
                        } catch (Exception ex) {
                            log.error("Failed to refund payment {}", payment.getId(), ex);
                        }
                    }

                    // Đánh dấu deposit đã được refund
                    share.setDepositStatus(DepositStatus.REFUNDED);
                    shareRepository.save(share);

                    log.info("Successfully refunded deposits for user {} in group {}",
                            share.getUser().getUserId(), groupId);

                } catch (Exception ex) {
                    log.error("Failed to refund deposit for user {} in group {}",
                            share.getUser().getUserId(), groupId, ex);
                }
            }
        }
    }

    /**
     * Hoàn tiền cho một payment đơn lẻ qua VNPay API
     *
     * @param payment Payment cần hoàn tiền
     */
    private void refundSinglePayment(Payment payment) throws IOException {
        // Parse vnp_TransactionNo từ providerResponse
        String vnpTransactionNo = VnPay_PaymentService.extractTransactionNo(payment.getProviderResponse());
        if (vnpTransactionNo == null) {
            log.warn("Cannot extract vnp_TransactionNo for payment {}. Skipping refund.", payment.getId());
            return;
        }

        // Format vnp_TransactionDate từ payment.paymentDate
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnpTransactionDate = formatter.format(
                Date.from(payment.getPaymentDate().atZone(ZoneId.systemDefault()).toInstant())
        );

        // Tạo refund request URL từ VNPay
        String refundUrl = vnPayPaymentService.createRefundRequest(
                payment.getAmount().longValue(),
                payment.getTransactionCode(),
                vnpTransactionNo,
                vnpTransactionDate
        );

        log.info("VNPay Refund URL created for payment {}: {}", payment.getId(), refundUrl);

        // Gọi HTTP GET đến VNPay để thực hiện refund
        URL url = new URL(refundUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        log.info("VNPay Refund Response Code for payment {}: {}", payment.getId(), responseCode);

        // Đọc response
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            log.info("VNPay Refund Response for payment {}: {}", payment.getId(), response);
        }

        // Đánh dấu payment REFUNDED và trừ quỹ
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        fundService.decreaseBalance(payment.getFund().getFundId(), payment.getAmount());

        log.info("Successfully refunded payment {} - Amount: {} VND",
                payment.getId(), payment.getAmount());
    }



}
