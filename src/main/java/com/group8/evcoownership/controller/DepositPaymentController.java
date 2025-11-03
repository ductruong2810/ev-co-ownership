package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DepositPaymentRequestDTO;
import com.group8.evcoownership.dto.DepositPaymentResponseDTO;
import com.group8.evcoownership.service.DepositPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
@Tag(name = "Deposit Payments", description = "Quản lý thanh toán tiền cọc")
public class DepositPaymentController {

    private static final Logger log = LoggerFactory.getLogger(DepositPaymentController.class);
    private final DepositPaymentService depositPaymentService;
    // Inject frontend URL từ file application.properties
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;

    /**
     * Tạo payment cho tiền cọc với VNPay
     */
    @PostMapping("/create")
    @Operation(summary = "Tạo thanh toán tiền cọc", description = "Tạo giao dịch thanh toán tiền cọc với tích hợp VNPay")
    public ResponseEntity<DepositPaymentResponseDTO> createDepositPayment(
            @Valid @RequestBody DepositPaymentRequestDTO request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        DepositPaymentResponseDTO response =
                depositPaymentService.createDepositPayment(request, httpRequest, authentication);
        return ResponseEntity.ok(response);
    }

    /**
     * Xác nhận callback từ VNPay → cập nhật trạng thái thanh toán
     */
    @PostMapping("/confirm")
    @Operation(summary = "Xác nhận thanh toán", description = "Xác nhận giao dịch thanh toán VNPay thành công")
    public ResponseEntity<DepositPaymentResponseDTO> confirmDepositPayment(
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_TxnRef") String transactionNo) {

        DepositPaymentResponseDTO response = depositPaymentService.confirmDepositPayment(txnRef, transactionNo);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin thanh toán dựa trên mã giao dịch (txnRef)
     * Dùng cho Frontend hiển thị chi tiết sau khi redirect từ VNPay
     */
    @GetMapping("/info-by-txn")
    @Operation(summary = "Thông tin thanh toán theo mã giao dịch", description = "Trả về thông tin chi tiết của giao dịch dựa trên mã tham chiếu (txnRef)")
    public ResponseEntity<DepositPaymentResponseDTO> getDepositInfoByTxn(@RequestParam String txnRef) {
        DepositPaymentResponseDTO response = depositPaymentService.getDepositInfoByTxn(txnRef);
        return ResponseEntity.ok(response);
    }


    /**
     * Lấy thông tin deposit của user trong group
     */
    @GetMapping("/info/{userId}/{groupId}")
    @Operation(summary = "Thông tin tiền cọc", description = "Lấy thông tin tiền cọc của người dùng trong một nhóm")
    public ResponseEntity<Map<String, Object>> getDepositInfo(
            @PathVariable Long userId,
            @PathVariable Long groupId) {

        Map<String, Object> info = depositPaymentService.getDepositInfo(userId, groupId);
        return ResponseEntity.ok(info);
    }

    /**
     * Lấy danh sách deposit status của tất cả members trong group
     */
    @GetMapping("/group/{groupId}/status")
    @Operation(summary = "Trạng thái tiền cọc nhóm", description = "Lấy trạng thái tiền cọc của tất cả thành viên trong nhóm")
    public ResponseEntity<List<Map<String, Object>>> getGroupDepositStatus(
            @PathVariable Long groupId) {

        List<Map<String, Object>> status = depositPaymentService.getGroupDepositStatus(groupId);
        return ResponseEntity.ok(status);
    }

    /**
     * Xử lý VNPay callback cho deposit payment
     */
    @GetMapping("/deposit-callback")
    public void handleDepositCallback(
            HttpServletResponse response,
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_ResponseCode") String responseCode,
            @RequestParam("vnp_TransactionNo") String transactionNo,
            @RequestParam(value = "groupId", required = false) Long groupId   //có thể null nếu FE chưa truyền
    ) throws IOException {

        try {
            if ("00".equals(responseCode)) {
                // Thanh toán thành công → cập nhật DB
                depositPaymentService.confirmDepositPayment(txnRef, transactionNo);

                // Redirect về FE hiển thị kết quả thành công
                if (groupId != null) {
                    response.sendRedirect(String.format(
                            "%s/dashboard/viewGroups/%d/payment-result?status=success&txnRef=%s",
                            frontendBaseUrl, groupId, txnRef
                    ));
                } else {
                    // fallback nếu thiếu groupId
                    response.sendRedirect(String.format(
                            "%s/payment-result?status=success&txnRef=%s",
                            frontendBaseUrl, txnRef
                    ));
                }

            } else {
                // Thanh toán thất bại
                if (groupId != null) {
                    response.sendRedirect(String.format(
                            "%s/dashboard/viewGroups/%d/payment-result?status=fail&txnRef=%s",
                            frontendBaseUrl, groupId, txnRef
                    ));
                } else {
                    response.sendRedirect(String.format(
                            "%s/payment-result?status=fail&txnRef=%s",
                            frontendBaseUrl, txnRef
                    ));
                }
            }

        } catch (Exception e) {
            log.error("Error processing deposit callback for txnRef: {}, groupId: {}", txnRef, groupId, e);
            //  Có lỗi trong quá trình xử lý callback
            if (groupId != null) {
                response.sendRedirect(String.format(
                        "%s/dashboard/viewGroups/%d/payment-result?status=error&txnRef=%s",
                        frontendBaseUrl, groupId, txnRef
                ));
            } else {
                response.sendRedirect(String.format(
                        "%s/payment-result?status=error&txnRef=%s",
                        frontendBaseUrl, txnRef
                ));
            }
        }
    }


}
