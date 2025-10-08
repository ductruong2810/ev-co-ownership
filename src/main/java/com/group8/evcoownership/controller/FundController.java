package com.group8.evcoownership.controller;

import com.group8.evcoownership.dto.FundBalanceResponse;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    // Api xem so du quy theo groupId
    @GetMapping("/{groupId}")
    public FundBalanceResponse getFundBalance(@PathVariable("groupId") Long groupId) {
        return fundService.getBalanceByGroupId(groupId);
    }

    // Api tao quy moi cho group
    @PostMapping("/{groupId}")
    public FundBalanceResponse createFund(@PathVariable Long groupId) {
        SharedFund fund = fundService.createForGroup(groupId);
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance());
    }

}
