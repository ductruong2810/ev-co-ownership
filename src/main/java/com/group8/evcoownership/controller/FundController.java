package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FundsSummaryDTO;
import com.group8.evcoownership.dto.LedgerSummaryDTO;
import com.group8.evcoownership.dto.SharedFundDTO;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.service.FundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
@Tag(name = "Funds", description = "Quản lý quỹ chung và số dư")
@PreAuthorize("isAuthenticated()")
public class FundController {

    private final FundService fundService;

//    //--------Create------
//    // Api tao quy moi cho group (path) - targetAmount mặc định = 0
//    @PostMapping("/{groupId}")
//    @Operation(summary = "Tạo quỹ cho nhóm", description = "Tạo quỹ chung mới cho một nhóm với số tiền mục tiêu mặc định")
//    public FundBalanceResponseDTO createFund(@PathVariable Long groupId) {
//        SharedFund fund = fundService.createOrGroup(groupId);
//        return new FundBalanceResponseDTO(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
//    }
//
//    // Api tao quy theo body(DTO) - có thể tùy chọn targetAmount
//    @PostMapping
//    @Operation(summary = "Tạo quỹ mới", description = "Tạo quỹ chung mới với thông tin chi tiết từ request body")
//    public FundBalanceResponseDTO createFund(@Valid @RequestBody SharedFundCreateRequestDTO req) {
//        SharedFund fund = fundService.create(req);
//        return new FundBalanceResponseDTO(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
//    }

    /**
     * new api after updating database
     */
    // ================== INIT ==================
//    @PostMapping("/groups/{groupId}/init")
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
//    @Operation(summary = "[ADMIN/STAFF] Khởi tạo 2 quỹ cho nhóm",
//            description = "Tạo OPERATING (chi được) & DEPOSIT_RESERVE (tiền cọc, không chi) nếu chưa có")
//    public void initFundsForGroup(@PathVariable Long groupId) {
//        fundService.initTwoFundsIfMissing(groupId);
//    }

// ================== READ ==================

    /**
     * API: Lấy sổ quỹ (Ledger) theo nhóm
     * hiển thị lịch sử giao dịch quỹ (thu/chi) trong một khoảng thời gian.
     *
     */
    @GetMapping("/groups/{groupId}/ledger/summary")
    @Operation(summary = "Tổng hợp sổ quỹ", description = "Trả về tổng thu/chi + số dư Operating/Reserve và danh sách dòng sổ quỹ")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
    public ResponseEntity<LedgerSummaryDTO> getLedgerSummary(
            @PathVariable Long groupId,
            @RequestParam(required = false) FundType fundType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(fundService.getLedgerSummary(groupId, fundType, from, to));
    }


//    @GetMapping("/groups/{groupId}/ledger")
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
//    @Operation(summary = "FE lấy sổ quỹ (IN/OUT) theo nhóm",
//            description = "Optional: fundType=OPERATING|DEPOSIT_RESERVE; from,to=yyyy-MM-dd")
//    public List<LedgerRowDTO> getLedger(
//            @PathVariable Long groupId,
//            @RequestParam(required = false) FundType fundType,
//            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
//            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
//        return fundService.getLedger(groupId, fundType, from, to);
//    }


    @GetMapping("/groups/{groupId}/all")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
    @Operation(summary = "Danh sách quỹ của 1 nhóm",
            description = "Trả về 2 bản ghi: OPERATING & DEPOSIT_RESERVE cho group")
    public List<SharedFundDTO> listFundsOfGroup(@PathVariable Long groupId) {
        return fundService.listFundsByGroup(groupId);
    }

    @GetMapping("/groups/{groupId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
    @Operation(summary = "Tổng quan quỹ của 1 nhóm",
            description = "operatingBalance (chi được) / depositBalance (cọc, khóa) / totalBalance")
    public FundsSummaryDTO getGroupSummary(@PathVariable Long groupId) {
        return fundService.getGroupFundsSummary(groupId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[ADMIN/STAFF] Danh sách tất cả quỹ (paged)",
            description = "Dùng cho admin/overview")
    public List<SharedFundDTO> listAll(@ParameterObject Pageable pageable) {
        return fundService.list(pageable); // đã map sang SharedFundDTO có fundType/spendable
    }


    // ====== DELETE ======
    @DeleteMapping("/{fundId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Xóa quỹ", description = "Chỉ Admin mới được phép xóa quỹ khỏi hệ thống.")
    public void delete(@PathVariable Long fundId) {
        fundService.deleteById(fundId);
    }


    // Tăng quỹ theo fundId
//    @PostMapping("/{fundId}/increase")
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
//    @Operation(summary = "[ADMIN/STAFF] Tăng số dư quỹ", description = "Tăng số dư quỹ với số tiền được chỉ định.")
//    public FundBalanceResponseDTO increase(@PathVariable Long fundId,
//                                           @Valid @RequestBody AmountRequestDTO req) {
//        fundService.increaseBalance(fundId, req.amount());
//        return fundService.getBalanceByFundId(fundId);
//    }
//
//    // Giảm quỹ theo fundId
//    @PostMapping("/{fundId}/decrease")
//    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
//    @Operation(summary = "[ADMIN/STAFF] Giảm số dư quỹ", description = "Giảm số dư quỹ với số tiền được chỉ định.")
//    public FundBalanceResponseDTO decrease(@PathVariable Long fundId,
//                                           @Valid @RequestBody AmountRequestDTO req) {
//        fundService.decreaseBalance(fundId, req.amount());
//        return fundService.getBalanceByFundId(fundId);
//    }
}
