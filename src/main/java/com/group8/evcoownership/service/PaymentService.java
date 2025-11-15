package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.NotificationType;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hibernate.Hibernate.isInitialized;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final UserRepository userRepo;
    private final SharedFundRepository fundRepo;
    private final FundService fundService;
    private final NotificationOrchestrator notificationOrchestrator;


    /**
     * Ham lay lich su giao dich ca nhan trong nhom
     */
    // Trong PaymentService
    @Transactional(readOnly = true)
    public PaymentHistoryResponseDTO getPersonalHistoryAllGroups(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate,
            Integer page,
            Integer size
    ) {
        if (userId == null) throw new IllegalArgumentException("userId required");

        int p = (page == null) ? 0 : Math.max(0, page);
        int s = (size == null) ? 20 : Math.min(Math.max(1, size), 200);

        LocalDateTime fromAt = (fromDate == null) ? null : fromDate.atStartOfDay();
        LocalDateTime toAt = (toDate == null) ? null : toDate.atTime(23, 59, 59);

        Pageable pageable = PageRequest.of(
                p, s,
                Sort.by(Sort.Direction.DESC, "paymentDate")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        var pageResult = paymentRepo.searchUserHistoryCompleted(userId, fromAt, toAt, pageable);

        var items = pageResult.getContent().stream().map(pmt -> {
            Long fundId = (pmt.getFund() != null) ? pmt.getFund().getFundId() : null;
            // lấy groupId/Name qua fund; gọi getId của proxy Hibernate không ép load
            Long gid = null;
            String gname = null;
            if (pmt.getFund() != null && pmt.getFund().getGroup() != null) {
                gid = pmt.getFund().getGroup().getGroupId();
                gname = pmt.getFund().getGroup().getGroupName(); // nếu entity có
            }

            return PaymentHistoryItemDTO.builder()
                    .paymentId(pmt.getId())
                    .fundId(fundId)
                    .groupId(gid)
                    .groupName(gname)
                    .amount(pmt.getAmount())
                    .paymentMethod(pmt.getPaymentMethod())
                    .status(pmt.getStatus() != null ? pmt.getStatus().name() : null)
                    .paymentType(pmt.getPaymentType() != null ? pmt.getPaymentType().name() : null)
                    .transactionCode(pmt.getTransactionCode())
                    .paymentDate(pmt.getPaymentDate())
                    .build();
        }).toList();

        var totalCompleted = paymentRepo.sumUserCompleted(userId, fromAt, toAt);

        return PaymentHistoryResponseDTO.builder()
                .userId(userId)
                .totalCompletedAmount(totalCompleted)
                .items(items)
                .build();
    }


    /**
     * Helper for Payment History\
     * Mapper nhỏ: Payment -> PaymentHistoryItemDTO
     */
    // ====== Mapper nhỏ: Payment -> PaymentHistoryItemDTO ======
    private PaymentHistoryItemDTO toHistoryItem(Payment p) {
        Long fundId = (p.getFund() != null) ? p.getFund().getFundId() : null;

        return PaymentHistoryItemDTO.builder()
                .paymentId(p.getId())
                .fundId(fundId)
                .amount(p.getAmount())
                .paymentMethod(p.getPaymentMethod())
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .paymentType(p.getPaymentType() != null ? p.getPaymentType().name() : null)
                .transactionCode(p.getTransactionCode())
                .paymentDate(p.getPaymentDate())
                .build();
    }
    // ====================================================================================


    // Map Entity -> DTO
    private PaymentResponseDTO toDto(Payment p) {
        Long uId = null;
        Long fId = null;
        String uName = null;
        User u = p.getPayer();
        if (u != null && isInitialized(u)) {
            uId = u.getUserId();
            uName = u.getFullName();
        }
        if (p.getFund() != null && isInitialized(p.getFund())) {
            fId = p.getFund().getFundId();
        }
        return PaymentResponseDTO.builder()
                .id(p.getId())
                .userId(uId)
                .fundId(fId)
                .userFullName(uName)
                .amount(p.getAmount())
                .paymentDate(p.getPaymentDate())
                .paymentMethod(p.getPaymentMethod())
                .status(p.getStatus().name())
                .transactionCode(p.getTransactionCode())
                .providerResponse(p.getProviderResponse())
                .paymentType(String.valueOf(p.getPaymentType()))
                .build();
    }

    @Transactional
    public PaymentResponseDTO create(PaymentRequestDTO req) {
        User user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + req.getUserId()));

        SharedFund fund = fundRepo.findById(req.getFundId())
                .orElseThrow(() -> new EntityNotFoundException("Fund not found: " + req.getFundId()));

        Payment p = Payment.builder()
                .payer(user)
                .fund(fund)
                .amount(req.getAmount())
                .paymentMethod(req.getPaymentMethod())
                .paymentType(PaymentType.valueOf(req.getPaymentType()))
                .status(PaymentStatus.PENDING)
                .paymentCategory("GROUP")
                .transactionCode(req.getTransactionCode())
                .build();

        return toDto(paymentRepo.save(p));
    }

    @Transactional(readOnly = true)
    public PaymentResponseDTO getById(Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> search(Long userId, String status, String type,
                                           int page, int size, String sort, boolean asc) {
        // sort: dùng tên field của entity Payment (vd: id, paymentDate, amount)
        Sort sortObj = asc ? Sort.by(sort).ascending() : Sort.by(sort).descending();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 200), sortObj);

        List<Payment> list;
        if (userId != null && status != null && type != null) {
            list = paymentRepo.findAllByPayer_UserIdAndStatusAndPaymentType(userId, PaymentStatus.valueOf(status), PaymentType.valueOf(type), pageable);
        } else if (userId != null) {
            list = paymentRepo.findAllByPayer_UserId(userId, pageable);
        } else if (status != null) {
            list = paymentRepo.findAllByStatus(PaymentStatus.valueOf(status), pageable);
        } else if (type != null) {
            list = paymentRepo.findAllByPaymentType(PaymentType.valueOf(type), pageable);
        } else {
            list = paymentRepo.findAllBy(pageable);
        }
        return list.stream().map(this::toDto).toList();
    }

    @Transactional
    public PaymentResponseDTO update(Long id, UpdatePaymentRequestDTO req) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if (req.getUserId() != null) {
            User user = userRepo.findById(req.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + req.getUserId()));
            p.setPayer(user);
        }
        if (req.getAmount() != null) p.setAmount(req.getAmount());
        if (req.getPaymentMethod() != null) p.setPaymentMethod(req.getPaymentMethod());
        if (req.getPaymentType() != null) p.setPaymentType(PaymentType.valueOf(req.getPaymentType()));
        if (req.getTransactionCode() != null) p.setTransactionCode(req.getTransactionCode());
        if (req.getProviderResponse() != null) p.setProviderResponse(req.getProviderResponse());
        if (req.getStatus() != null) {
            p.setStatus(PaymentStatus.valueOf(req.getStatus()));
            if (PaymentStatus.COMPLETED.equals(PaymentStatus.valueOf(req.getStatus())) && p.getPaymentDate() == null) {
                p.setPaymentDate(LocalDateTime.now());
            }
        }
        return toDto(paymentRepo.save(p));
    }

    @Transactional
    public void delete(Long id) {
        if (!paymentRepo.existsById(id)) {
            throw new EntityNotFoundException("Payment not found: " + id);
        }
        paymentRepo.deleteById(id);
    }

    @Transactional
    public PaymentResponseDTO markPaid(Long id, String transactionCode, String providerResponseJson) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if (PaymentStatus.COMPLETED.equals(p.getStatus())) {
            return toDto(p);
        }

        // Chỉ cho phép từ Pending -> Completed
        if (!PaymentStatus.PENDING.equals(p.getStatus())) {
            throw new IllegalStateException("Invalid transition: " + p.getStatus() + " -> Completed");
        }

        // Nếu đã có transactionCode khác, chặn cập nhật để chống gắn nhầm giao dịch
        if (p.getTransactionCode() != null
                && transactionCode != null
                && !transactionCode.equals(p.getTransactionCode())) {
            throw new IllegalStateException("Transaction code mismatch");
        }

        if (transactionCode != null) {
            p.setTransactionCode(transactionCode);
        }
        p.setStatus(PaymentStatus.COMPLETED);
        if (p.getPaymentDate() == null) {
            p.setPaymentDate(LocalDateTime.now());
        }
        if (providerResponseJson != null) {
            p.setProviderResponse(providerResponseJson);
        }

        return toDto(paymentRepo.save(p));
    }

    @Transactional
    public PaymentResponseDTO markFailed(Long id, String providerResponseJson) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if (PaymentStatus.FAILED.equals(p.getStatus())) {
            return toDto(p);
        }

        // Chỉ cho phép từ Pending -> Failed
        if (!PaymentStatus.PENDING.equals(p.getStatus())) {
            throw new IllegalStateException("Invalid transition: " + p.getStatus() + " -> Failed");
        }

        p.setStatus(PaymentStatus.FAILED);
        if (providerResponseJson != null) {
            p.setProviderResponse(providerResponseJson);
        }

        return toDto(paymentRepo.save(p));
    }

    @Transactional
    public PaymentResponseDTO markRefunded(Long id, String providerResponseJson) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if (!PaymentStatus.COMPLETED.equals(p.getStatus())) {
            throw new IllegalStateException("Invalid transition: " + p.getStatus() + " -> Refunded");
        }
        p.setStatus(PaymentStatus.REFUNDED);
        if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);
        return toDto(paymentRepo.save(p));
    }

    // Các chuyển trạng thái hợp lệ
    private static final Set<String> ALLOWED = Set.of(
            key(PaymentStatus.PENDING, PaymentStatus.COMPLETED),
            key(PaymentStatus.PENDING, PaymentStatus.FAILED),
            key(PaymentStatus.COMPLETED, PaymentStatus.REFUNDED)
    );

    private static String key(PaymentStatus from, PaymentStatus to) {
        return from.name() + "->" + to.name();
    }

    @Transactional
    public PaymentResponseDTO updateStatus(Long paymentId, PaymentStatus target,
                                           String transactionCode, String providerResponseJson) {

        Payment p = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        PaymentStatus current = p.getStatus();
        if (current == null) current = PaymentStatus.PENDING; // fallback

        // Idempotent: nếu đã ở đúng trạng thái, chỉ cập nhật providerResponse (nếu có) rồi trả về
        if (current == target) {
            if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);
            return toDto(paymentRepo.save(p));
        }

        String transition = key(current, target);
        if (!ALLOWED.contains(transition)) {
            throw new IllegalStateException("Invalid status transition: " + current + " -> " + target);
        }

        switch (target) {
            case FAILED -> {
                // Pending -> Failed
                p.setStatus(PaymentStatus.FAILED);
                if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);
                paymentRepo.save(p);

                notificationOrchestrator.sendComprehensiveNotification(
                        p.getPayer().getUserId(),
                        NotificationType.PAYMENT_FAILED,
                        "Payment Failed",
                        String.format("Your payment of %s VND has failed. Please try again.", p.getAmount()),
                        buildEmailData(p, PaymentStatus.FAILED)
                );
            }
            case COMPLETED -> {
                // Pending -> Completed
                if (transactionCode == null || transactionCode.isBlank()) {
                    throw new IllegalArgumentException("transactionCode is required when marking Completed");
                }
                p.setStatus(PaymentStatus.COMPLETED);
                p.setTransactionCode(transactionCode);
                if (p.getPaymentDate() == null) p.setPaymentDate(LocalDateTime.now());
                if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);

                paymentRepo.saveAndFlush(p); // flush trước khi cộng quỹ
                // Cộng quỹ đúng 1 lần khi chốt Completed
                fundService.increaseBalance(p.getFund().getFundId(), p.getAmount());

                notificationOrchestrator.sendComprehensiveNotification(
                        p.getPayer().getUserId(),
                        NotificationType.PAYMENT_SUCCESS,
                        "Payment Successful",
                        String.format("Your payment of %s VND has been processed successfully", p.getAmount()),
                        buildEmailData(p, PaymentStatus.COMPLETED)
                );
            }
            case REFUNDED -> {
                // Completed -> Refunded
                p.setStatus(PaymentStatus.REFUNDED);
                if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);

                paymentRepo.saveAndFlush(p);
                // Hoàn quỹ đúng 1 lần khi chuyển Refunded
                fundService.decreaseBalance(p.getFund().getFundId(), p.getAmount());
            }
            default -> throw new IllegalStateException("Unsupported target: " + target);
        }

        return toDto(paymentRepo.save(p));
    }

    private Map<String, Object> buildEmailData(Payment payment, PaymentStatus status) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", payment.getId());
        data.put("transactionCode", payment.getTransactionCode());
        data.put("amount", payment.getAmount());
        data.put("paymentMethod", payment.getPaymentMethod());
        data.put("paymentType", payment.getPaymentType());
        data.put("paymentDate", payment.getPaymentDate());
        data.put("status", status.name());
        return data;
    }

}

