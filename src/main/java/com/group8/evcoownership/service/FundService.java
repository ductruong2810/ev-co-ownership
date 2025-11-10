package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.repository.ExpenseRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.PaymentRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.group8.evcoownership.enums.RoleName.*;


@Service
@RequiredArgsConstructor
public class FundService {
    private final SharedFundRepository fundRepo;
    private final OwnershipGroupRepository groupRepo;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository PaymentRepository;

    // =========================================================
    // New Functions after Database Update
    // =========================================================

    /**
     * Ham getLedger de xem day du thong tin Fund cua 1 group
     *
     */
    @Transactional(readOnly = true)
    public List<LedgerRowDTO> getLedger(Long groupId,
                                        @Nullable FundType fundType,
                                        @Nullable LocalDate fromDate,
                                        @Nullable LocalDate toDate) {
        LocalDate fromD = (fromDate == null) ? LocalDate.now().minusDays(30) : fromDate;
        LocalDate toD   = (toDate   == null) ? LocalDate.now()              : toDate;
        LocalDateTime from = fromD.atStartOfDay();
        LocalDateTime to   = toD.atTime(23,59,59);

        // ===== IN (nạp tiền, chỉ lấy PAID) =====
        List<Payment> ins = (fundType == null)
                ? PaymentRepository.findByFund_Group_GroupIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                groupId, DepositStatus.PAID, from, to)
                : PaymentRepository.findByFund_Group_GroupIdAndFund_FundTypeAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                groupId, fundType, DepositStatus.PAID, from, to);

        var inRows = ins.stream().map(p -> new LedgerRowDTO(
                "IN",
                p.getFund().getFundId(),
                p.getFund().getFundType(),
                safeName(p.getPayer()),                 // title
                displayRole(p.getPayer()),              // subtitle
                p.getPayer() != null ? p.getPayer().getUserId() : null,
                p.getAmount(),
                toLocalDateTime(OffsetDateTime.from(p.getPaidAt()))
        )).toList();

        // ===== OUT (chi phí) =====
        List<Expense> outs = (fundType == null)
                ? expenseRepository.findByFund_Group_GroupIdAndExpenseDateBetweenOrderByExpenseDateDesc(groupId, from, to)
                : expenseRepository.findByFund_Group_GroupIdAndFund_FundTypeAndExpenseDateBetweenOrderByExpenseDateDesc(
                groupId, fundType, from, to);

        var outRows = outs.stream().map(e -> new LedgerRowDTO(
                "OUT",
                e.getFund().getFundId(),
                e.getFund().getFundType(),
                mapExpenseTitle(e.getSourceType()),      // vd: "Bảo Trì & Chi Phí"
                nzStr(e.getDescription()),               // subtitle từ description
                e.getRecipientUser() != null ? e.getRecipientUser().getUserId() : null,
                e.getAmount(),
                coalesce(e.getExpenseDate(), e.getUpdatedAt())
        )).toList();

        return Stream.concat(inRows.stream(), outRows.stream())
                .sorted(Comparator.comparing(LedgerRowDTO::occurredAt).reversed())
                .toList();
    }

    // -------- helpers (nhỏ gọn) ----------
    private String safeName(User u){ return u==null ? "N/A" : (u.getFullName()!=null ? u.getFullName() : u.getEmail()); }
    private String displayRole(User u){
        if (u==null || u.getRole()==null) return "";
        return switch (u.getRole().getRoleName()){
            case ADMIN -> "Admin";
            case STAFF -> "Staff";
            case CO_OWNER -> "Co-owner";
            default -> "Co-owner";
        };
    }
    private String mapExpenseTitle(String sourceType){
        if (sourceType==null) return "Chi phí";
        return switch (sourceType){
            case "MAINTENANCE", "INCIDENT", "EXPENSE" -> "Bảo Trì & Chi Phí";
            default -> "Chi phí";
        };
    }
    private String nzStr(String s){ return s==null ? "" : s; }
    private LocalDateTime toLocalDateTime(OffsetDateTime odt){ return odt==null ? null : odt.toLocalDateTime(); }
    private LocalDateTime coalesce(LocalDateTime a, LocalDateTime b){ return a!=null ? a : b; }



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
    public void initTwoFundsIfMissing(@NotNull Long groupId) {
        OwnershipGroup g = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));
        boolean hasOperating = fundRepo.existsByGroup_GroupIdAndFundType(groupId, FundType.OPERATING);
        if (!hasOperating) {
            SharedFund operating = SharedFund.builder()
                    .group(g).fundType(FundType.OPERATING)
                    .isSpendable(true).balance(BigDecimal.ZERO).targetAmount(BigDecimal.ZERO)
                    .build();
            fundRepo.save(operating);
        }
        boolean hasReserve = fundRepo.existsByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE);
        if (!hasReserve) {
            SharedFund reserve = SharedFund.builder()
                    .group(g).fundType(FundType.DEPOSIT_RESERVE)
                    .isSpendable(false).balance(BigDecimal.ZERO).targetAmount(BigDecimal.ZERO)
                    .build();
            fundRepo.save(reserve);
        }
    }

    /**
     * 4 hàm nghiệp vụ
     * addDepositToReserve được sử dụng trong confirmDepositPayment
     */
    @Transactional public void addDepositToReserve(Long groupId, BigDecimal amt) {
        SharedFund r = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE).orElseThrow();
        increaseBalance(r.getFundId(), amt);
    }

    @Transactional public void refundFromReserve(Long groupId, BigDecimal amt) {
        SharedFund r = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE).orElseThrow();
        decreaseBalance(r.getFundId(), amt);
    }

    @Transactional public void topUpOperating(Long groupId, BigDecimal amt) {
        SharedFund op = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING).orElseThrow();
        increaseBalance(op.getFundId(), amt);
    }

    @Transactional public void spendOperating(Long groupId, BigDecimal amt) {
        SharedFund op = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING).orElseThrow();
        if (!op.isSpendable()) throw new IllegalStateException("Operating fund is not spendable");
        decreaseBalance(op.getFundId(), amt);
    }

    /**
     * New read functions after updating database
     */
    private BigDecimal nz(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }


    // Get List Fund by GroupID
    // ham nay se lay groupId
    // ==> roi tra ve 1 list: 2 bang ghi, 1 cho deposit và 1 cho operating
    @Transactional(readOnly = true)
    public List<SharedFundDTO> listFundsByGroup(Long groupId) {
        List<SharedFund> funds = fundRepo.findAllByGroup_GroupId(groupId);
        if (funds.isEmpty()) {
            throw new EntityNotFoundException("No funds found for group " + groupId);
        }
        return funds.stream()
                .map(f -> new SharedFundDTO(
                        f.getFundId(),
                        f.getGroup() != null ? f.getGroup().getGroupId() : null,
                        f.getFundType(),
                        f.isSpendable(),
                        nz(f.getBalance()),
                        nz(f.getTargetAmount()),
                        f.getCreatedAt(),
                        f.getUpdatedAt()
                ))
                .toList();
    }

    // Ham nay tinh tong cua 2 loai quy: deposit va operating
    // tra ve : operatingBalance + reserveBalance + totalBalance
    @Transactional(readOnly = true)
    public FundsSummaryDTO getGroupFundsSummary(Long groupId) {
        SharedFund op = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING)
                .orElseThrow(() -> new EntityNotFoundException("Operating fund not found for group " + groupId));
        SharedFund rs = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE)
                .orElseThrow(() -> new EntityNotFoundException("Reserve fund not found for group " + groupId));

        BigDecimal ob = nz(op.getBalance());
        BigDecimal rb = nz(rs.getBalance());
        return new FundsSummaryDTO(groupId, ob, rb, ob.add(rb));
    }
    @Transactional(readOnly = true)
    public List<SharedFundDTO> list(Pageable pageable) {
        return fundRepo.findAll(pageable)
                .map(f -> new SharedFundDTO(
                        f.getFundId(),
                        (f.getGroup() != null ? f.getGroup().getGroupId() : null),
                        f.getFundType(),
                        f.isSpendable(),
                        (f.getBalance() == null ? BigDecimal.ZERO : f.getBalance()),
                        (f.getTargetAmount() == null ? BigDecimal.ZERO : f.getTargetAmount()),
                        f.getCreatedAt(),
                        f.getUpdatedAt()
                ))
                .getContent();
    }


    /**
     *
     *
     */

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
