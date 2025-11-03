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
import org.springframework.security.access.prepost.PreAuthorize;
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

    // ===============================================
    // 1️ TẠO THANH TOÁN TIỀN CỌC
    // ===============================================
    @PostMapping("/create")
    @PreAuthorize("hasRole('CO_OWNER')")
    @Operation(
            summary = "Tạo yêu cầu thanh toán tiền cọc",
            description = """
            Tạo giao dịch thanh toán tiền cọc thông qua cổng thanh toán VNPay.  
            - Yêu cầu phải đăng nhập.  
            - Người dùng (CO_OWNER) chỉ có thể tạo thanh toán tiền cọc cho nhóm mà họ thuộc về.  
            - Hệ thống sẽ trả về `vnpayUrl` để redirect người dùng tới trang VNPay.
            """
    )    public ResponseEntity<DepositPaymentResponseDTO> createDepositPayment(
            @Valid @RequestBody DepositPaymentRequestDTO request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        DepositPaymentResponseDTO response =
                depositPaymentService.createDepositPayment(request, httpRequest, authentication);
        return ResponseEntity.ok(response);
    }

    // ===============================================
    // 2️ XÁC NHẬN THANH TOÁN (VNPay callback internal)
    // ===============================================
    @PostMapping("/confirm")
    @Operation(
            summary = "Xác nhận thanh toán từ VNPay",
            description = """
            Endpoint nội bộ được VNPay gọi lại sau khi người dùng thanh toán thành công.  
            - Cập nhật trạng thái `COMPLETED` cho Payment.  
            - Không được FE gọi trực tiếp.
            """
    )    public ResponseEntity<DepositPaymentResponseDTO> confirmDepositPayment(
            @RequestParam("vnp_TxnRef") String txnRef,
            @RequestParam("vnp_TxnRef") String transactionNo) {

        DepositPaymentResponseDTO response = depositPaymentService.confirmDepositPayment(txnRef, transactionNo);
        return ResponseEntity.ok(response);
    }

    // ===============================================
    // 3️ LẤY THÔNG TIN GIAO DỊCH THEO MÃ TXN REF
    // ===============================================
    @GetMapping("/info-by-txn")
    @Operation(
            summary = "Lấy thông tin thanh toán theo mã giao dịch",
            description = """
            Dùng để FE hiển thị chi tiết kết quả thanh toán sau khi redirect từ VNPay.  
            Truyền `txnRef` (mã giao dịch) để truy xuất thông tin Payment tương ứng.
            """
    )
    public ResponseEntity<DepositPaymentResponseDTO> getDepositInfoByTxn(@RequestParam String txnRef) {
        DepositPaymentResponseDTO response = depositPaymentService.getDepositInfoByTxn(txnRef);
        return ResponseEntity.ok(response);
    }



    // ===============================================
    // 4️ LẤY THÔNG TIN TIỀN CỌC CỦA USER TRONG GROUP
    // ===============================================
    @GetMapping("/info/{userId}/{groupId}")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(
            summary = "Thông tin tiền cọc cá nhân",
            description = """
            Trả về thông tin chi tiết tiền cọc của một người dùng trong nhóm.  
            - Dành cho người dùng đang xem chi tiết của chính họ hoặc quản trị viên nhóm.  
            """
    )
       public ResponseEntity<Map<String, Object>> getDepositInfo(
            @PathVariable Long userId,
            @PathVariable Long groupId) {

        Map<String, Object> info = depositPaymentService.getDepositInfo(userId, groupId);
        return ResponseEntity.ok(info);
    }

    // ===============================================
    // 5️ LẤY TRẠNG THÁI TIỀN CỌC CỦA TOÀN NHÓM
    // ===============================================
    @GetMapping("/group/{groupId}/status")
    @PreAuthorize("hasAnyRole('CO_OWNER','STAFF','ADMIN')")
    @Operation(
            summary = "Lấy trạng thái tiền cọc của nhóm",
            description = """
            Hiển thị trạng thái đóng cọc của tất cả thành viên trong nhóm sở hữu.  
            - Chỉ quản trị viên nhóm hoặc admin được phép truy cập.  
            - Trả về danh sách userId + depositStatus.
            """
    )
    public ResponseEntity<List<Map<String, Object>>> getGroupDepositStatus(
            @PathVariable Long groupId) {

        List<Map<String, Object>> status = depositPaymentService.getGroupDepositStatus(groupId);
        return ResponseEntity.ok(status);
    }

    // ===============================================
    // 6️ XỬ LÝ CALLBACK TRẢ VỀ TỪ VNPay (PUBLIC)
    // ===============================================
    @GetMapping("/deposit-callback")
    @Operation(
            summary = "Callback trả về từ VNPay (public)",
            description = """
            Đây là URL mà VNPay redirect người dùng sau khi thanh toán.  
            - Nếu mã `vnp_ResponseCode = '00'`: cập nhật thanh toán thành công và redirect về FE.  
            - Nếu khác '00': redirect về FE hiển thị lỗi hoặc thất bại.  
            - FE có thể truyền `groupId` để redirect chính xác về nhóm tương ứng.
            """
    )    public void handleDepositCallback(
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
