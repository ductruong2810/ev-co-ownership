package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.enums.PaymentStatus;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
@Transactional
public class FundService {
    private final SharedFundRepository fundRepo;
    private final OwnershipGroupRepository groupRepo;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository PaymentRepository;

    // =========================================================
    // New Functions after Database Update
    // =========================================================

    /**
     * Ham getLedgerSummary de xem day du thong tin Fund cua 1 group
     * Ham nay se duoc goi
     */
    @Transactional(readOnly = true)
    public LedgerSummaryDTO getLedgerSummary(Long groupId,
                                             @Nullable FundType fundType,
                                             @Nullable LocalDateTime from,
                                             @Nullable LocalDateTime to,
                                             @Nullable String preset) {
        LocalDateTimeRange range = resolveRange(from, to, preset);
        // 1) Tổng thu / chi trong khoản thời gian
        BigDecimal totalIn = nz(PaymentRepository.sumCompletedIn(groupId, fundType, range.from(), range.to()));
        BigDecimal totalOut = nz(expenseRepository.sumApprovedOut(groupId, fundType, range.from(), range.to()));

        // 2) Số dư hiện tại của 2 quỹ
        BigDecimal operatingBal = fundRepo
                .findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING)
                .map(f -> nz(f.getBalance())).orElse(BigDecimal.ZERO);

