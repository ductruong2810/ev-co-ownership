package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.FundBalanceResponse;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FundService {
    private final SharedFundRepository fundRepo;
    private final OwnershipGroupRepository groupRepo;

    @Transactional
    public FundBalanceResponse getBalanceByGroupId(Long groupId)
    {
        SharedFund fund = fundRepo.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance());
    }

    @Transactional
    // Tao quy chung cho 1 nhom
    public SharedFund createForGroup(Long groupId){
        if(fundRepo.existsByGroup_GroupId(groupId)){
            throw new EntityNotFoundException("SharedFund already exists for group");
        }
        OwnershipGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found" + groupId));

        SharedFund fund = SharedFund.builder()
                .group(group)
                .balance(BigDecimal.ZERO)
                .build();
        return fundRepo.save(fund);

    }

    @Transactional
    // Lấy quỹ theo groupId; nếu chưa có thì tạo mới rồi trả về.
    public SharedFund getOrCeateByGroup(Long groupId)
    {
        return fundRepo.findByGroup_GroupId(groupId)
                .orElseGet(() -> createForGroup(groupId));
    }

}
