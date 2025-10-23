package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.exception.DepositPaymentException;
import com.group8.evcoownership.exception.PaymentConflictException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepositPaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final OwnershipShareRepository shareRepository;
    private final ContractRepository contractRepository;
    private final SharedFundRepository fundRepository;
    private final UserRepository userRepository;
    private final OwnershipGroupRepository groupRepository;
    private final VnPay_PaymentService vnPayPaymentService;
    private final DepositCalculationService depositCalculationService;
    private final VehicleRepository vehicleRepository;

    /**
     * Tạo payment cho tiền cọc với VNPay
     */
    @Transactional
    public DepositPaymentResponse createDepositPayment(DepositPaymentRequest request, HttpServletRequest httpRequest) {
        // Kiểm tra user tồn tại
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.userId()));

        // Kiểm tra group tồn tại
        OwnershipGroup group = groupRepository.findById(request.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + request.groupId()));

        // Kiểm tra user có trong group không
        OwnershipShare share = shareRepository.findById(new OwnershipShareId(request.userId(), request.groupId()))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        // Kiểm tra contract tồn tại
        Contract contract = contractRepository.findByGroupGroupId(request.groupId())
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for this group"));

        // Kiểm tra contract đã được ký chưa
        if (contract.getTerms() == null || !contract.getTerms().contains("[ĐÃ KÝ]")) {
            throw new DepositPaymentException("Contract must be signed before making deposit payment");
        }

        // Kiểm tra user đã đóng tiền cọc chưa
        if (share.getDepositStatus() == DepositStatus.PAID) {
            throw new PaymentConflictException("User has already paid the deposit");
        }

        // Tính toán số tiền cọc dựa trên tỷ lệ sở hữu của user
        BigDecimal requiredAmount = calculateDepositAmountForUser(user, group, share);
        if (request.amount().compareTo(requiredAmount) != 0) {
            throw new DepositPaymentException("Deposit amount must be exactly " + requiredAmount + " VND (based on ownership percentage: " + share.getOwnershipPercentage() + "%)");
        }

        // Lấy hoặc tạo SharedFund cho group
        SharedFund fund = fundRepository.findByGroup_GroupId(request.groupId())
                .orElseGet(() -> {
                    SharedFund newFund = SharedFund.builder()
                            .group(group)
                            .balance(BigDecimal.ZERO)
                            .build();
                    return fundRepository.save(newFund);
                });

        // Tạo payment record
        Payment payment = Payment.builder()
                .payer(user)
                .fund(fund)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentType(PaymentType.DEPOSIT)
                .status(PaymentStatus.PENDING)
                .paymentCategory("GROUP")
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Tạo VNPay URL cho deposit payment
        String vnpayUrl = vnPayPaymentService.createDepositPaymentUrl(request.amount().longValue(), httpRequest);

        return DepositPaymentResponse.builder()
                .paymentId(savedPayment.getId())
                .userId(user.getUserId())
                .groupId(group.getGroupId())
                .amount(request.amount())
                .requiredAmount(requiredAmount)
                .paymentMethod(request.paymentMethod())
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .vnpayUrl(vnpayUrl)
                .message("Deposit payment created successfully. Please complete the payment via VNPay.")
                .build();
    }

    /**
     * Xác nhận payment thành công (callback từ payment gateway)
     */
    @Transactional
    public DepositPaymentResponse confirmDepositPayment(Long paymentId, String transactionCode) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentConflictException("Payment is not in PENDING status");
        }

        // Cập nhật payment status
        paymentService.updateStatus(paymentId, PaymentStatus.COMPLETED, transactionCode, null);

        // Cập nhật deposit status của user
        OwnershipShareId shareId = new OwnershipShareId(payment.getPayer().getUserId(),
                payment.getFund().getGroup().getGroupId());
        OwnershipShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new EntityNotFoundException("Ownership share not found"));

        share.setDepositStatus(DepositStatus.PAID);
        share.setUpdatedAt(LocalDateTime.now());
        shareRepository.save(share);

        return DepositPaymentResponse.builder()
                .paymentId(paymentId)
                .userId(payment.getPayer().getUserId())
                .groupId(payment.getFund().getGroup().getGroupId())
                .amount(payment.getAmount())
                .requiredAmount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .status(PaymentStatus.COMPLETED)
                .transactionCode(transactionCode)
                .paidAt(LocalDateTime.now())
                .message("Deposit payment completed successfully")
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
        BigDecimal requiredAmount = calculateDepositAmountForUser(share.getUser(), share.getGroup(), share);

        Map<String, Object> info = new HashMap<>();
        info.put("userId", userId);
        info.put("groupId", groupId);
        info.put("depositStatus", share.getDepositStatus());
        info.put("requiredAmount", requiredAmount);
        info.put("ownershipPercentage", share.getOwnershipPercentage());
        info.put("contractSigned", contract.getTerms() != null && contract.getTerms().contains("[ĐÃ KÝ]"));
        info.put("canPay", contract.getTerms() != null && contract.getTerms().contains("[ĐÃ KÝ]")
                && share.getDepositStatus() == DepositStatus.PENDING);

        return info;
    }

    /**
     * Lấy danh sách deposit status của tất cả members trong group
     */
    public List<Map<String, Object>> getGroupDepositStatus(Long groupId) {
        List<OwnershipShare> shares = shareRepository.findByGroupGroupId(groupId);
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        return shares.stream().map(share -> {
            Map<String, Object> status = new HashMap<>();
            status.put("userId", share.getUser().getUserId());
            status.put("userName", share.getUser().getFullName());
            status.put("userEmail", share.getUser().getEmail());
            status.put("depositStatus", share.getDepositStatus());
            status.put("ownershipPercentage", share.getOwnershipPercentage());
            status.put("joinDate", share.getJoinDate());

            // Tính toán số tiền cọc cho user này
            BigDecimal depositAmount = calculateDepositAmountForUser(share.getUser(), group, share);
            status.put("requiredDepositAmount", depositAmount);

            return status;
        }).toList();
    }

    /**
     * Tính toán số tiền cọc cho user dựa trên tỷ lệ sở hữu
     */
    private BigDecimal calculateDepositAmountForUser(User user, OwnershipGroup group, OwnershipShare share) {
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
}
