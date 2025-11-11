package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FundTopupRequestDTO;
import com.group8.evcoownership.dto.FundTopupResponseDTO;
import com.group8.evcoownership.service.FundPaymentService;
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
@PreAuthorize("isAuthenticated()")
public class FundPaymentController {

    private final FundPaymentService fundPaymentService;

    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    // =============== 1) CREATE ===============
    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
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
    @PreAuthorize("permitAll()")
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
            @RequestParam("vnp_TransactionNo") String transactionNo
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
    @PreAuthorize("permitAll()")
    @Operation(
            summary = "Callback trả về từ VNPay (public)",
            description = """
            VNPay redirect người dùng về đây sau khi thanh toán.
            - Nếu vnp_ResponseCode = '00': xác nhận & cộng quỹ.
            - Ngược lại: fail.
            """
    )
    public void handleFundCallback(
            HttpServletResponse response,
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_ResponseCode") String responseCode,
            @RequestParam("vnp_TransactionNo") String transactionNo,
            @RequestParam(value = "groupId", required = false) Long groupId
    ) throws IOException {

        try {
            if ("00".equals(responseCode)) {
                fundPaymentService.confirmFundTopup(txnRef, transactionNo);
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
        if (groupId != null) {
            res.sendRedirect(String.format(
                    "%s/dashboard/viewGroups/%d/payment-result?type=fund&status=%s&txnRef=%s",
                    frontendBaseUrl, groupId, status, txnRef));
        } else {
            res.sendRedirect(String.format(
                    "%s/payment-result?type=fund&status=%s&txnRef=%s",
                    frontendBaseUrl, status, txnRef));
        }
    }
}

