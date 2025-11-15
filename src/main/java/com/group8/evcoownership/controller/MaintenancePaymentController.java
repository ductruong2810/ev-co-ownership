package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FundTopupResponseDTO;
import com.group8.evcoownership.service.MaintenancePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/maintenances/payments")
@RequiredArgsConstructor
@Tag(
        name = "Maintenance Payments",
        description = "Thanh toán PERSONAL cho chi phí bảo trì (maintenance) qua VNPay"
)
public class MaintenancePaymentController {

    private final MaintenancePaymentService maintenancePaymentService;

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    // =============== 1) CREATE ===============
    @PostMapping("/create/{maintenanceId}")
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    @Operation(
            summary = "Tạo thanh toán PERSONAL cho maintenance (VNPay)",
            description = """
                    Co-owner (người liable) tạo giao dịch thanh toán chi phí bảo trì qua VNPay.
                    - maintenanceId: ID của yêu cầu bảo trì mà co-owner phải trả tiền.
                    - Backend sẽ:
                      + Kiểm tra maintenance tồn tại và có liableUser.
                      + Xác thực user login chính là liableUser.
                      + Tạo Payment category = PERSONAL, không gắn SharedFund.
                      + Trả về `vnpayUrl` để FE redirect sang VNPay.
                    """
    )
    public ResponseEntity<FundTopupResponseDTO> createMaintenancePayment(
            @PathVariable @NotNull Long maintenanceId,
            HttpServletRequest httpRequest,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                maintenancePaymentService.createMaintenancePayment(
                        maintenanceId,
                        httpRequest,
                        authentication
                )
        );
    }

    // =============== 2) CONFIRM (server-to-server) ===============
    @PostMapping("/confirm")
    @Operation(
            summary = "[INTERNAL] Xác nhận thanh toán maintenance từ VNPay",
            description = """
                    Endpoint nội bộ để VNPay (hoặc backend khác) gọi sau khi người dùng thanh toán
                    chi phí maintenance.
                    - Không dành cho FE gọi trực tiếp.
                    - Không cộng vào SharedFund, chỉ update Payment + Maintenance (→ FUNDED).
                    """
    )
    public ResponseEntity<FundTopupResponseDTO> confirmMaintenancePayment(
            @ParameterObject HttpServletRequest request,
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_TransactionNo") String transactionNo
    ) {
        return ResponseEntity.ok(
                maintenancePaymentService.confirmMaintenancePayment(txnRef, transactionNo)
        );
    }

    // =============== 3) INFO BY TXN ===============
    @GetMapping("/info-by-txn")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
    @Operation(
            summary = "Lấy thông tin thanh toán maintenance theo mã giao dịch",
            description = """
                    FE gọi để hiển thị kết quả thanh toán PERSONAL cho maintenance
                    sau khi redirect từ VNPay.
                    - Truyền `txnRef` (vnp_TxnRef) để lấy Payment tương ứng.
                    """
    )
    public ResponseEntity<FundTopupResponseDTO> getMaintenancePaymentInfoByTxn(
            @RequestParam String txnRef
    ) {
        return ResponseEntity.ok(
                maintenancePaymentService.getMaintenancePaymentInfoByTxn(txnRef)
        );
    }

    // =============== 4) PUBLIC REDIRECT CALLBACK ===============
    @GetMapping("/callback")
    @Operation(
            summary = "Callback trả về từ VNPay cho thanh toán maintenance (public)",
            description = """
                    VNPay redirect người dùng về đây sau khi thanh toán chi phí maintenance.
                    - Nếu vnp_ResponseCode = '00': xác nhận thanh toán → COMPLETED, update maintenance sang FUNDED.
                    - Ngược lại: fail.
                    - Không cộng SharedFund, vì thanh toán thuộc loại PERSONAL.
                    """
    )
    public void handleMaintenanceCallback(
            HttpServletResponse response,
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_ResponseCode") String responseCode,
            @RequestParam("vnp_TransactionNo") String transactionNo,
            @RequestParam(value = "groupId", required = false) Long groupId
    ) throws IOException {

        try {
            if ("00".equals(responseCode)) {
                maintenancePaymentService.confirmMaintenancePayment(txnRef, transactionNo);
                redirect(response, groupId, "success", txnRef);
            } else {
                redirect(response, groupId, "fail", txnRef);
            }
        } catch (Exception ex) {
            redirect(response, groupId, "error", txnRef);
        }
    }

    // ===== Helper redirect =====
    private void redirect(HttpServletResponse res, Long groupId, String status, String txnRef) throws IOException {
        // type=maintenance để FE phân biệt với fund/deposit
        String type = "maintenance";

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
