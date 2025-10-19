package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.DepositPaymentRequest;
import com.group8.evcoownership.dto.DepositPaymentResponse;
import com.group8.evcoownership.service.DepositPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/deposits")
@RequiredArgsConstructor
public class DepositPaymentController {

    private final DepositPaymentService depositPaymentService;

    /**
     * Tạo payment cho tiền cọc với VNPay
     */
    @PostMapping("/create")
    public ResponseEntity<DepositPaymentResponse> createDepositPayment(
            @Valid @RequestBody DepositPaymentRequest request,
            HttpServletRequest httpRequest) {

        DepositPaymentResponse response = depositPaymentService.createDepositPayment(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Xác nhận payment thành công (callback từ payment gateway)
     */
    @PostMapping("/confirm/{paymentId}")
    public ResponseEntity<DepositPaymentResponse> confirmDepositPayment(
            @PathVariable Long paymentId,
            @RequestParam String transactionCode) {

        DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(paymentId, transactionCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin deposit của user trong group
     */
    @GetMapping("/info/{userId}/{groupId}")
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
    public ResponseEntity<List<Map<String, Object>>> getGroupDepositStatus(
            @PathVariable Long groupId) {

        List<Map<String, Object>> status = depositPaymentService.getGroupDepositStatus(groupId);
        return ResponseEntity.ok(status);
    }

    /**
     * Kiểm tra và kích hoạt group nếu tất cả đã đóng tiền cọc
     */
    @PostMapping("/check-activation/{groupId}")
    public ResponseEntity<Map<String, Object>> checkAndActivateGroup(
            @PathVariable Long groupId) {

        depositPaymentService.checkAndActivateGroup(groupId);

        Map<String, Object> result = Map.of(
                "success", true,
                "message", "Group activation check completed",
                "groupId", groupId
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Xử lý VNPay callback cho deposit payment
     */
    @GetMapping("/deposit-callback")
    public ResponseEntity<Map<String, Object>> handleVnPayCallback(
            @RequestParam Map<String, String> params) {

        String responseCode = params.get("vnp_ResponseCode");
        String transactionCode = params.get("vnp_TransactionNo");
        String amount = params.get("vnp_Amount");

        Map<String, Object> result = new HashMap<>();

        if ("00".equals(responseCode)) {
            // Payment thành công
            try {
                // Tìm payment dựa trên transaction code hoặc amount
                // Trong thực tế, bạn sẽ cần lưu transaction reference khi tạo payment
                DepositPaymentResponse response = depositPaymentService.confirmDepositPayment(
                        Long.parseLong(params.get("vnp_TxnRef")), // Payment ID từ VNPay
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
