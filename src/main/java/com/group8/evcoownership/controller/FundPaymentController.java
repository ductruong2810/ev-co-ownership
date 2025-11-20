package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FundTopupRequestDTO;
import com.group8.evcoownership.dto.FundTopupResponseDTO;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.service.FundPaymentService;
import com.group8.evcoownership.service.MaintenancePaymentService;
import com.group8.evcoownership.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/funds/payments")
@RequiredArgsConstructor
@Tag(name = "Fund Payments", description = "Nạp tiền vào quỹ (Operating) qua VNPay")
public class FundPaymentController {

    private final FundPaymentService fundPaymentService;
    private final PaymentRepository paymentRepository;
    private final MaintenancePaymentService maintenancePaymentService;

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    // =============== 1) CREATE ===============
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    @Operation(
            summary = "Tạo yêu cầu nạp quỹ (VNPay)",
            description = """
                    Tạo giao dịch nạp quỹ OPERATING thông qua VNPay.
                    - Yêu cầu đăng nhập (CO_OWNER/STAFF/ADMIN).
                    - Trả về `vnpayUrl` để FE redirect sang VNPay.
                    """
    )
    public ResponseEntity<FundTopupResponseDTO> createFundTopup(
            @Valid @RequestBody FundTopupRequestDTO request,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                fundPaymentService.createFundTopup(request, httpRequest, authentication)
        );
    }

    // =============== 2) CONFIRM (server-to-server) ===============
    @PostMapping("/confirm")
    @Operation(
            summary = "[INTERNAL] Xác nhận thanh toán từ VNPay",
            description = """
                    Endpoint nội bộ VNPay gọi lại (server-to-server) sau khi người dùng thanh toán.
                    - Kiểm tra vnp_ResponseCode ở phía Service (khuyên dùng verify chữ ký).
                    - Cập nhật Payment sang COMPLETED và cộng vào SharedFund.balance.
                    - Không dành cho FE gọi trực tiếp.
                    """
    )
    public ResponseEntity<FundTopupResponseDTO> confirmFundTopup(
            @ParameterObject HttpServletRequest request,
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_TxnRef") String transactionNo
    ) {
        // Nếu bạn verify chữ ký ở Controller, truyền request.getParameterMap() vào Service.
        return ResponseEntity.ok(fundPaymentService.confirmFundTopup(txnRef, transactionNo));
    }

    // =============== 3) INFO BY TXN ===============
    @GetMapping("/info-by-txn")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
    @Operation(
            summary = "Lấy thông tin nạp quỹ theo mã giao dịch",
            description = """
                    FE gọi để hiển thị kết quả thanh toán sau khi redirect từ VNPay.
                    - Truyền `txnRef` (vnp_TxnRef) để lấy Payment tương ứng.
                    """
    )
    public ResponseEntity<FundTopupResponseDTO> getFundTopupInfoByTxn(
            @RequestParam String txnRef
    ) {
        return ResponseEntity.ok(fundPaymentService.getFundTopupInfoByTxn(txnRef));
    }

    // =============== 4) PUBLIC REDIRECT CALLBACK ===============
    @GetMapping("/callback")
    @Operation(
            summary = "Callback trả về từ VNPay (public)",
            description = """
            VNPay redirect người dùng về đây sau khi thanh toán.
            - Dựa vào txnRef để tìm Payment và phân biệt loại:
              + CONTRIBUTION  -> nạp quỹ (fund topup)
              + MAINTENANCE_FEE -> thanh toán maintenance PERSONAL
            - Nếu vnp_ResponseCode = '00': xác nhận & cập nhật tương ứng.
            - Ngược lại: fail.
            """
    )
    public void handleFundCallback(
            HttpServletResponse response,
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_ResponseCode") String responseCode,
            @RequestParam(value = "vnp_TransactionNo", required = false) String transactionNo,
            @RequestParam(value = "groupId", required = false) Long groupIdParam
    ) throws IOException {

        // 1) Luôn lấy Payment theo txnRef
        Payment payment = paymentRepository.findByTransactionCode(txnRef)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + txnRef));

        // 2) Xác định type cho FE theo PaymentType
        String typeForFrontend;
        if (payment.getPaymentType() == PaymentType.CONTRIBUTION) {
            typeForFrontend = "fund";
        } else if (payment.getPaymentType() == PaymentType.MAINTENANCE_FEE) {
            typeForFrontend = "maintenance";
        } else {
            throw new IllegalStateException("Unsupported payment type for callback: " + payment.getPaymentType());
        }

        // 3) Xác định groupId ưu tiên:
        //    - Nếu VNPay trả về param groupId thì dùng nó
        //    - Nếu không, tự suy ra qua maintenance → vehicle → ownershipGroup
        Long groupIdForRedirect = (groupIdParam != null)
                ? groupIdParam
                : resolveGroupIdFromPayment(payment);

        try {
            if ("00".equals(responseCode)) {
                // ====== Thành công ======
                if (payment.getPaymentType() == PaymentType.CONTRIBUTION) {
                    fundPaymentService.confirmFundTopup(txnRef, transactionNo);
                } else if (payment.getPaymentType() == PaymentType.MAINTENANCE_FEE) {
                    maintenancePaymentService.confirmMaintenancePayment(txnRef, transactionNo);
                }

                redirect(response, groupIdForRedirect, typeForFrontend, "success", txnRef);
            } else {
                // ====== Thất bại VNPay (responseCode != 00) ======
                // VẪN redirect với type đúng (maintenance) và groupId lấy qua maintenance → vehicle
                redirect(response, groupIdForRedirect, typeForFrontend, "fail", txnRef);
            }
        } catch (Exception ex) {
            // Có lỗi BE, vẫn cố trả đúng type + groupId nếu đã lấy được
            redirect(response, groupIdForRedirect, typeForFrontend, "fail", txnRef);
        }
    }

    // ===== Helper: Lấy groupId KHÔNG cần qua fund cho maintenance =====
    private Long resolveGroupIdFromPayment(Payment p) {
        // ƯU TIÊN: nếu là payment gắn với Maintenance → Vehicle → OwnershipGroup
        if (p.getMaintenance() != null
                && p.getMaintenance().getVehicle() != null
                && p.getMaintenance().getVehicle().getOwnershipGroup() != null) {
            return p.getMaintenance()
                    .getVehicle()
                    .getOwnershipGroup()
                    .getGroupId();
        }

        // Fallback: nếu là contribution thì lấy qua fund (nếu có)
        if (p.getFund() != null && p.getFund().getGroup() != null) {
            return p.getFund().getGroup().getGroupId();
        }

        return null; // trường hợp hiếm, FE tự xử lý khi groupId = null
    }

    // ===== Helper redirect =====
    private void redirect(HttpServletResponse res, Long groupId, String type, String status, String txnRef) throws IOException {
        if (groupId != null) {
            res.sendRedirect(String.format(
                    "%s/dashboard/viewGroups/%d/payment-result?type=%s&status=%s&txnRef=%s",
                    frontendBaseUrl, groupId, type, status, txnRef));
        } else {
            res.sendRedirect(String.format(
                    "%s/payment-result?type=%s&status=%s&txnRef=%s",
                    frontendBaseUrl, type, status, txnRef));
        }
    }

}

