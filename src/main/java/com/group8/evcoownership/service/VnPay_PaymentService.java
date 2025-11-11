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
    public String createPaymentUrl(long fee, HttpServletRequest request, String txnRef, Long groupId) {
        return createPaymentUrl(fee, request, txnRef, false, groupId);
    }

    /**
     * 1 Tạo payment URL cho tiền cọc (deposit) với groupId cụ thể
     */
    public String createDepositPaymentUrl(long fee, HttpServletRequest request, String txnRef, Long groupId) {
        return createPaymentUrl(fee, request, txnRef, true, groupId);
    }


    /**
     * 2️ Hàm chính tạo URL thanh toán cho VNPay
     * - Thêm groupId vào callback URL khi là deposit
     */
    public String createPaymentUrl(long fee, HttpServletRequest request, String txnRef, boolean isDeposit, Long groupId) {
        long amount = fee * 100L;
        Map<String, String> vnpParamsMap = getVNPayConfig();

        String returnUrl = isDeposit ? this.vnp_DepositReturnUrl : this.vnp_ReturnUrl;

        // Nếu là deposit thì thêm groupId (ưu tiên từ param truyền vào)
        if (isDeposit) {
            String effectiveGroupId = (groupId != null) ? String.valueOf(groupId) : request.getParameter("groupId");
            if (effectiveGroupId == null || effectiveGroupId.isEmpty()) {
                System.err.println("[VNPay] groupId missing — fallback to 0");
                effectiveGroupId = "0";
            }
            returnUrl = returnUrl + "?groupId=" + effectiveGroupId;
        }

        vnpParamsMap.put("vnp_ReturnUrl", returnUrl);
        vnpParamsMap.put("vnp_Amount", String.valueOf(amount));
        vnpParamsMap.put("vnp_IpAddr", getIpAddress(request));
        vnpParamsMap.put("vnp_TxnRef", txnRef);

        String queryUrl = getPaymentURL(vnpParamsMap, true);
        String hashData = getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = hmacSHA512(secretKey, hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        return vnp_PayUrl + "?" + queryUrl;
    }


    /**
     * 3 Callback hiển thị kết quả thanh toán (nếu bạn dùng tạm thời)
     * Ở dự án EV Co-ownership, thực tế bạn dùng DepositController.depositCallback()
     * để xử lý cập nhật Payment/OwnershipShare → nên hàm này chỉ cần redirect đơn giản
     */
    public void handlePaymentCallBack(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String status = request.getParameter("vnp_ResponseCode");
        String txnRef = request.getParameter("vnp_TxnRef");
        String groupId = request.getParameter("groupId");

        // Dựa theo groupId redirect về đúng trang FE trong dashboard
        String redirectUrl = String.format(
                "%s/dashboard/viewGroups/%s/payment-result?status=%s&txnRef=%s",
                frontendUrl,
                groupId != null ? groupId : "unknown",
                "00".equals(status) ? "success" : "fail",
                txnRef != null ? txnRef : ""
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

//        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
//        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
//        String vnpCreateDate = formatter.format(calendar.getTime());
//        vnpParamsMap.put("vnp_CreateDate", vnpCreateDate);
//        calendar.add(Calendar.MINUTE, 15);
//        String vnp_ExpireDate = formatter.format(calendar.getTime());
//        vnpParamsMap.put("vnp_ExpireDate", vnp_ExpireDate);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")); //thêm dòng này

        String vnp_CreateDate = formatter.format(calendar.getTime());
        vnpParamsMap.put("vnp_CreateDate", vnp_CreateDate);

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

//    public static String getIpAddress(HttpServletRequest request) {
//        String ipAdress;
//        try {
//            ipAdress = request.getHeader("X-FORWARDED-FOR");
//            if (ipAdress == null) {
//                ipAdress = request.getRemoteAddr();
//            }
//        } catch (Exception e) {
//            ipAdress = "Invalid IP:" + e.getMessage();
//        }
//        return ipAdress;
//    }

    private String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        // FIX: chuyển IPv6 localhost (::1) về IPv4 127.0.0.1
        if ("0:0:0:0:0:0:0:1".equals(ipAddress)) {
            ipAddress = "127.0.0.1";
        }

        return ipAddress;
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

    /**
     * Tạo yêu cầu hoàn tiền qua VNPay API
     *
     * @param amount             Số tiền cần hoàn (VND)
     * @param vnpTxnRef          Mã giao dịch nội bộ
     * @param vnpTransactionNo   Số giao dịch VNPay từ payment gốc
     * @param vnpTransactionDate Ngày giao dịch gốc (format: yyyyMMddHHmmss)
     * @return URL để gọi VNPay refund API
     */
    public String createRefundRequest(Long amount, String vnpTxnRef, String vnpTransactionNo, String vnpTransactionDate) {
        Map<String, String> vnpParamsMap = new HashMap<>();

        // Base config
        vnpParamsMap.put("vnp_Version", this.vnp_Version);
        vnpParamsMap.put("vnp_Command", "refund");
        vnpParamsMap.put("vnp_TmnCode", this.vnp_TmnCode);
        vnpParamsMap.put("vnp_CurrCode", "VND");

        // Transaction info
        vnpParamsMap.put("vnp_TxnRef", vnpTxnRef);
        vnpParamsMap.put("vnp_TransactionNo", vnpTransactionNo);
        vnpParamsMap.put("vnp_TransactionDate", vnpTransactionDate);
        vnpParamsMap.put("vnp_Amount", String.valueOf(amount * 100));  // Đổi sang xu

        // Order info
        vnpParamsMap.put("vnp_OrderInfo", "Hoan tien coc - " + vnpTxnRef);
        vnpParamsMap.put("vnp_OrderType", this.orderType);
        vnpParamsMap.put("vnp_Locale", "vn");

        // Create timestamp
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
        fmt.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        vnpParamsMap.put("vnp_CreateDate", fmt.format(cal.getTime()));

        // Build URL và hash
        String queryUrl = getPaymentURL(vnpParamsMap, true);
        String hashData = getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = hmacSHA512(this.secretKey, hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        return this.vnp_PayUrl + "?" + queryUrl;
    }

    /**
     * Parse vnp_TransactionNo từ providerResponse JSON
     */
    public static String extractTransactionNo(String providerResponseJson) {
        if (providerResponseJson == null || providerResponseJson.isEmpty()) {
            return null;
        }
        try {
            // Format: {"vnp_TransactionNo":"123","vnp_TxnRef":"456"}
            int start = providerResponseJson.indexOf("\"vnp_TransactionNo\":\"");
            if (start == -1) return null;
            start += "\"vnp_TransactionNo\":\"".length();
            int end = providerResponseJson.indexOf("\"", start);
            return providerResponseJson.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
