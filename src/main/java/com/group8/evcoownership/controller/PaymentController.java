package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.service.PaymentService;
import com.group8.evcoownership.service.VnPay_PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static jdk.jfr.FlightRecorder.isInitialized;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Quản lý thanh toán và giao dịch")
@PreAuthorize("isAuthenticated()")
public class PaymentController {

    private final PaymentService paymentService;
    private final VnPay_PaymentService vnPayService;

    /**
     * Lay lich su giao dich cua 1 user
     */
    @GetMapping("/history")
    @Operation(
            summary = "Lịch sử giao dịch đã hoàn tất của 1 user trong 1 group",
            description = "Chỉ trả các Payment có status=COMPLETED; không lọc theo paymentType. " +
                    "Query: userId, groupId, fromDate/toDate (yyyy-MM-dd), page, size."
    )
    @PreAuthorize("hasAnyRole('CO_OWNER')")
    public ResponseEntity<PaymentHistoryResponseDTO> getPaymentHistory(
            @Valid @ModelAttribute PaymentHistoryBasicRequestDTO q) {
        // @ModelAttribute sẽ tự bind query string vào DTO (kể cả fromDate/toDate)
        return ResponseEntity.ok(paymentService.getPersonalHistory(q));
    }


    /**
     * Tao thanh toan
     */
    @PostMapping
    @Operation(summary = "Tạo thanh toán", description = "Tạo một giao dịch thanh toán mới và sinh URL thanh toán VNPay")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequestDTO request, HttpServletRequest servletRequest) {

        Long groupId = request.getGroupId();

        // Lưu payment vào DB
        PaymentResponseDTO payment = paymentService.create(request);

        //Sinh URL VNPay
        String paymentUrl = vnPayService.createPaymentUrl(
                request.getAmount().longValue(),
                servletRequest,
                payment.getTransactionCode(), // hoặc payment.getId().toString()
                false,
                groupId
        );


        //Trả về cho FE
        return ResponseEntity.ok(Map.of(
                "payment", payment,
                "paymentUrl", paymentUrl
        ));
    }

    // READ - by id
    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin thanh toán", description = "Lấy thông tin chi tiết của một giao dịch thanh toán theo ID")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN', 'CO_OWNER')")
    public PaymentResponseDTO getById(@PathVariable Long id) {
        return paymentService.getById(id);
    }

    // LIST (trả List, có page/size/sort/asc để limit/offset)
    @GetMapping
    @Operation(summary = "Tìm kiếm thanh toán", description = "Tìm kiếm và lọc danh sách giao dịch thanh toán với phân trang")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public List<PaymentResponseDTO> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,      // dùng tên field của entity Payment
            @RequestParam(defaultValue = "false") boolean asc     // false = desc
    ) {
        return paymentService.search(userId, status, type, page, size, sort, asc);
    }

    // UPDATE
    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thanh toán", description = "Cập nhật thông tin của một giao dịch thanh toán")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponseDTO update(@PathVariable Long id,
                                  @Valid @RequestBody UpdatePaymentRequestDTO req) {
        return paymentService.update(id, req);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái thanh toán", description = "Cập nhật trạng thái của giao dịch thanh toán")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponseDTO updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody PaymentStatusUpdateRequestDTO req) {
        return paymentService.updateStatus(id, req.status(), req.transactionCode(), req.providerResponseJson());
    }

    // DELETE
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa thanh toán", description = "Xóa một giao dịch thanh toán khỏi hệ thống")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        paymentService.delete(id);
    }

    // MARK PAID (ví dụ gọi từ VNPay return/IPN sau khi verify)
    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Đánh dấu đã thanh toán", description = "Đánh dấu giao dịch đã được thanh toán thành công")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponseDTO markPaid(@PathVariable Long id,
                                    @RequestParam(required = false) String transactionCode,
                                    @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markPaid(id, transactionCode, providerResponseJson);
    }

    // MARK FAILED
    @PostMapping("/{id}/mark-failed")
    @Operation(summary = "Đánh dấu thanh toán thất bại", description = "Đánh dấu giao dịch thanh toán thất bại")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponseDTO markFailed(@PathVariable Long id,
                                      @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markFailed(id, providerResponseJson);
    }

    // MARK REFUNDED (nếu có luồng hoàn tiền)
    @PostMapping("/{id}/mark-refunded")
    @Operation(summary = "Đánh dấu đã hoàn tiền", description = "Đánh dấu giao dịch đã được hoàn tiền")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PaymentResponseDTO markRefunded(@PathVariable Long id,
                                        @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markRefunded(id, providerResponseJson);
    }
}

