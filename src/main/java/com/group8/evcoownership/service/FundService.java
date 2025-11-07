package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.FundBalanceResponseDTO;
import com.group8.evcoownership.dto.SharedFundCreateRequestDTO;
import com.group8.evcoownership.dto.SharedFundDTO;
import com.group8.evcoownership.dto.SharedFundUpdateRequestDTO;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
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

    // =========================================================
    // New Functions after Database Update
    // =========================================================
    /**
     * 1) initTwoFundIfMIssing : su dung cho OwnerShipGroupService
     * 2) addDepositToReserve  : su dung cho confirmDepositPayment
     * 3) refundFromReserve
     * 4) topUpOperating
     * 5) spendOperating
     */

    /**
     * initTwoFundIfMIssing
     * Kiem tra neu chua co group co type Operating va Deposit_Reserve thi tao
     * Được gọi ở OwnerShipGroupService
     */
    @Transactional
    public void initTwoFundsIfMissing(Long groupId) {
        var g = groupRepo.findById(groupId).orElseThrow();
        if (!fundRepo.existsByGroup_GroupIdAndFundType(groupId, FundType.OPERATING)) {
            fundRepo.save(SharedFund.builder().group(g).fundType(FundType.OPERATING)
                    .isSpendable(true).balance(BigDecimal.ZERO).targetAmount(BigDecimal.ZERO).build());
        }
        if (!fundRepo.existsByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE)) {
            fundRepo.save(SharedFund.builder().group(g).fundType(FundType.DEPOSIT_RESERVE)
                    .isSpendable(false).balance(BigDecimal.ZERO).targetAmount(BigDecimal.ZERO).build());
        }
    }

    /**
     * 4 hàm nghiệp vụ
     * addDepositToReserve được sử dụng trong confirmDepositPayment
     */
    @Transactional public void addDepositToReserve(Long groupId, BigDecimal amt) {
        var r = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE).orElseThrow();
        increaseBalance(r.getFundId(), amt);
    }

    @Transactional public void refundFromReserve(Long groupId, BigDecimal amt) {
        var r = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE).orElseThrow();
        decreaseBalance(r.getFundId(), amt);
    }

    @Transactional public void topUpOperating(Long groupId, BigDecimal amt) {
        var op = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING).orElseThrow();
        increaseBalance(op.getFundId(), amt);
    }

    @Transactional public void spendOperating(Long groupId, BigDecimal amt) {
        var op = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING).orElseThrow();
        if (!op.isSpendable()) throw new IllegalStateException("Operating fund is not spendable");
        decreaseBalance(op.getFundId(), amt);
    }




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
                .targetAmount(BigDecimal.ZERO)
                .build();
        return fundRepo.save(fund);

    }

    @Transactional
    // Tao  SharedFund qua body DTO
    public SharedFund create(SharedFundCreateRequestDTO req) {
        if (fundRepo.existsByGroup_GroupId(req.getGroupId())) {
            throw new IllegalStateException("SharedFund already exists for group");
        }
        OwnershipGroup group = groupRepo.findById(req.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + req.getGroupId()));

        SharedFund fund = SharedFund.builder()
                .group(group)
                .balance(BigDecimal.ZERO)
                .targetAmount(req.getTargetAmount() != null ? req.getTargetAmount() : BigDecimal.ZERO)
                .build();
        return fundRepo.save(fund);
    }

    // -------Read--------
    // Lấy số dư quỹ theo groupId, trả về dạng DTO gọn
    @Transactional(readOnly = true)
    public FundBalanceResponseDTO getBalanceByGroupId(Long groupId) {
        SharedFund fund = fundRepo.findByGroup_GroupId(groupId)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
        return new FundBalanceResponseDTO(fund.getFundId(), fund.getGroup().getGroupId(), fund.getBalance(), fund.getTargetAmount());
    }

    // Lay SharedFund theo fundId
    @Transactional(readOnly = true)
    public FundBalanceResponseDTO getBalanceByFundId(Long fundId) {
        SharedFund fund = fundRepo.findById(fundId)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found: " + fundId));
        return new FundBalanceResponseDTO(
                fund.getFundId(),
                fund.getGroup().getGroupId(),
                fund.getBalance(),
                fund.getTargetAmount()
        );
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
    public List<SharedFundDTO> list(Pageable pageable) {
        return fundRepo.findAll(pageable).map(f ->
                new SharedFundDTO(
                        f.getFundId(),
                        f.getGroup() != null ? f.getGroup().getGroupId() : null,
                        f.getBalance(),
                        f.getTargetAmount(),
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
    public SharedFund updateBalance(Long id, SharedFundUpdateRequestDTO req) {
        SharedFund fund = fundRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found"));
        fund.setBalance(req.getBalance());// chi update, khong tang giam
        if (req.getTargetAmount() != null) {
            fund.setTargetAmount(req.getTargetAmount());
        }
        return fundRepo.save(fund);
    }

    //------delete-------
    public void deleteById(Long fundId) {
        if (!fundRepo.existsById(fundId)) {
            throw new EntityNotFoundException("SharedFund not found" + fundId);
        }
        fundRepo.deleteById(fundId);
    }


    private static final int MAX_RETRY = 3;

    // ... (các hàm CRUD bạn đã có ở trên)

    /**
     * Tăng quỹ (nạp tiền)
     */
    @Transactional
    public void increaseBalance(Long fundId, BigDecimal amount) {
        updateBalanceWithRetry(fundId, amount, true);
    }

    /**
     * Giảm quỹ (chi/hoàn tiền)
     */
    @Transactional
    public void decreaseBalance(Long fundId, BigDecimal amount) {
        updateBalanceWithRetry(fundId, amount, false);
    }

    /**
     * Core update với optimistic locking + retry
     */
    private void updateBalanceWithRetry(Long fundId, BigDecimal amount, boolean increase) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be > 0");
        }

        int attempts = 0;
        while (true) {
            attempts++;
            try {
                SharedFund fund = fundRepo.findById(fundId)
                        .orElseThrow(() -> new EntityNotFoundException("SharedFund not found: " + fundId));

                BigDecimal current = fund.getBalance() == null ? BigDecimal.ZERO : fund.getBalance();

                if (!increase && amount.compareTo(current) > 0) {
                    throw new IllegalStateException("Số dư quỹ không đủ để chi!");
                }

                BigDecimal newBalance = increase ? current.add(amount) : current.subtract(amount);
                fund.setBalance(newBalance);

                fundRepo.saveAndFlush(fund); // sẽ ném OptimisticLock nếu version bị đổi
                return; // OK
            } catch (OptimisticLockingFailureException e) {
                if (attempts >= MAX_RETRY) {
                    throw new IllegalStateException("Cập nhật quỹ thất bại do tranh chấp đồng thời. Vui lòng thử lại.", e);
                }
                // ngắt nhịp rất ngắn trước khi thử lại (tùy chọn)
                try {
                    Thread.sleep(20L * attempts);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }


}
