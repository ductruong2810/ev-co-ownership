package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.exception.DepositPaymentException;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DepositPaymentService {


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

    @Value("${payment.vnPay.tmnCode}")
    private String vnp_TmnCode;

    @Value("${payment.vnPay.secretKey}")
    private String vnp_HashSecret;

    @Value("${payment.vnPay.url}")
    private String vnp_PayUrl;

    @Value("${payment.vnPay.depositReturnUrl}")
    private String vnp_ReturnUrl;

    /**
     * 1️⃣ Tạo payment mới → sinh URL thanh toán VNPay
     */
    @Transactional
    public DepositPaymentResponse createDepositPayment(DepositPaymentRequest request,
                                                       HttpServletRequest servletRequest,
                                                       Authentication authentication) {

        Long userId = parseId(request.userId(), "userId");
        Long groupId = parseId(request.groupId(), "groupId");

        // 1️⃣ Xác thực user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        String authenticatedEmail = authentication.getName();
        if (!user.getEmail().equals(authenticatedEmail)) {
            throw new DepositPaymentException("You can only create deposit payment for your own account");
        }

        // 2️⃣ Kiểm tra group, membership, contract, fund
        OwnershipGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        OwnershipShare share = shareRepository.findById(new OwnershipShareId(userId, groupId))
                .orElseThrow(() -> new EntityNotFoundException("User is not a member of this group"));

        Contract contract = contractRepository.findByGroupGroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Contract not found for this group"));

        SharedFund fund = sharedFundRepository.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Fund not found for group: " + groupId));

        // 3️⃣ Tính toán số tiền cần đặt cọc
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
        // 4️⃣ Tạo record Payment trong DB
        String txnRef = String.valueOf(System.currentTimeMillis()).substring(5, 13); // 8 số ngẫu nhiên

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

        // 5️⃣ Sinh link thanh toán VNPay
        //String paymentUrl = vnPayPaymentService.createDepositPaymentUrl(requiredAmount.longValue(), servletRequest);
        String paymentUrl = vnPayPaymentService.createDepositPaymentUrl(requiredAmount.longValue(), servletRequest, txnRef);


        return DepositPaymentResponse.builder()
                .paymentId(payment != null ? payment.getId() : null)
                .userId(user != null ? user.getUserId() : null)
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
     * 2️⃣ Xác nhận callback từ VNPay → cập nhật Payment COMPLETED
     */
    public DepositPaymentResponse confirmDepositPayment(String txnRef, String transactionNo) {
        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() -> new RuntimeException("Payment not found for txnRef: " + txnRef));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return convertToResponse(payment);
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        return convertToResponse(payment);
    }



    /**
     * 3️⃣ API cho frontend kiểm tra trạng thái thanh toán
     */
    public DepositPaymentResponse getByTxnRef(String txnRef) {
        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() -> new RuntimeException("Payment not found for txnRef: " + txnRef));
        return convertToResponse(payment);
    }

    private DepositPaymentResponse convertToResponse(Payment p) {
        return DepositPaymentResponse.builder()
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
        info.put("contractSigned", contract.getTerms() != null && contract.getTerms().contains("[ĐÃ KÝ]"));
        info.put("canPay", contract.getTerms() != null && contract.getTerms().contains("[ĐÃ KÝ]")
                && share.getDepositStatus() == DepositStatus.PENDING);

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

    /**
     * Kiểm tra có thể tự động duyệt contract không
     * Business rules: 
     * 1. Số thành viên thực tế = memberCapacity của group
     * 2. Tất cả thành viên đã đóng deposit (đã được kiểm tra ở trên)
     */
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

        if (contract.isEmpty()) {
            return "NO_CONTRACT";
        }

        return contract.get().getApprovalStatus().name();
    }

    /**
     * ✅ Lấy thông tin chi tiết của thanh toán dựa theo mã giao dịch (txnRef)
     */
    public DepositPaymentResponse getDepositInfoByTxn(String txnRef) {
        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() -> new RuntimeException("Payment not found for txnRef: " + txnRef));

        return convertToResponse(payment);
    }


}
