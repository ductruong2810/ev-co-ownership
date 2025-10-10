package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.CreatePaymentRequest;
import com.group8.evcoownership.dto.PaymentResponse;
import com.group8.evcoownership.dto.UpdatePaymentRequest;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import com.group8.evcoownership.service.PaymentService; // interface Service của bạn
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // CREATE
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest req) {
        return paymentService.create(req);
    }

    // READ - by id
    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable Long id) {
        return paymentService.getById(id);
    }

    // LIST (trả List, có page/size/sort/asc để limit/offset)
    @GetMapping
    public List<PaymentResponse> search(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,      // dùng tên field của entity Payment
            @RequestParam(defaultValue = "false") boolean asc     // false = desc
    ) {
        return paymentService.search(userId, status, type, page, size, sort, asc);
    }

    // UPDATE
    @PutMapping("/{id}")
    public PaymentResponse update(@PathVariable Long id,
                                  @Valid @RequestBody UpdatePaymentRequest req) {
        return paymentService.update(id, req);
    }

    // DELETE
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        paymentService.delete(id);
    }

    // MARK PAID (ví dụ gọi từ VNPay return/IPN sau khi verify)
    @PostMapping("/{id}/mark-paid")
    public PaymentResponse markPaid(@PathVariable Long id,
                                    @RequestParam(required = false) String transactionCode,
                                    @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markPaid(id, transactionCode, providerResponseJson);
    }

    // MARK FAILED
    @PostMapping("/{id}/mark-failed")
    public PaymentResponse markFailed(@PathVariable Long id,
                                      @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markFailed(id, providerResponseJson);
    }

    // MARK REFUNDED (nếu có luồng hoàn tiền)
    @PostMapping("/{id}/mark-refunded")
    public PaymentResponse markRefunded(@PathVariable Long id,
                                        @RequestBody(required = false) String providerResponseJson) {
        return paymentService.markRefunded(id, providerResponseJson);
    }
}

