package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByFund_Group_GroupId(Long groupId);

    @Query("""
    SELECT e FROM Expense e
    WHERE (:fundId IS NULL OR e.fund.fundId = :fundId)
      AND (:sourceType IS NULL OR e.sourceType = :sourceType)
      AND (:status IS NULL OR e.status = :status)
      AND (:approvedById IS NULL OR e.approvedBy.userId = :approvedById)
      AND (:recipientUserId IS NULL OR e.recipientUser.userId = :recipientUserId)
""")
    Page<Expense> findFiltered(
            @Param("fundId") Long fundId,
            @Param("sourceType") String sourceType,
            @Param("status") String status,
            @Param("approvedById") Long approvedById,
            @Param("recipientUserId") Long recipientUserId,
            Pageable pageable
    );

}
