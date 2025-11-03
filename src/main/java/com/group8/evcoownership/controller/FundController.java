package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.service.FundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
@Tag(name = "Funds", description = "Quản lý quỹ chung và số dư")
public class FundController {

    private final FundService fundService;

    //--------Create------
    // Api tao quy moi cho group (path) - targetAmount mặc định = 0
    @PostMapping("/{groupId}")
    @Operation(summary = "Tạo quỹ cho nhóm", description = "Tạo quỹ chung mới cho một nhóm với số tiền mục tiêu mặc định")
    public FundBalanceResponseDTO createFund(@PathVariable Long groupId) {
        SharedFund fund = fundService.createOrGroup(groupId);
        return new FundBalanceResponseDTO(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }

    // Api tao quy theo body(DTO) - có thể tùy chọn targetAmount
    @PostMapping
    @Operation(summary = "Tạo quỹ mới", description = "Tạo quỹ chung mới với thông tin chi tiết từ request body")
    public FundBalanceResponseDTO createFund(@Valid @RequestBody SharedFundCreateRequestDTO req) {
        SharedFund fund = fundService.create(req);
        return new FundBalanceResponseDTO(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }

    //-------Read------

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF','CO_OWNER')")
    @Operation(summary = "[CO_OWNER/STAFF/ADMIN] Xem số dư quỹ theo nhóm", description = """
        Lấy thông tin số dư quỹ của nhóm cụ thể.
        Co-owner chỉ có thể xem quỹ thuộc nhóm mình.
        """)    public FundBalanceResponseDTO getFundBalance(@PathVariable("groupId") Long groupId) {
        return fundService.getBalanceByGroupId(groupId);
    }

    // Lay fund theo fundId
    @GetMapping("/id/{fundId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[ADMIN/STAFF] Xem số dư quỹ theo ID", description = "Lấy thông tin số dư quỹ theo fundId.")    public FundBalanceResponseDTO getFundById(@PathVariable("fundId") Long fundId) {
        return fundService.getBalanceByFundId(fundId);
    }

    // Lấy fund theo groupId (trả entity)
//    @GetMapping("/group/{groupId}")
//    public SharedFund getByGroup(@PathVariable Long groupId) {
//        return fundService.getByGroupId(groupId);
//    }


    // Danh sách dạng List
//    @GetMapping("/funds")
//    public List<SharedFund> list(
//            @ParameterObject
//            @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC)
//            Pageable pageable) {
//        return fundService.list(pageable);
//    }
    @GetMapping("/funds")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[ADMIN/STAFF] Danh sách quỹ", description = "Lấy danh sách tất cả quỹ trong hệ thống (phân trang).")    public List<SharedFundDTO> list(@ParameterObject Pageable pageable) {
        return fundService.list(pageable);
    }


    //------Update-----
    @PutMapping("/{fundId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[ADMIN/STAFF] Cập nhật quỹ", description = "Cập nhật thông tin chi tiết của một quỹ.")
    public SharedFund updateBalance(@PathVariable Long fundId,
                                    @Valid @RequestBody SharedFundUpdateRequestDTO req) {
        return fundService.updateBalance(fundId, req);
    }

    // ====== DELETE ======
    @DeleteMapping("/{fundId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[ADMIN] Xóa quỹ", description = "Chỉ Admin mới được phép xóa quỹ khỏi hệ thống.")
    public void delete(@PathVariable Long fundId) {
        fundService.deleteById(fundId);
    }


    // Tăng quỹ theo fundId
    @PostMapping("/{fundId}/increase")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[ADMIN/STAFF] Tăng số dư quỹ", description = "Tăng số dư quỹ với số tiền được chỉ định.")
    public FundBalanceResponseDTO increase(@PathVariable Long fundId,
                                           @Valid @RequestBody AmountRequestDTO req) {
        fundService.increaseBalance(fundId, req.amount());
        return fundService.getBalanceByFundId(fundId);
    }

    // Giảm quỹ theo fundId
    @PostMapping("/{fundId}/decrease")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "[ADMIN/STAFF] Giảm số dư quỹ", description = "Giảm số dư quỹ với số tiền được chỉ định.")
    public FundBalanceResponseDTO decrease(@PathVariable Long fundId,
                                           @Valid @RequestBody AmountRequestDTO req) {
        fundService.decreaseBalance(fundId, req.amount());
        return fundService.getBalanceByFundId(fundId);
    }
}
