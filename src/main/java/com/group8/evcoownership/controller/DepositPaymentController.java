package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.service.DepositPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
@Tag(name = "Deposit Payments", description = "Quản lý thanh toán tiền cọc")
public class DepositPaymentController {

    private final DepositPaymentService depositPaymentService;
    // Inject frontend URL từ file application.properties
    @Value("${frontend.base.url}")
    private String frontendBaseUrl;
    /**
     * Tạo payment cho tiền cọc với VNPay
     */
    @PostMapping("/create")
    @Operation(summary = "Tạo thanh toán tiền cọc", description = "Tạo giao dịch thanh toán tiền cọc với tích hợp VNPay")
    public ResponseEntity<DepositPaymentResponse> createDepositPayment(
            @Valid @RequestBody DepositPaymentRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        DepositPaymentResponse response =
                depositPaymentService.createDepositPayment(request, httpRequest, authentication);
        return ResponseEntity.ok(response);
    }

    /**
     * Xác nhận callback từ VNPay → cập nhật trạng thái thanh toán
     */
    @PostMapping("/confirm")
    @Operation(summary = "Xác nhận thanh toán", description = "Xác nhận giao dịch thanh toán VNPay thành công")
    public ResponseEntity<DepositPaymentResponse> confirmDepositPayment(
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_TxnRef") String transactionNo) {

        DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(txnRef, transactionNo);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin thanh toán dựa trên mã giao dịch (txnRef)
     * Dùng cho Frontend hiển thị chi tiết sau khi redirect từ VNPay
     */
    @GetMapping("/info-by-txn")
    @Operation(summary = "Thông tin thanh toán theo mã giao dịch", description = "Trả về thông tin chi tiết của giao dịch dựa trên mã tham chiếu (txnRef)")
    public ResponseEntity<DepositPaymentResponse> getDepositInfoByTxn(@RequestParam String txnRef) {
        DepositPaymentResponse response = depositPaymentService.getDepositInfoByTxn(txnRef);
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
     * ✅ Xử lý VNPay callback cho deposit payment
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
            e.printStackTrace(); // Log lỗi để debug nếu có
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
