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

        // 1) Chọn callback BE theo loại giao dịch
        String baseReturn = isDeposit ? this.vnp_DepositReturnUrl : this.vnp_ReturnUrl;

        // 2) Lấy groupId & type để gửi kèm cho callback
        String gid = (groupId != null) ? String.valueOf(groupId) : request.getParameter("groupId");
        if (gid == null || gid.isEmpty()) gid = "0";          // hoặc throw nếu muốn bắt buộc
        String type = isDeposit ? "deposit" : "fund";

        // 3) Luôn append groupId + type vào returnUrl (dùng ? hoặc & cho đúng cú pháp)
        String joiner = baseReturn.contains("?") ? "&" : "?";
        String returnUrl = baseReturn + joiner + "groupId=" + gid + "&type=" + type;

        // 4) Set tham số bắt buộc của VNPay
        vnpParamsMap.put("vnp_ReturnUrl", returnUrl);         // getPaymentURL sẽ URL-encode value này
        vnpParamsMap.put("vnp_Amount", String.valueOf(amount));
        vnpParamsMap.put("vnp_IpAddr", getIpAddress(request));
        vnpParamsMap.put("vnp_TxnRef", txnRef);

//        String queryUrl   = getPaymentURL(vnpParamsMap, true);
//        String hashData   = getPaymentURL(vnpParamsMap, false);
//        String vnpHash    = hmacSHA512(secretKey, hashData);
//        queryUrl += "&vnp_SecureHash=" + vnpHash;
//
//        return vnp_PayUrl + "?" + queryUrl;

        String hashData = buildHashData(vnpParamsMap);
        String signature = hmacSHA512(secretKey, hashData);
        vnpParamsMap.put("vnp_SecureHash", signature);

        String queryUrl = buildQueryString(vnpParamsMap);
        return vnp_PayUrl + "?" + queryUrl;
    }


    /**
     * 3 Callback hiển thị kết quả thanh toán (nếu bạn dùng tạm thời)
     * Ở dự án EV Co-ownership, thực tế bạn dùng DepositController.depositCallback()
     * để xử lý cập nhật Payment/OwnershipShare → nên hàm này chỉ cần redirect đơn giản
     */
    public void handlePaymentCallBack(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String respCode = req.getParameter("vnp_ResponseCode");
        String txnRef = req.getParameter("vnp_TxnRef");
        String groupId = req.getParameter("groupId");
        String type = req.getParameter("type"); // "fund" | "deposit"

        String status = "00".equals(respCode) ? "success" : "fail";

        String redirect = String.format(
                "%s/dashboard/viewGroups/%s/payment-result?type=%s&status=%s&txnRef=%s",
                frontendUrl,
                (groupId != null ? groupId : "unknown"),
                (type != null ? type : "fund"),
                status,
                (txnRef != null ? txnRef : "")
        );
        res.sendRedirect(redirect);
    }

    /**
     * New for fixing sai chu ky o deposit payment
     */
    // Helper encode: dùng UTF-8, thay "+" -> "%20" để ổn định
    private static String enc(String s) {
        // Giữ nguyên chuẩn URL-encode của Java (UTF-8, khoảng trắng = '+')
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }


    // Build chuỗi cho HASH: sort theo key, KHÔNG đưa vnp_SecureHash / vnp_SecureHashType
    private static String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_"))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .filter(e -> !"vnp_SecureHash".equals(e.getKey()))
                .filter(e -> !"vnp_SecureHashType".equals(e.getKey()))   // ← thêm dòng này
                .sorted(Map.Entry.comparingByKey())
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }


    // Build query gửi đi: giống hệt hashData + thêm vnp_SecureHash ở cuối
    private static String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /**
     *
     */


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
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec sk = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(sk);
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(result.length * 2);
            for (byte b : result) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


//    public static String hmacSHA512(final String key, final String data) {
//        try {
//            if (key == null || data == null) {
//                throw new NullPointerException();
//            }
//            final Mac hmac512 = Mac.getInstance("HmacSHA512");
//            byte[] hmacKeyBytes = key.getBytes();
//            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
//            hmac512.init(secretKey);
//            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
//            byte[] result = hmac512.doFinal(dataBytes);
//            StringBuilder sb = new StringBuilder(2 * result.length);
//            for (byte b : result) {
//                sb.append(String.format("%02x", b & 0xff));
//            }
//            return sb.toString();
//
//        } catch (Exception ex) {
//            return "";
//        }
//    }

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
//        String queryUrl = getPaymentURL(vnpParamsMap, true);
//        String hashData = getPaymentURL(vnpParamsMap, false);
//        String vnpSecureHash = hmacSHA512(this.secretKey, hashData);
//        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
//
//        return this.vnp_PayUrl + "?" + queryUrl;

        vnpParamsMap.put("vnp_SecureHashType", "HmacSHA512");
        String hashData = buildHashData(vnpParamsMap);
        String signature = hmacSHA512(this.secretKey, hashData);
        vnpParamsMap.put("vnp_SecureHash", signature);

        String queryUrl = buildQueryString(vnpParamsMap);
        return this.vnp_PayUrl + "?" + queryUrl;

    }

    /**
     * lấy vnp_TransactionNo ra khoi chuoi JSON được lưu trong providerResponse
     */
    public static String extractTransactionNo(String providerResponseJson) {
        if (providerResponseJson == null || providerResponseJson.isEmpty()) {
            return null;
        }
        try {
            // Format: {"vnp_TransactionNo":"123","vnp_TxnRef":"456"}
            // tim xem vnp_TranctionNo o dau
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
