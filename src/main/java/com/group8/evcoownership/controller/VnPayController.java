package com.group8.evcoownership.controller;


import com.group8.evcoownership.service.VnPay_PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth/vnpay")
@RequiredArgsConstructor
@Tag(name = "VNPay", description = "Tích hợp thanh toán VNPay")
public class VnPayController {
    private final VnPay_PaymentService VnPay_PaymentService;

    @PostMapping("/create-vnPaypayment/{fee}")
    @Operation(summary = "Tạo URL thanh toán VNPay", description = "Tạo URL thanh toán VNPay cho một khoản phí cụ thể")
    public ResponseEntity<Map<String, String>> createPaymentUrl(@PathVariable long fee, HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();

        // Tạo mã giao dịch nội bộ (txnRef)
        String txnRef = String.valueOf(System.currentTimeMillis());

        // Gọi đúng method có 3 tham số
        String url = VnPay_PaymentService.createPaymentUrl(fee, request, txnRef, null);

        map.put("url", url);
        return ResponseEntity.ok(map);
    }


    @GetMapping("/vn-pay-callback")
    @Operation(summary = "Xử lý callback VNPay", description = "Xử lý callback từ VNPay sau khi thanh toán")
    public void payCallbackHandler(HttpServletRequest request, HttpServletResponse response) throws Exception {
        VnPay_PaymentService.handlePaymentCallBack(request, response);
    }

}