package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    List<Expense> findBySourceType(String sourceType);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.sourceType = :sourceType AND e.sourceId = :sourceId")
    java.math.BigDecimal getTotalAmountBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);
}
