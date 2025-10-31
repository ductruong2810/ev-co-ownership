package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByFund_Group_GroupId(Long groupId);
}
