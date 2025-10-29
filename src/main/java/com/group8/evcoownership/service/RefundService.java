package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.entity.Refund;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.exception.ResourceNotFoundException;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.repository.RefundRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {
    
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    
    /**
     * Tạo yêu cầu refund cho payment đã hoàn thành
     */
    @Transactional
    public Refund createRefundRequest(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found"));
        
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only completed payments can be refunded");
        }
        
        // Tạo refund record
        String txnRef = System.currentTimeMillis() + "";
        Refund refund = Refund.builder()
                .dispute(null) // TODO: gắn Dispute nếu có
                .amount(payment.getAmount())
                .method(payment.getPaymentMethod())
                .txnRef(txnRef)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .note(reason)
                .provider("VNPAY")
                .build();
        
        return refundRepository.save(refund);
    }
    
    /**
     * Xử lý callback từ VNPay sau khi refund thành công
     */
    @Transactional
    public Refund confirmRefund(String refundTxnRef, String vnp_TransactionNo, String vnp_ResponseCode) {
        Refund refund = refundRepository.findByTxnRef(refundTxnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Refund not found"));
        
        if ("00".equals(vnp_ResponseCode)) {
            refund.setStatus("SUCCESS");
            refund.setSettledAt(LocalDateTime.now());
            refund.setProviderRefundRef(vnp_TransactionNo);
        } else {
            refund.setStatus("FAILED");
            refund.setReasonCode(vnp_ResponseCode);
        }
        
        return refundRepository.save(refund);
    }
    
    /**
     * Lấy danh sách refund
     */
    public List<Refund> getAllRefunds() {
        return refundRepository.findAll();
    }
}

