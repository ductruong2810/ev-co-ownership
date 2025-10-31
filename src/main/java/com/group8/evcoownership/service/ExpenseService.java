package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.ExpenseResponseDTO;
import com.group8.evcoownership.dto.ExpenseCreateRequestDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final SharedFundRepository sharedFundRepository;
    private final UserRepository userRepository;

    // =============== CREATE ===============
    @Transactional
    public ExpenseResponseDTO create(ExpenseCreateRequestDTO req, String username) {
        SharedFund fund = sharedFundRepository.findById(req.getFundId())
                .orElseThrow(() -> new RuntimeException("Fund not found"));
        User approver = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User recipient = req.getRecipientUserId() != null
                ? userRepository.findById(req.getRecipientUserId())
                .orElseThrow(() -> new RuntimeException("Recipient not found"))
                : null;

        Expense expense = Expense.builder()
                .fund(fund)
                .sourceType(req.getSourceType())
                .sourceId(req.getSourceId())
                .description(req.getDescription())
                .amount(req.getAmount())
                .recipientUser(recipient)
                .status("PENDING")
                .approvedBy(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        expenseRepository.save(expense);
        return mapToDTO(expense);
    }

    // =============== APPROVE ===============
    @Transactional
    public ExpenseResponseDTO approve(Long expenseId, String username) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        if (!"PENDING".equals(expense.getStatus())) {
            throw new RuntimeException("Expense already processed");
        }

        SharedFund fund = expense.getFund();
        if (fund.getBalance().compareTo(expense.getAmount()) < 0) {
            throw new RuntimeException("Insufficient fund balance");
        }

        fund.setBalance(fund.getBalance().subtract(expense.getAmount()));
        expense.setStatus("COMPLETED");
        expense.setExpenseDate(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setFundBalanceAfter(fund.getBalance());

        User approver = userRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("Approver not found"));
        expense.setApprovedBy(approver);

        sharedFundRepository.save(fund);
        expenseRepository.save(expense);

        return mapToDTO(expense);
    }

    // =============== GET ONE ===============
    public ExpenseResponseDTO getOne(Long id) {
        return expenseRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Expense not found"));
    }

    // =============== LIST BY GROUP ===============
    public List<ExpenseResponseDTO> getByGroup(Long groupId) {
        return expenseRepository.findByFund_Group_GroupId(groupId)
                .stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // =============== MAPPING ===============
    private ExpenseResponseDTO mapToDTO(Expense e) {
        return ExpenseResponseDTO.builder()
                .id(e.getId())
                .sourceType(e.getSourceType())
                .sourceId(e.getSourceId())
                .description(e.getDescription())
                .amount(e.getAmount())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .expenseDate(e.getExpenseDate())
                .fundBalanceAfter(e.getFundBalanceAfter())
                .approvedById(e.getApprovedBy() != null ? e.getApprovedBy().getUserId() : null)
                .recipientUserId(e.getRecipientUser() != null ? e.getRecipientUser().getUserId() : null)
                .build();
    }
}


