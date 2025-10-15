// repository/PaymentRepository.java
package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Payment;
import com.group8.evcoownership.enums.PaymentStatus;
import com.group8.evcoownership.enums.PaymentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Dùng cho list trả về List (áp dụng limit/offset/sort qua Pageable)
    List<Payment> findAllBy(Pageable pageable);

    List<Payment> findAllByUser_UserId(Long userId, Pageable pageable);

    List<Payment> findAllByStatus(PaymentStatus status, Pageable pageable);

    List<Payment> findAllByPaymentType(PaymentType type, Pageable pageable);

    List<Payment> findAllByUser_UserIdAndStatusAndPaymentType(
            Long userId, PaymentStatus status, PaymentType type, Pageable pageable
    );


    // Nếu dùng VNPay, nên có lookup theo mã giao dịch (txnRef)
    Optional<Payment> findByTransactionCode(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);
}
