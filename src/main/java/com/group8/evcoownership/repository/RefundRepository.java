package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByDispute_Id(Long disputeId);
    
    Optional<Refund> findByTxnRef(String txnRef);
    
    Optional<Refund> findByProviderRefundRef(String providerRefundRef);
}

