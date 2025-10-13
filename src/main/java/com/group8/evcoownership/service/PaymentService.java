package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.CreatePaymentRequest;
import com.group8.evcoownership.dto.PaymentResponse;
import com.group8.evcoownership.dto.UpdatePaymentRequest;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final UserRepository userRepo;
    private final SharedFundRepository fundRepo;
    private final FundService fundService;

    // Map Entity -> DTO
    private PaymentResponse toDto(Payment p) {
        Long uId = null;
        Long fId = null;
        String uName = null;
        User u = p.getUser();
        if (u != null && Hibernate.isInitialized(u)) {
            uId = u.getUserId();
            uName = u.getFullName();
        }
        return PaymentResponse.builder()
                .id(p.getId())
                .userId(uId)
                .fundId(fId)
                .userFullName(uName)
                .amount(p.getAmount())
                .paymentDate(p.getPaymentDate())
                .paymentMethod(p.getPaymentMethod())
                .status(p.getStatus())
                .transactionCode(p.getTransactionCode())
                .providerResponse(p.getProviderResponse())
                .paymentType(String.valueOf(p.getPaymentType()))
                .build();
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest req) {
        User user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + req.getUserId()));

        SharedFund fund = fundRepo.findById(req.getFundId())
                .orElseThrow(() -> new EntityNotFoundException("Fund not found: " + req.getFundId()));

        Payment p = Payment.builder()
                .user(user)
                .fund(fund)
                .amount(req.getAmount())
                .paymentMethod(req.getPaymentMethod())
                .paymentType(PaymentType.valueOf(req.getPaymentType()))
                .status("PENDING")
                .transactionCode(req.getTransactionCode())
                .build();

        return toDto(paymentRepo.save(p));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> search(Long userId, String status, String type,
                                        int page, int size, String sort, boolean asc) {
        // sort: dùng tên field của entity Payment (vd: id, paymentDate, amount)
        Sort sortObj = asc ? Sort.by(sort).ascending() : Sort.by(sort).descending();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, 200), sortObj);

        List<Payment> list;
        if (userId != null && status != null && type != null) {
            list = paymentRepo.findAllByUser_UserIdAndStatusAndPaymentType(userId, status, type, pageable);
        } else if (userId != null) {
            list = paymentRepo.findAllByUser_UserId(userId, pageable);
        } else if (status != null) {
            list = paymentRepo.findAllByStatus(status, pageable);
        } else if (type != null) {
            list = paymentRepo.findAllByPaymentType(type, pageable);
        } else {
            list = paymentRepo.findAllBy(pageable);
        }
        return list.stream().map(this::toDto).toList();
    }

    @Transactional
    public PaymentResponse update(Long id, UpdatePaymentRequest req) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if (req.getUserId() != null) {
            User user = userRepo.findById(req.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + req.getUserId()));
            p.setUser(user);
        }
        if (req.getAmount() != null) p.setAmount(req.getAmount());
        if (req.getPaymentMethod() != null) p.setPaymentMethod(req.getPaymentMethod());
        if (req.getPaymentType() != null) p.setPaymentType(PaymentType.valueOf(req.getPaymentType()));
        if (req.getTransactionCode() != null) p.setTransactionCode(req.getTransactionCode());
        if (req.getProviderResponse() != null) p.setProviderResponse(req.getProviderResponse());
        if (req.getStatus() != null) {
            p.setStatus(req.getStatus());
            if ("COMPLETED".equals(req.getStatus()) && p.getPaymentDate() == null) {
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
    public PaymentResponse markPaid(Long id, String transactionCode, String providerResponseJson) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if ("COMPLETED".equals(p.getStatus())) {
            return toDto(p);
        }

        // Chỉ cho phép từ Pending -> Completed
        if (!"PENDING".equals(p.getStatus())) {
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
        p.setStatus("COMPLETED");
        if (p.getPaymentDate() == null) {
            p.setPaymentDate(LocalDateTime.now());
        }
        if (providerResponseJson != null) {
            p.setProviderResponse(providerResponseJson);
        }

        return toDto(paymentRepo.save(p));
    }

    @Transactional
    public PaymentResponse markFailed(Long id, String providerResponseJson) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if ("FAILED".equals(p.getStatus())) {
            return toDto(p);
        }

        // Chỉ cho phép từ Pending -> Failed
        if (!"PENDING".equals(p.getStatus())) {
            throw new IllegalStateException("Invalid transition: " + p.getStatus() + " -> Failed");
        }

        p.setStatus("FAILED");
        if (providerResponseJson != null) {
            p.setProviderResponse(providerResponseJson);
        }

        return toDto(paymentRepo.save(p));
    }

    @Transactional
    public PaymentResponse markRefunded(Long id, String providerResponseJson) {
        Payment p = paymentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));

        if (!"COMPLETED".equals(p.getStatus())) {
            throw new IllegalStateException("Invalid transition: " + p.getStatus() + " -> Refunded");
        }
        p.setStatus("REFUNDED");
        if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);
        return toDto(paymentRepo.save(p));
    }

    // Các chuyển trạng thái hợp lệ
    private static final Set<String> ALLOWED = Set.of(
            key(PaymentStatus.Pending, PaymentStatus.Completed),
            key(PaymentStatus.Pending, PaymentStatus.Failed),
            key(PaymentStatus.Completed, PaymentStatus.Refunded)
    );

    private static String key(PaymentStatus from, PaymentStatus to) {
        return from.name() + "->" + to.name();
    }

    @Transactional
    public PaymentResponse updateStatus(Long paymentId, PaymentStatus target,
                                        String transactionCode, String providerResponseJson) {

        Payment p = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        PaymentStatus current = PaymentStatus.valueOf(p.getStatus());
        if (current == null) current = PaymentStatus.Pending; // fallback

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
            case Failed -> {
                // Pending -> Failed
                p.setStatus(String.valueOf(PaymentStatus.Failed));
                if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);
                paymentRepo.save(p);
            }
            case Completed -> {
                // Pending -> Completed
                if (transactionCode == null || transactionCode.isBlank()) {
                    throw new IllegalArgumentException("transactionCode is required when marking Completed");
                }
                p.setStatus(String.valueOf(PaymentStatus.Completed));
                p.setTransactionCode(transactionCode);
                if (p.getPaymentDate() == null) p.setPaymentDate(LocalDateTime.now());
                if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);

                paymentRepo.saveAndFlush(p); // flush trước khi cộng quỹ
                // Cộng quỹ đúng 1 lần khi chốt Completed
                fundService.increaseBalance(p.getFund().getFundId(), p.getAmount());
            }
            case Refunded -> {
                // Completed -> Refunded
                p.setStatus(String.valueOf(PaymentStatus.Refunded));
                if (providerResponseJson != null) p.setProviderResponse(providerResponseJson);

                paymentRepo.saveAndFlush(p);
                // Hoàn quỹ đúng 1 lần khi chuyển Refunded
                fundService.decreaseBalance(p.getFund().getFundId(), p.getAmount());
            }
            default -> throw new IllegalStateException("Unsupported target: " + target);
        }

        return toDto(paymentRepo.save(p));
    }

}

