// repository/PaymentRepository.java
package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {


    // Dùng cho list trả về List (áp dụng limit/offset/sort qua Pageable)
    List<Payment> findAllBy(Pageable pageable);

    List<Payment> findAllByPayer_UserId(Long userId, Pageable pageable);

    List<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);

    List<Payment> findAllByPaymentType(PaymentType type, Pageable pageable);

    List<Payment> findAllByPayer_UserIdAndStatusAndPaymentType(
            Long userId, PaymentStatus status, PaymentType type, Pageable pageable
    );


    // Nếu dùng VNPay, nên có lookup theo mã giao dịch (txnRef)
    Optional<Payment> findByTransactionCode(String transactionCode);

    List<Payment> findAllByTransactionCodeOrderByIdDesc(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);

    Optional<Payment> findTopByPayer_UserIdAndFund_Group_GroupIdAndPaymentTypeOrderByPaymentDateDesc(
            Long userId, Long groupId, PaymentType paymentType);

    Optional<Payment> findTopByPayer_UserIdAndFund_Group_GroupIdAndPaymentTypeAndStatusOrderByPaymentDateDesc(
            Long userId, Long groupId, PaymentType paymentType, PaymentStatus status);

    /**
     * new for ledgerRowDTO
     */
    List<Payment> findByFund_Group_GroupIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
            Long groupId, PaymentStatus status, LocalDateTime from, LocalDateTime to);

    List<Payment> findByFund_Group_GroupIdAndFund_FundTypeAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
            Long groupId, FundType fundType, PaymentStatus status, LocalDateTime from, LocalDateTime to);


    /**
     * ham lay sumCompletedIn
     * cho ham getLedgerSummary
     */
    @Query("""
   select coalesce(sum(p.amount), 0)
   from Payment p
   where p.fund.group.groupId = :groupId
     and (:fundType is null or p.fund.fundType = :fundType)
     and p.status = com.group8.evcoownership.enums.PaymentStatus.COMPLETED
     and (:from is null or p.paidAt >= :from)
     and (:to   is null or p.paidAt <  :to)
""")
    BigDecimal sumCompletedIn(Long groupId,
                              @Param("fundType") FundType fundType,
                              @Param("from") LocalDateTime from,
                              @Param("to")   LocalDateTime to);


    /**
     * Repository for Payment History
     */

    @Query("""
  SELECT p FROM Payment p
  JOIN p.fund f
  JOIN f.group g
  WHERE p.payer.userId = :userId
    AND p.status = com.group8.evcoownership.enums.PaymentStatus.COMPLETED
    AND (:fromAt IS NULL OR p.paymentDate >= :fromAt)
    AND (:toAt   IS NULL OR p.paymentDate <= :toAt)
""")
    Page<Payment> searchUserHistoryCompleted(
            @Param("userId") Long userId,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt")   LocalDateTime toAt,
            Pageable pageable
    );

    @Query("""
  SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
  JOIN p.fund f
  JOIN f.group g
  WHERE p.payer.userId = :userId
    AND p.status = com.group8.evcoownership.enums.PaymentStatus.COMPLETED
    AND (:fromAt IS NULL OR p.paymentDate >= :fromAt)
    AND (:toAt   IS NULL OR p.paymentDate <= :toAt)
""")
    BigDecimal sumUserCompleted(
            @Param("userId") Long userId,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt")   LocalDateTime toAt
    );


}
