package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.AmountRequest;
import com.group8.evcoownership.dto.FundBalanceResponse;
import com.group8.evcoownership.dto.SharedFundCreateRequest;
import com.group8.evcoownership.dto.SharedFundDto;
import com.group8.evcoownership.dto.SharedFundUpdateRequest;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.service.FundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    //--------Create------
    // Api tao quy moi cho group (path) - targetAmount mặc định = 0
    @PostMapping("/{groupId}")
    public FundBalanceResponse createFund(@PathVariable Long groupId) {
        SharedFund fund = fundService.createOrGroup(groupId);
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }
    
    // Api tao quy theo body(DTO) - có thể tùy chọn targetAmount
    @PostMapping
    public FundBalanceResponse createFund(@Valid @RequestBody SharedFundCreateRequest req) {
        SharedFund fund = fundService.create(req);
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }

    //-------Read------

    // Api xem so du quy theo groupId
    @GetMapping("/{groupId}")
    public FundBalanceResponse getFundBalance(@PathVariable("groupId") Long groupId) {
        return fundService.getBalanceByGroupId(groupId);
    }

    // Lay fund theo fundId
    @GetMapping("/id/{fundId}")
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
    public List<SharedFundDto> list(@ParameterObject Pageable pageable) {
        return fundService.list(pageable);
    }


    //------Update-----
    @PutMapping("/{fundId}")
    public SharedFund updateBalance(@PathVariable Long fundId,
                                    @Valid @RequestBody SharedFundUpdateRequest req) {
        return fundService.updateBalance(fundId, req);
    }

    // ====== DELETE ======
    @DeleteMapping("/{fundId}")
    public void delete(@PathVariable Long fundId) {
        fundService.deleteById(fundId);
    }


    // Tăng quỹ theo fundId
    @PostMapping("/{fundId}/increase")
    public FundBalanceResponse increase(@PathVariable Long fundId,
                                        @Valid @RequestBody AmountRequest req) {
        fundService.increaseBalance(fundId, req.amount());
        return fundService.getBalanceByFundId(fundId);
    }

    // Giảm quỹ theo fundId
    @PostMapping("/{fundId}/decrease")
    public FundBalanceResponse decrease(@PathVariable Long fundId,
                                        @Valid @RequestBody AmountRequest req) {
        fundService.decreaseBalance(fundId, req.amount());
        return fundService.getBalanceByFundId(fundId);
    }
}
