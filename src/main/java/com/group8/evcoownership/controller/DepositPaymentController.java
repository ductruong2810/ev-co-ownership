package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.service.DepositPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
@Tag(name = "Deposit Payments", description = "Quản lý thanh toán tiền cọc")
public class DepositPaymentController {

    private final DepositPaymentService depositPaymentService;

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
     * Xác nhận payment thành công (callback từ payment gateway)
     */
    @PostMapping("/confirm/{paymentId}")
    @Operation(summary = "Xác nhận thanh toán", description = "Xác nhận giao dịch thanh toán tiền cọc thành công")
    public ResponseEntity<DepositPaymentResponse> confirmDepositPayment(
            @PathVariable Long paymentId,
            @RequestParam String transactionCode) {

        DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(String.valueOf(paymentId), transactionCode);
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
    @Operation(summary = "Callback VNPay", description = "Xử lý callback từ VNPay cho thanh toán tiền cọc")
    public ResponseEntity<Map<String, Object>> handleVnPayCallback(
            @RequestParam Map<String, String> params) {

        String responseCode = params.get("vnp_ResponseCode");
        String transactionCode = params.get("vnp_TransactionNo");

        Map<String, Object> result = new HashMap<>();

        if ("00".equals(responseCode)) {
            // Payment thành công
            try {
                // Tìm payment dựa trên transaction code hoặc amount
                // Trong thực tế, bạn sẽ cần lưu transaction reference khi tạo payment
                DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(
                        String.valueOf(Long.parseLong(params.get("vnp_TxnRef"))), // Payment ID từ VNPay
                        transactionCode
                );

                result.put("success", true);
                result.put("message", "Payment completed successfully");
                result.put("data", response);

            } catch (Exception e) {
                result.put("success", false);
                result.put("message", "Error processing payment: " + e.getMessage());
            }
        } else {
            result.put("success", false);
            result.put("message", "Payment failed with code: " + responseCode);
        }

        return ResponseEntity.ok(result);
    }
}
