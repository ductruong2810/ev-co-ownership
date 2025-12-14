package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.FundTopupRequestDTO;
import com.group8.evcoownership.dto.FundTopupResponseDTO;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.exception.DepositPaymentException;
import com.group8.evcoownership.repository.ContractRepository;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Nạp quỹ (OPERATING) qua VNPay, tái dùng Payment/SharedFund.
 * - createFundTopup(): tạo Payment PENDING + trả vnpayUrl
 * - confirmFundTopup(): COMPLETED + cộng vào SharedFund.balance
 * - getFundTopupInfoByTxn(): tra cứu theo txnRef
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FundPaymentService {

    private final FundService fundService;
    private final DepositPaymentService depositPaymentService;
    private final PaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final SharedFundRepository sharedFundRepository;
    private final UserRepository userRepository;
    private final VnPay_PaymentService vnPayPaymentService;

    // ham phu
    private Long parseId(String id, String fieldName) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new DepositPaymentException(fieldName + " must be a valid number");
        }
    }


    // =============== CREATE ===============
    @Transactional
    public FundTopupResponseDTO createFundTopup(
            FundTopupRequestDTO request,
            HttpServletRequest servletRequest,
            Authentication auth
    ) {
        // validate user and group
        Long userId = parseId(request.userId(), "userId");
        Long groupId = parseId(request.groupId(), "groupId");

        //  Xác thực user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        String authenticatedEmail = auth.getName();
        if (!user.getEmail().equals(authenticatedEmail)) {
            throw new DepositPaymentException("You can only create fund top-up for your own account");
        }

        // Kiểm tra contract tồn tại - lấy contract mới nhất nếu có nhiều
        contractRepository.findByGroupGroupIdOrderByCreatedAtDesc(groupId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for this group"));


        // 1) Validate
        SharedFund fund = sharedFundRepository
                .findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING)
                .orElseThrow(() -> new IllegalArgumentException("Fund not found for group: " + groupId));

        if (!FundType.OPERATING.equals(fund.getFundType())) {
            throw new IllegalArgumentException("Only OPERATING fund can be topped up");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }


        // 2) Sinh txnRef duy nhất
        String txnRef = depositPaymentService.generateUniqueTxnRef();

        // 3) Lưu Payment ở trạng thái PENDING
        Payment payment = new Payment();
        payment.setTransactionCode(txnRef);               // map vnp_TxnRef
        payment.setPaymentType(PaymentType.CONTRIBUTION);   // phân biệt với DEPOSIT
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(request.amount());
        payment.setFund(fund);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentMethod("VNPAY");                     // nếu cột này NOT NULL
        payment.setPayer(user);
        payment.setPaymentCategory("GROUP");

        payment = paymentRepository.save(payment);

        // 4) Lấy URL VNPay
        //String ip = httpReq.getRemoteAddr();
        String vnpayUrl = vnPayPaymentService.createPaymentUrl(request.amount().longValue(), servletRequest, txnRef, groupId);

        // 5) Trả response
        return FundTopupResponseDTO.builder()
                .paymentId(payment.getId())
                .userId(payment.getPayer().getUserId())
                .groupId(fund.getGroup().getGroupId())
                .amount(request.amount())
                .status(PaymentStatus.PENDING)
                .transactionCode(txnRef)
                .paidAt(null)
                .vnpayUrl(vnpayUrl)
                .message("Created fund top-up request")
                .build();
    }

    // =============== CONFIRM (server->server hoặc từ callback public) ===============
    @Transactional
    public FundTopupResponseDTO confirmFundTopup(String txnRef, String transactionNo) {
        Payment payment = depositPaymentService.getLatestPaymentByTxnRef(txnRef);


        if (payment.getPaymentType() != PaymentType.CONTRIBUTION) {
            throw new IllegalStateException("TxnRef does not belong to FUND_TOPUP");
        }

        // Idempotent
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return map(payment, "Already completed");
        }

        // (Optional) verify chữ ký với VnPayService nếu bạn đã implement

        // providerResponse
        String providerResponse = String.format(
                "{\"vnp_TransactionNo\":\"%s\",\"vnp_TxnRef\":\"%s\"}",
                transactionNo, txnRef
        );


        // 1) Cập nhật trạng thái
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setProviderResponse(providerResponse);
        paymentRepository.save(payment);

        // 2) Cộng số dư quỹ
        Long groupId = payment.getFund().getGroup().getGroupId();
        fundService.topUpOperating(groupId, payment.getAmount()); // cộng vào OPERATING

        return map(payment, "Fund top-up completed");
    }

    // =============== GET INFO BY TXN REF ===============
    @Transactional
    public FundTopupResponseDTO getFundTopupInfoByTxn(String txnRef) {
        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + txnRef));
        return map(payment, "OK");
    }


    private FundTopupResponseDTO map(Payment p, String message) {
        return FundTopupResponseDTO.builder()
                .paymentId(p.getId())
                .userId(p.getId())
                .groupId(p.getFund().getGroup().getGroupId())
                .userId(p.getPayer() != null ? p.getPayer().getUserId() : null) // nếu có payer
                .amount(p.getAmount())
                .status(p.getStatus())
                .transactionCode(p.getTransactionCode())
                .paidAt(p.getPaidAt())
                .vnpayUrl(null)
                .message(message)
                .build();
    }
}
