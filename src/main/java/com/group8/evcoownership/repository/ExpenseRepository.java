package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Expense;
import com.group8.evcoownership.enums.FundType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                ORDER BY 
                  CASE 
                    WHEN e.status = 'PENDING' THEN 1
                    WHEN e.status = 'APPROVED' THEN 2
                    WHEN e.status = 'COMPLETED' THEN 3
                    WHEN e.status = 'REJECTED' THEN 4
                    ELSE 5
                  END,
                  e.createdAt DESC
            """)
    Page<Expense> findAllFiltered(
            @Param("fundId") Long fundId,
            @Param("sourceType") String sourceType,
            @Param("status") String status,
            @Param("approvedById") Long approvedById,
            @Param("recipientUserId") Long recipientUserId,
            Pageable pageable
    );


    /**
     * new dto for LedgerRowDto
     */
    // 1) Theo group + khoảng thời gian (order by mới nhất)
    List<Expense> findByFund_Group_GroupIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            Long groupId, LocalDateTime from, LocalDateTime to
    );

    // 2) Theo group + fundType + khoảng thời gian (order by mới nhất)
    List<Expense> findByFund_Group_GroupIdAndFund_FundTypeAndExpenseDateBetweenOrderByExpenseDateDesc(
            Long groupId, FundType fundType, LocalDateTime from, LocalDateTime to
    );

    /**
     * sum approvedOut
     * cho ham LedgerRow
     */
    @Query("""
               select coalesce(sum(e.amount), 0)
               from Expense e
               where e.fund.group.groupId = :groupId
                 and (:fundType is null or e.fund.fundType = :fundType)
                 and e.status = 'COMPLETED'
                 and (:from is null or e.expenseDate >= :from)
                 and (:to   is null or e.expenseDate <  :to)
            """)
    BigDecimal sumApprovedOut(Long groupId,
                              @Param("fundType") FundType fundType,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

}
