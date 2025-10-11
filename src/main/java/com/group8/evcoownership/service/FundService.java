package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.FundBalanceResponse;
import com.group8.evcoownership.dto.SharedFundCreateRequest;
import com.group8.evcoownership.dto.SharedFundDto;
import com.group8.evcoownership.dto.SharedFundUpdateRequest;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FundService {
    private final SharedFundRepository fundRepo;
    private final OwnershipGroupRepository groupRepo;

    // -------Create--------
    @Transactional
    // Tạo SharedFund mới cho một group cụ thể.
    public SharedFund createOrGroup(Long groupId) {
        if (fundRepo.existsByGroup_GroupId(groupId)) {
            throw new IllegalStateException("SharedFund already exists for group");
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
    // Tao  SharedFund qua body DTO
    public SharedFund create(SharedFundCreateRequest req) {
        return createOrGroup(req.getGroupId());
    }

    // -------Read--------
    // Lấy số dư quỹ theo groupId, trả về dạng DTO gọn
    @Transactional(readOnly = true)
    public FundBalanceResponse getBalanceByGroupId(Long groupId) {
        SharedFund fund = fundRepo.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance());
    }

    // Lay SharedFund theo fundId
    @Transactional(readOnly = true)
    public FundBalanceResponse getById(Long fundId) {
        SharedFund fund = fundRepo.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
        return new FundBalanceResponse(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance());
//        return fundRepo.findById(fundId)
//                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
    }

    // Lay SharedFund theo groupId
    @Transactional(readOnly = true)
    public SharedFund getByGroupId(Long groupId) {
        return fundRepo.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
    }

//    // Liet ke all SharedFund
//    @Transactional(readOnly = true)
//    public List<SharedFund> list(Pageable pageable) {
//        return fundRepo.findAll(pageable).getContent(); // lấy phần content ra làm List
//    }

    @Transactional(readOnly = true)
    public List<SharedFundDto> list(Pageable pageable) {
        return fundRepo.findAll(pageable).map(f ->
                new SharedFundDto(
                        f.getFundId(),
                        f.getGroup() != null ? f.getGroup().getGroupId() : null,
                        f.getBalance(),
                        f.getCreatedAt(),
                        f.getUpdatedAt()
                )
        ).getContent();
    }

    // Neu chua co thi tao roi tra ve
    @Transactional(readOnly = true)
    // Lấy quỹ theo groupId; nếu chưa có thì tạo mới rồi trả về.
    public SharedFund getOrCeateByGroup(Long groupId) {
        return fundRepo.findByGroup_GroupId(groupId)
                .orElseGet(() -> createOrGroup(groupId));
    }

    //-------Update-------
    public SharedFund updateBalance(Long id, SharedFundUpdateRequest req) {
        SharedFund fund = fundRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
        fund.setBalance(req.getBalance());// chi update, khong tang giam
        return fundRepo.save(fund);
    }

    //------delete-------
    public void deleteById(Long fundId) {
        if (!fundRepo.existsById(fundId)) {
            throw new EntityNotFoundException("SharedFund not found" + fundId);
        }
        fundRepo.deleteById(fundId);
    }


}
