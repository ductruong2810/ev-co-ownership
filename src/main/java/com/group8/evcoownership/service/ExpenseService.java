package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.ExpenseCreateRequestDTO;
import com.group8.evcoownership.dto.ExpenseResponseDTO;
import com.group8.evcoownership.dto.ExpenseUpdateRequestDTO;
import com.group8.evcoownership.entity.Expense;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.repository.ExpenseRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final SharedFundRepository sharedFundRepository;

    /* ---------- Create ---------- */
    public ExpenseResponseDTO create(ExpenseCreateRequestDTO req) {
        if (req.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (req.getAmount().signum() < 0) {
            throw new IllegalArgumentException("Amount must be >= 0");
        }

        SharedFund fund = sharedFundRepository.findById(req.getFundId())
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found: " + req.getFundId()));

        Expense e = Expense.builder()
                .fund(fund)
                .amount(req.getAmount())
                .sourceType(req.getSourceType())
                .sourceId(req.getSourceId())
                .description(req.getDescription())
                // nếu client gửi expenseDate thì set, nếu không để null để @CreationTimestamp tự gán
                .build();

        if (req.getExpenseDate() != null) {
            e.setExpenseDate(req.getExpenseDate());
        }

        e = expenseRepository.save(e);
        return toResponse(e);
    }

    /* ---------- Read one ---------- */
    public ExpenseResponseDTO getById(Long id) {
        Expense e = expenseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense not found: " + id));
        return toResponse(e);
    }

    /* ---------- Patch update ---------- */
    public ExpenseResponseDTO update(Long id, ExpenseUpdateRequestDTO req) {
        Expense e = expenseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense not found: " + id));

        if (req.getAmount() != null) {
            if (req.getAmount().signum() < 0) {
                throw new IllegalArgumentException("Amount must be >= 0");
            }
            e.setAmount(req.getAmount());
        }
        if (req.getSourceType() != null) e.setSourceType(req.getSourceType());
        if (req.getSourceId() != null) e.setSourceId(req.getSourceId());
        if (req.getDescription() != null) e.setDescription(req.getDescription());
        if (req.getExpenseDate() != null) e.setExpenseDate(req.getExpenseDate());

        e = expenseRepository.save(e);
        return toResponse(e);
    }

    /* ---------- Delete ---------- */
    public void delete(Long id) {
        Expense e = expenseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Expense not found: " + id));
        expenseRepository.delete(e);
    }

    /* ---------- List + filters ---------- */
    public Page<ExpenseResponseDTO> list(
            Long fundId,
            String sourceType,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        boolean hasFund = fundId != null;
        boolean hasSourceType = sourceType != null && !sourceType.isBlank();
        boolean hasRange = from != null && to != null;

        if (hasFund && hasSourceType) {
            return expenseRepository
                    .findByFund_FundIdAndSourceType(fundId, sourceType, pageable)
                    .map(this::toResponse);
        } else if (hasFund && hasRange) {
            return expenseRepository
                    .findByFund_FundIdAndExpenseDateBetween(fundId, from, to, pageable)
                    .map(this::toResponse);
        } else if (hasRange) {
            return expenseRepository
                    .findByExpenseDateBetween(from, to, pageable)
                    .map(this::toResponse);
        } else if (hasFund) {
            return expenseRepository
                    .findByFund_FundId(fundId, pageable)
                    .map(this::toResponse);
        } else {
            return expenseRepository.findAll(pageable).map(this::toResponse);
        }

    }

    /* ---------- Mapper ---------- */
    private ExpenseResponseDTO toResponse(Expense e) {
        return ExpenseResponseDTO.builder()
                .id(e.getId())
                .fundId(e.getFund() != null ? e.getFund().getFundId() : null) // nếu SharedFund là fundId -> đổi sang getFundId()
                .sourceType(e.getSourceType())
                .sourceId(e.getSourceId())
                .description(e.getDescription())
                .amount(e.getAmount())
                .expenseDate(e.getExpenseDate())
                .build();
    }
}
