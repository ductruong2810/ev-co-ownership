package com.group8.evcoownership.controller;


import com.group8.evcoownership.service.VnPay_PaymentService;
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
public class VnPayController {
    private final VnPay_PaymentService VnPay_PaymentService;

    @PostMapping("/create-vnPaypayment/{fee}")
    public ResponseEntity<Map<String, String>> createPaymentUrl(@PathVariable long fee, HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        map.put("url", VnPay_PaymentService.createPaymentUrl(fee, request));
        return ResponseEntity.ok(map);
    }

    @GetMapping("/vn-pay-callback")
    public void payCallbackHandler(HttpServletRequest request, HttpServletResponse response) throws Exception {
        VnPay_PaymentService.handlePaymentCallBack(request, response);
    }

}