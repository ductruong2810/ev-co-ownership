package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.ExpenseResponseDTO;
import com.group8.evcoownership.dto.ExpenseCreateRequestDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final IncidentRepository incidentRepository;

    // =============== CREATE ===============
    @Transactional
    public ExpenseResponseDTO create(ExpenseCreateRequestDTO req, String username) {
        SharedFund fund = sharedFundRepository.findById(req.getFundId())
                .orElseThrow(() -> new EntityNotFoundException("Fund not found"));

        User approver = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        User recipient = null;

        // 🔍 Nếu sourceType = INCIDENT → lấy user từ Incident
        if ("INCIDENT".equalsIgnoreCase(req.getSourceType())) {
            Incident incident = incidentRepository.findById(req.getSourceId())
                    .orElseThrow(() -> new EntityNotFoundException("Incident not found"));
            recipient = incident.getBooking().getUser(); // hoặc incident.getReportedBy() tuỳ cấu trúc entity
        }

        // 🔧 Nếu là Maintenance thì để recipient = null

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
                .orElseThrow(() -> new EntityNotFoundException("Expense not found"));
        if (!"PENDING".equals(expense.getStatus())) {
            throw new IllegalStateException("Expense already processed");
        }

        SharedFund fund = expense.getFund();
        if (fund.getBalance().compareTo(expense.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient fund balance");
        }

        fund.setBalance(fund.getBalance().subtract(expense.getAmount()));
        expense.setStatus("COMPLETED");
        expense.setExpenseDate(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setFundBalanceAfter(fund.getBalance());

        User approver = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("Approver not found"));
        expense.setApprovedBy(approver);

        sharedFundRepository.save(fund);
        expenseRepository.save(expense);

        return mapToDTO(expense);
    }

    // =============== GET ALL ===============
    public Page<ExpenseResponseDTO> getAll(
            Long fundId,
            String sourceType,
            String status,
            Long approvedById,
            Long recipientUserId,
            Pageable pageable
    ) {
        Page<Expense> page = expenseRepository.findFiltered(
                fundId, sourceType, status, approvedById, recipientUserId, pageable
        );
        return page.map(this::mapToDTO);
    }



    // =============== GET ONE ===============
    public ExpenseResponseDTO getOne(Long id) {
        return expenseRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new EntityNotFoundException("Expense not found"));
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


