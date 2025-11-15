package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.FundTopupResponseDTO;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.exception.DepositPaymentException;
import com.group8.evcoownership.repository.MaintenanceRepository;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MaintenancePaymentService {

    private final PaymentRepository paymentRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final DepositPaymentService depositPaymentService; // để reuse generateUniqueTxnRef()
    private final VnPay_PaymentService vnPayPaymentService;

    @Transactional
    public FundTopupResponseDTO createMaintenancePayment(
            Long maintenanceId,
            HttpServletRequest servletRequest,
            Authentication auth
    ) {
        // 1) Lấy maintenance
        Maintenance m = maintenanceRepository.findById(maintenanceId)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found: " + maintenanceId));

        if (m.getLiableUser() == null) {
            throw new IllegalStateException("Maintenance does not have a liable user");
        }

        // 2) Xác thực: chỉ cho chính liableUser thanh toán (hoặc ADMIN, tuỳ bạn)
        String email = auth.getName();
        User payer = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

        if (!payer.getUserId().equals(m.getLiableUser().getUserId())) {
            throw new DepositPaymentException("You can only pay for your own maintenance liabilities");
        }

        if (m.getActualCost() == null || m.getActualCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Maintenance cost must be > 0 to create payment");
        }

        // (Optional) nếu đã FUNDED hoặc COMPLETED thì không cho tạo nữa
        if ("FUNDED".equalsIgnoreCase(m.getStatus()) || "COMPLETED".equalsIgnoreCase(m.getStatus())) {
            throw new IllegalStateException("Maintenance already funded or completed");
        }

        // 3) Sinh txnRef
        String txnRef = depositPaymentService.generateUniqueTxnRef();

        // 4) Tạo Payment PERSONAL, không gắn SharedFund
        Payment payment = new Payment();
        payment.setTransactionCode(txnRef);
        payment.setPaymentType(PaymentType.MAINTENANCE_FEE); // bạn thêm enum này nếu chưa có
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(m.getActualCost());  // thanh toán full chi phí maintenance
        payment.setPaymentDate(LocalDateTime.now());
        payment.setPaymentMethod("VNPAY");
        payment.setPayer(payer);

        payment.setPaymentCategory("PERSONAL");
        payment.setChargedUser(m.getLiableUser());
        payment.setPersonalReason("MAINTENANCE");
        payment.setFund(null); // ✨ IMPORTANT: không đụng SharedFund

        // nếu có sourceType/sourceId:
        // payment.setSourceType("MAINTENANCE");
        // payment.setSourceId(m.getId());

        payment = paymentRepository.save(payment);

        // 5) Tạo URL VNPay (groupId có thể lấy từ vehicle.ownershipGroup nếu muốn)
        Long groupId = (m.getVehicle() != null && m.getVehicle().getOwnershipGroup() != null)
                ? m.getVehicle().getOwnershipGroup().getGroupId()
                : null;

        String vnpayUrl = vnPayPaymentService.createPaymentUrl(
                payment.getAmount().longValue(),
                servletRequest,
                txnRef,
                groupId
        );

        // 6) Trả về response (tái dùng FundTopupResponseDTO cho đỡ tạo thêm DTO)
        return FundTopupResponseDTO.builder()
                .paymentId(payment.getId())
                .userId(payer.getUserId())
                .groupId(groupId) // có thể null
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionCode(txnRef)
                .paidAt(null)
                .vnpayUrl(vnpayUrl)
                .message("Created maintenance personal payment")
                .build();
    }

    @Transactional
    public FundTopupResponseDTO confirmMaintenancePayment(String txnRef, String transactionNo) {
        Payment payment = depositPaymentService.getLatestPaymentByTxnRef(txnRef);

        if (payment.getPaymentType() != PaymentType.MAINTENANCE_FEE) {
            throw new IllegalStateException("TxnRef does not belong to MAINTENANCE payment");
        }

        // Idempotent
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return map(payment, "Already completed");
        }

        // providerResponse
        String providerResponse = String.format(
                "{\"vnp_TransactionNo\":\"%s\",\"vnp_TxnRef\":\"%s\"}",
                transactionNo, txnRef
        );

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setProviderResponse(providerResponse);
        paymentRepository.save(payment);

        // Ở đây KHÔNG cộng SharedFund
        // Nhưng có thể update Maintenance → FUNDED
        // Giả sử có field Maintenance.fundedAt:

        // Tìm maintenance tương ứng:
        // Nếu bạn có sourceType/sourceId thì truy trực tiếp, còn không thì phải query ngược:
        // ví dụ: maintenanceRepository.findById(payment.getSourceId())...

        // Tạm giả sử bạn tìm được m:
        // m.setStatus("FUNDED");
        // m.setFundedAt(LocalDateTime.now());
        // maintenanceRepository.save(m);

        return map(payment, "Maintenance personal payment completed");
    }

    // =============== 3) INFO BY TXN ===============
    @Transactional
    public FundTopupResponseDTO getMaintenancePaymentInfoByTxn(String txnRef) {
        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() ->
                        new IllegalArgumentException("Payment not found: " + txnRef));

        // (khuyến khích) đảm bảo đây là payment dành cho maintenance
        if (payment.getPaymentType() != PaymentType.MAINTENANCE_FEE) {
            throw new IllegalStateException("TxnRef does not belong to MAINTENANCE payment");
        }

        return map(payment, "OK");
    }

    // =============== Helper map ===============

    private FundTopupResponseDTO map(Payment p, String message) {
        Long groupId = null;

        // Nếu payment này gắn với SharedFund (fund != null) thì lấy groupId từ đó
        if (p.getFund() != null && p.getFund().getGroup() != null) {
            groupId = p.getFund().getGroup().getGroupId();
        }
        // Với payment PERSONAL cho maintenance: fund = null → groupId sẽ là null (OK)


        return FundTopupResponseDTO.builder()
                .paymentId(p.getId())
                .userId(p.getPayer() != null ? p.getPayer().getUserId() : null)
                .groupId(groupId)
                .amount(p.getAmount())
                .status(p.getStatus())
                .transactionCode(p.getTransactionCode())
                .paidAt(p.getPaidAt())
                .vnpayUrl(null)
                .message(message)
                .build();
    }
}



