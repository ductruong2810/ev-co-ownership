package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.service.PaymentService;
import com.group8.evcoownership.service.VnPay_PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Quản lý thanh toán và giao dịch")
public class PaymentController {

    private final PaymentService paymentService;

    // CREATE
//    @PostMapping
//    @ResponseStatus(HttpStatus.CREATED)
//    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest req) {
//        return paymentService.create(req);
//    }
    private final VnPay_PaymentService vnPayService;

    @PostMapping
    @Operation(summary = "Tạo thanh toán", description = "Tạo một giao dịch thanh toán mới và sinh URL thanh toán VNPay")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequest request, HttpServletRequest servletRequest) {

        Long groupId = request.getGroupId();

        // 1️⃣ Lưu payment vào DB
        PaymentResponse payment = paymentService.create(request);

        // 2️⃣ Sinh URL VNPay
        String paymentUrl = vnPayService.createPaymentUrl(
                request.getAmount().longValue(),
                servletRequest,
                payment.getTransactionCode(), // hoặc payment.getId().toString()
                false,
                groupId
        );


        // 3️⃣ Trả về cho FE
        return ResponseEntity.ok(Map.of(
                "payment", payment,
                "paymentUrl", paymentUrl
        ));
    }

    // READ - by id
    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin thanh toán", description = "Lấy thông tin chi tiết của một giao dịch thanh toán theo ID")
    public PaymentResponse getById(@PathVariable Long id) {
        return paymentService.getById(id);
    }

    // LIST (trả List, có page/size/sort/asc để limit/offset)
    @GetMapping
    @Operation(summary = "Tìm kiếm thanh toán", description = "Tìm kiếm và lọc danh sách giao dịch thanh toán với phân trang")
    public List<PaymentResponse> search(
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
    public PaymentResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdatePaymentRequest req) {
        return paymentService.update(id, req);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái thanh toán", description = "Cập nhật trạng thái của giao dịch thanh toán")
    public PaymentResponse updateStatus(@PathVariable Long id,
                                        @Valid @RequestBody PaymentStatusUpdateRequest req) {
        return paymentService.updateStatus(id, req.status(), req.transactionCode(), req.providerResponseJson());
    }

    // DELETE
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa thanh toán", description = "Xóa một giao dịch thanh toán khỏi hệ thống")
    public void delete(@PathVariable Long id) {
        paymentService.delete(id);
    }

    // MARK PAID (ví dụ gọi từ VNPay return/IPN sau khi verify)
    @PostMapping("/{id}/mark-paid")
    @Operation(summary = "Đánh dấu đã thanh toán", description = "Đánh dấu giao dịch đã được thanh toán thành công")
    public PaymentResponse markPaid(@PathVariable Long id,
                                    @RequestParam(required = false) String transactionCode,
                                    @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markPaid(id, transactionCode, providerResponseJson);
    }

    // MARK FAILED
    @PostMapping("/{id}/mark-failed")
    @Operation(summary = "Đánh dấu thanh toán thất bại", description = "Đánh dấu giao dịch thanh toán thất bại")
    public PaymentResponse markFailed(@PathVariable Long id,
                                      @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markFailed(id, providerResponseJson);
    }

    // MARK REFUNDED (nếu có luồng hoàn tiền)
    @PostMapping("/{id}/mark-refunded")
    @Operation(summary = "Đánh dấu đã hoàn tiền", description = "Đánh dấu giao dịch đã được hoàn tiền")
    public PaymentResponse markRefunded(@PathVariable Long id,
                                        @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markRefunded(id, providerResponseJson);
    }
}

