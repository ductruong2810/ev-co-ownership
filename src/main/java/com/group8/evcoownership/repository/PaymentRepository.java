// repository/PaymentRepository.java
package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Payment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Dùng cho list trả về List (áp dụng limit/offset/sort qua Pageable)
    List<Payment> findAllBy(Pageable pageable);

    List<Payment> findAllByUser_UserId(Long userId, Pageable pageable);

    List<Payment> findAllByStatus(String status, Pageable pageable);

    List<Payment> findAllByPaymentType(String type, Pageable pageable);

    List<Payment> findAllByUser_UserIdAndStatusAndPaymentType(
            Long userId, String status, String type, Pageable pageable
    );


    // Nếu dùng VNPay, nên có lookup theo mã giao dịch (txnRef)
    Optional<Payment> findByTransactionCode(String transactionCode);

    boolean existsByTransactionCode(String transactionCode);
}
