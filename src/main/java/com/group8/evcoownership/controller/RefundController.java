package com.group8.evcoownership.controller;

import com.group8.evcoownership.entity.Refund;
import com.group8.evcoownership.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@Tag(name = "Refunds", description = "Quản lý hoàn tiền")
public class RefundController {
    
    private final RefundService refundService;
    
    @PostMapping("/create/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo yêu cầu hoàn tiền", description = "Tạo yêu cầu hoàn tiền cho payment đã hoàn thành")
    public ResponseEntity<Refund> createRefund(
            @PathVariable Long paymentId,
            @RequestParam(required = false) String reason) {
        
        Refund refund = refundService.createRefundRequest(paymentId, reason);
        return ResponseEntity.ok(refund);
    }
    
    @GetMapping
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @Operation(summary = "Danh sách refund", description = "Lấy danh sách tất cả refund")
    public ResponseEntity<List<Refund>> getAllRefunds() {
        List<Refund> refunds = refundService.getAllRefunds();
        return ResponseEntity.ok(refunds);
    }
}

