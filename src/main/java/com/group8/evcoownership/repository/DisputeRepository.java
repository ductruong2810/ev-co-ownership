package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    Page<Dispute> findByFund_FundId(Long fundId, Pageable pageable);
    Page<Dispute> findByUser_UserId(Long userId, Pageable pageable);
    Page<Dispute> findByStatus(String status, Pageable pageable);
    Page<Dispute> findByFund_FundIdAndStatus(Long fundId, String status, Pageable pageable);
    Page<Dispute> findByUser_UserIdAndStatus(Long userId, String status, Pageable pageable);
}
