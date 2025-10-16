package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Page<Expense> findByFund_FundId(Long fundId, Pageable pageable);
    Page<Expense> findByFund_FundIdAndSourceType(Long fundId, String sourceType, Pageable pageable);
    Page<Expense> findByExpenseDateBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<Expense> findByFund_FundIdAndExpenseDateBetween(Long fundId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
