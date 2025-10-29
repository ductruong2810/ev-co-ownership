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
    public FundBalanceResponse createFund(@PathVariable Long groupId) {
        SharedFund fund = fundService.createOrGroup(groupId);
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }

    // Api tao quy theo body(DTO) - có thể tùy chọn targetAmount
    @PostMapping
    @Operation(summary = "Tạo quỹ mới", description = "Tạo quỹ chung mới với thông tin chi tiết từ request body")
    public FundBalanceResponse createFund(@Valid @RequestBody SharedFundCreateRequest req) {
        SharedFund fund = fundService.create(req);
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }

    //-------Read------

    // Api xem so du quy theo groupId
    @GetMapping("/{groupId}")
    @Operation(summary = "Xem số dư quỹ theo nhóm", description = "Lấy thông tin số dư quỹ của một nhóm cụ thể")
    public FundBalanceResponse getFundBalance(@PathVariable("groupId") Long groupId) {
        return fundService.getBalanceByGroupId(groupId);
    }

    // Lay fund theo fundId
    @GetMapping("/id/{fundId}")
    @Operation(summary = "Xem số dư quỹ theo ID", description = "Lấy thông tin số dư quỹ theo ID quỹ")
    public FundBalanceResponse getFundById(@PathVariable("fundId") Long fundId) {
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
    @Operation(summary = "Danh sách quỹ", description = "Lấy danh sách tất cả quỹ trong hệ thống với phân trang")
    public List<SharedFundDto> list(@ParameterObject Pageable pageable) {
        return fundService.list(pageable);
    }


    //------Update-----
    @PutMapping("/{fundId}")
    @Operation(summary = "Cập nhật quỹ", description = "Cập nhật thông tin của một quỹ")
    public SharedFund updateBalance(@PathVariable Long fundId,
                                    @Valid @RequestBody SharedFundUpdateRequest req) {
        return fundService.updateBalance(fundId, req);
    }

    // ====== DELETE ======
    @DeleteMapping("/{fundId}")
    @Operation(summary = "Xóa quỹ", description = "Xóa một quỹ khỏi hệ thống")
    public void delete(@PathVariable Long fundId) {
        fundService.deleteById(fundId);
    }


    // Tăng quỹ theo fundId
    @PostMapping("/{fundId}/increase")
    @Operation(summary = "Tăng số dư quỹ", description = "Tăng số dư của quỹ theo số tiền được chỉ định")
    public FundBalanceResponse increase(@PathVariable Long fundId,
                                        @Valid @RequestBody AmountRequest req) {
        fundService.increaseBalance(fundId, req.amount());
        return fundService.getBalanceByFundId(fundId);
    }

    // Giảm quỹ theo fundId
    @PostMapping("/{fundId}/decrease")
    @Operation(summary = "Giảm số dư quỹ", description = "Giảm số dư của quỹ theo số tiền được chỉ định")
    public FundBalanceResponse decrease(@PathVariable Long fundId,
                                        @Valid @RequestBody AmountRequest req) {
        fundService.decreaseBalance(fundId, req.amount());
        return fundService.getBalanceByFundId(fundId);
    }
}