        BigDecimal depositBal = fundRepo
                .findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE)
                .map(f -> nz(f.getBalance())).orElse(BigDecimal.ZERO);

        // 3) Lấy danh sách dòng sổ quỹ (tái dùng hàm getLedger bạn đã có)
        List<LedgerRowDTO> rows = getLedger(groupId, fundType,
                (range.from() != null ? range.from().toLocalDate() : null),
                (range.to() != null ? range.to().toLocalDate() : null));

        return new LedgerSummaryDTO(totalIn, totalOut, operatingBal, depositBal, rows);
    }

    /**
     * Ham getLedger de xem day du thong tin Fund cua 1 group
     *
     */
    @Transactional(readOnly = true)
    public List<LedgerRowDTO> getLedger(Long groupId,
                                        @Nullable FundType fundType,
                                        @Nullable LocalDate fromDate,
                                        @Nullable LocalDate toDate) {

        // Neu fromD la null thi mac dinh 30 ngay truoc
        // Neu toD la null thi mac dinh ngay ket thuc la hom nay
        LocalDate fromD = (fromDate == null) ? LocalDate.now().minusDays(30) : fromDate;
        LocalDate toD = (toDate == null) ? LocalDate.now() : toDate;

        // du lieu tu fe la LocalDate
        // ==> phai chuyen ve LocalDateTime
        // inclusive range: [00:00:00, 23:59:59.999999999]
        LocalDateTime from = fromD.atStartOfDay();
        LocalDateTime to = toD.plusDays(1).atStartOfDay().minusNanos(1);

        // ===== IN (nạp tiền, chỉ lấy PAID) =====
        List<Payment> ins = (fundType == null)
                ? PaymentRepository.findByFund_Group_GroupIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                groupId, PaymentStatus.COMPLETED, from, to)
                : PaymentRepository.findByFund_Group_GroupIdAndFund_FundTypeAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
                groupId, fundType, PaymentStatus.COMPLETED, from, to);

        var inRows = ins.stream().map(p -> new LedgerRowDTO(
                "IN",
                p.getFund().getFundId(),
                p.getFund().getFundType(),
                safeName(p.getPayer()),                 // title
                displayRole(p.getPayer()),              // subtitle
                p.getPayer() != null ? p.getPayer().getUserId() : null,
                p.getAmount(),
                p.getPaidAt()
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
    private String safeName(User u) {
        return u == null ? "N/A" : (u.getFullName() != null ? u.getFullName() : u.getEmail());
    }

    private String displayRole(User u) {
        if (u == null || u.getRole() == null) return "";
        return switch (u.getRole().getRoleName()) {
            case ADMIN -> "Admin";
            case STAFF -> "Staff";
            default -> "Co-owner";
        };
    }

    private String mapExpenseTitle(String sourceType) {
        if (sourceType == null) return "Chi phí";
        return switch (sourceType) {
            case "MAINTENANCE", "INCIDENT", "EXPENSE" -> "Bảo Trì & Chi Phí";
            default -> "Chi phí";
        };
    }

    private String nzStr(String s) {
        return s == null ? "" : s;
    }

    //    private LocalDateTime toLocalDateTime(OffsetDateTime odt){ return odt==null ? null : odt.toLocalDateTime(); }
    private LocalDateTime coalesce(LocalDateTime a, LocalDateTime b) {
        return a != null ? a : b;
    }


    /**
     * initTwoFundIfMIssing
     * Kiem tra neu group co fund Operating va Deposit_Reserve thi tao
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
     * 2 hàm nghiệp vụ
     * addDepositToReserve : dong coc
     * topUpOperating: dong quy
     */
    // nap tien coc
    @Transactional
    public void addDepositToReserve(Long groupId, BigDecimal amt) {
        SharedFund r = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.DEPOSIT_RESERVE).orElseThrow();
        increaseBalance(r.getFundId(), amt);
    }

    // nap tien quy
    @Transactional
    public void topUpOperating(Long groupId, BigDecimal amt) {
        SharedFund op = fundRepo.findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING).orElseThrow();
        increaseBalance(op.getFundId(), amt);
    }

    /**
     * New read functions after updating a database
     */
    private BigDecimal nz(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }

    private record LocalDateTimeRange(LocalDateTime from, LocalDateTime to) {
    }

    private LocalDateTimeRange resolveRange(@Nullable LocalDateTime from,
                                            @Nullable LocalDateTime to,
                                            @Nullable String preset) {
        if (from != null || to != null || preset == null) {
            return new LocalDateTimeRange(from, to);
        }
        LocalDateTime now = LocalDateTime.now();
        return switch (preset.toUpperCase()) {
            case "MONTH" -> {
                LocalDateTime start = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
                LocalDateTime end = now.plusDays(1).toLocalDate().atStartOfDay();
                yield new LocalDateTimeRange(start, end);
            }
            case "WEEK" -> {
                LocalDateTime start = now.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay();
                LocalDateTime end = now.plusDays(1).toLocalDate().atStartOfDay();
                yield new LocalDateTimeRange(start, end);
            }
            case "YEAR" -> {
                LocalDateTime start = now.withDayOfYear(1).toLocalDate().atStartOfDay();
                LocalDateTime end = now.plusDays(1).toLocalDate().atStartOfDay();
                yield new LocalDateTimeRange(start, end);
            }
            case "ALL" -> new LocalDateTimeRange(null, null);
            default -> new LocalDateTimeRange(null, null);
        };
    }


    // Get List Fund by GroupID
    // ham nay se lay groupId
    // ==> roi trả ve 1 list: 2 bang ghi, 1 cho deposit và 1 cho operating
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

    // No Usage-------Create--------
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
                long backoffMillis = Math.min(200L, 20L * attempts);
                LockSupport.parkNanos(Duration.ofMillis(backoffMillis).toNanos());
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Thread interrupted while retrying fund balance update", e);
                }
            }
        }
    }

    // ================== CSV EXPORT FUNCTIONS ==================

    /**
     * Generate CSV content for a single group's ledger
     */
    @Transactional(readOnly = true)
    public String generateGroupLedgerCSV(Long groupId,
                                         @Nullable FundType fundType,
                                         @Nullable LocalDateTime from,
                                         @Nullable LocalDateTime to,
                                         @Nullable String preset) {
        OwnershipGroup group = groupRepo.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found: " + groupId));

        LedgerSummaryDTO summary = getLedgerSummary(groupId, fundType, from, to, preset);

        StringBuilder csv = new StringBuilder();
        csv.append("Financial Report - Group: ").append(group.getGroupName()).append(" (ID: ").append(groupId).append(")\n");
        csv.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        if (from != null || to != null) {
            csv.append("Period: ");
            if (from != null) csv.append(from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            csv.append(" to ");
            if (to != null) csv.append(to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            csv.append("\n");
        }
        csv.append("\n");

        // Summary section
        csv.append("Summary\n");
        csv.append("Total Income,").append(summary.totalIn()).append("\n");
        csv.append("Total Expense,").append(summary.totalOut()).append("\n");
        csv.append("Operating Balance,").append(summary.operatingBalance()).append("\n");
        csv.append("Deposit Balance,").append(summary.depositBalance()).append("\n");
        csv.append("\n");

        // Transaction details
        csv.append("Transaction Details\n");
        csv.append("Direction,Fund Type,Title,Subtitle,User ID,Amount,Date\n");
        for (LedgerRowDTO row : summary.rows()) {
            csv.append(escapeCSV(row.direction())).append(",");
            csv.append(escapeCSV(row.fundType() != null ? row.fundType().toString() : "")).append(",");
            csv.append(escapeCSV(row.title())).append(",");
            csv.append(escapeCSV(row.subtitle())).append(",");
            csv.append(row.userId() != null ? row.userId() : "").append(",");
            csv.append(row.amount()).append(",");
            csv.append(row.occurredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        }

        return csv.toString();
    }

    /**
     * Generate CSV content for all groups' financial reports
     */
    @Transactional(readOnly = true)
    public String generateAllGroupsFinancialReportCSV(@Nullable FundType fundType,
                                                      @Nullable LocalDateTime from,
                                                      @Nullable LocalDateTime to) {
        List<OwnershipGroup> allGroups = groupRepo.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("Financial Reports - All Groups\n");
        csv.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        if (from != null || to != null) {
            csv.append("Period: ");
            if (from != null) csv.append(from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            csv.append(" to ");
            if (to != null) csv.append(to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            csv.append("\n");
        }
        csv.append("\n");

        // Summary by group
        csv.append("Summary by Group\n");
        csv.append("Group ID,Group Name,Status,Total Income,Total Expense,Operating Balance,Deposit Balance,Net Balance\n");

        BigDecimal grandTotalIncome = BigDecimal.ZERO;
        BigDecimal grandTotalExpense = BigDecimal.ZERO;
        BigDecimal grandOperatingBalance = BigDecimal.ZERO;
        BigDecimal grandDepositBalance = BigDecimal.ZERO;

        for (OwnershipGroup group : allGroups) {
            try {
                LedgerSummaryDTO summary = getLedgerSummary(group.getGroupId(), fundType, from, to, null);
                BigDecimal netBalance = summary.totalIn().subtract(summary.totalOut());

                csv.append(group.getGroupId()).append(",");
                csv.append(escapeCSV(group.getGroupName())).append(",");
                csv.append(escapeCSV(group.getStatus() != null ? group.getStatus().toString() : "")).append(",");
                csv.append(summary.totalIn()).append(",");
                csv.append(summary.totalOut()).append(",");
                csv.append(summary.operatingBalance()).append(",");
                csv.append(summary.depositBalance()).append(",");
                csv.append(netBalance).append("\n");

                grandTotalIncome = grandTotalIncome.add(summary.totalIn());
                grandTotalExpense = grandTotalExpense.add(summary.totalOut());
                grandOperatingBalance = grandOperatingBalance.add(summary.operatingBalance());
                grandDepositBalance = grandDepositBalance.add(summary.depositBalance());
            } catch (Exception e) {
                // Skip groups with errors, log and continue
                csv.append(group.getGroupId()).append(",");
                csv.append(escapeCSV(group.getGroupName())).append(",");
                csv.append("ERROR,");
                csv.append("0,0,0,0,0\n");
            }
        }

        csv.append("\n");
        csv.append("Grand Total,,");
        csv.append(grandTotalIncome).append(",");
        csv.append(grandTotalExpense).append(",");
        csv.append(grandOperatingBalance).append(",");
        csv.append(grandDepositBalance).append(",");
        csv.append(grandTotalIncome.subtract(grandTotalExpense)).append("\n");

        return csv.toString();
    }

    /**
     * Escape CSV special characters
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        // If contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }


}
