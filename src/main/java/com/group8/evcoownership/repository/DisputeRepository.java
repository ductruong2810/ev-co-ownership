package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Dispute;
import com.group8.evcoownership.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);

    Page<Dispute> findByFund_FundId(Long fundId, Pageable pageable);

    Page<Dispute> findByFund_FundIdAndStatus(Long fundId, DisputeStatus status, Pageable pageable);
}
