package com.group8.evcoownership.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VnPay_PaymentService {
    @Value("${payment.vnPay.url}")
    private String vnp_PayUrl;
    @Value("${payment.vnPay.returnUrl}")
    private String vnp_ReturnUrl;
    @Value("${payment.vnPay.depositReturnUrl}")
    private String vnp_DepositReturnUrl;
    @Value("${payment.vnPay.tmnCode}")
    private String vnp_TmnCode;
    @Value("${payment.vnPay.secretKey}")
    private String secretKey;
    @Value("${payment.vnPay.version}")
    private String vnp_Version;
    @Value("${payment.vnPay.command}")
    private String vnp_Command;
    @Value("${payment.vnPay.orderType}")
    private String orderType;
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    // thanh toan như không phải đóng tièn cọc, e.g: incident
    public String createPaymentUrl(long fee, HttpServletRequest request, String txnRef) {
        return createPaymentUrl(fee, request, txnRef, false);
    }

    /**
     * Tạo payment URL cho deposit payment
     */
    public String createDepositPaymentUrl(long fee, HttpServletRequest request, String txnRef) {
        return createPaymentUrl(fee, request, txnRef, true);
    }


    public String createPaymentUrl(long fee, HttpServletRequest request, String txnRef, boolean isDeposit)
    {
        long amount = fee * 100L;
        Map<String, String> vnpParamsMap = getVNPayConfig();

        // Sử dụng callback URL khác nhau cho deposit và payment thông thường
        String returnUrl = isDeposit ? this.vnp_DepositReturnUrl : this.vnp_ReturnUrl;
        vnpParamsMap.put("vnp_ReturnUrl", returnUrl);
        vnpParamsMap.put("vnp_Amount", String.valueOf(amount));
        vnpParamsMap.put("vnp_IpAddr", getIpAddress(request));

        // Quan trọng: thêm mã giao dịch nội bộ
        vnpParamsMap.put("vnp_TxnRef", txnRef);



        //build query url
        String queryUrl = getPaymentURL(vnpParamsMap, true);
        String hashData = getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = hmacSHA512(secretKey, hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        return vnp_PayUrl + "?" + queryUrl;
    }

    public void handlePaymentCallBack(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String status = request.getParameter("vnp_ResponseCode");
        String redirectUrl = String.format(
                "%s/payment-status?vnp_ResponseCode=%s",
                frontendUrl,
                status
        );
        response.sendRedirect(redirectUrl);
    }


    private Map<String, String> getVNPayConfig() {
        Map<String, String> vnpParamsMap = new HashMap<>();
        vnpParamsMap.put("vnp_Version", this.vnp_Version);
        vnpParamsMap.put("vnp_Command", this.vnp_Command);
        vnpParamsMap.put("vnp_TmnCode", this.vnp_TmnCode);
        vnpParamsMap.put("vnp_CurrCode", "VND");
//        vnpParamsMap.put("vnp_TxnRef", getRandomNumber(8));
        vnpParamsMap.put("vnp_OrderInfo", "Thanh toan don hang:" + getRandomNumber(8));
        vnpParamsMap.put("vnp_OrderType", this.orderType);
        vnpParamsMap.put("vnp_Locale", "vn");

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnpCreateDate = formatter.format(calendar.getTime());
        vnpParamsMap.put("vnp_CreateDate", vnpCreateDate);
        calendar.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(calendar.getTime());
        vnpParamsMap.put("vnp_ExpireDate", vnp_ExpireDate);



        return vnpParamsMap;
    }

    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();

        } catch (Exception ex) {
            return "";
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ipAdress;
        try {
            ipAdress = request.getHeader("X-FORWARDED-FOR");
            if (ipAdress == null) {
                ipAdress = request.getRemoteAddr();
            }
        } catch (Exception e) {
            ipAdress = "Invalid IP:" + e.getMessage();
        }
        return ipAdress;
    }

    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static String getPaymentURL(Map<String, String> paramsMap, boolean encodeKey) {
        return paramsMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(entry ->
                        (encodeKey ? URLEncoder.encode(entry.getKey(),
                                StandardCharsets.US_ASCII)
                                : entry.getKey()) + "=" +
                                URLEncoder.encode(entry.getValue()
                                        , StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }
}
