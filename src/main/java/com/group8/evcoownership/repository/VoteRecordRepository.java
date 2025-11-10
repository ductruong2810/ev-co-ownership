package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.VoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRecordRepository extends JpaRepository<VoteRecord, Long> {
    Optional<VoteRecord> findByVotingIdAndUserId(Long votingId, Long userId);

    List<VoteRecord> findByVotingId(Long votingId);

    @Query("SELECT COUNT(vr) FROM VoteRecord vr WHERE vr.votingId = :votingId")
    long countByVotingId(@Param("votingId") Long votingId);

    boolean existsByVotingIdAndUserId(Long votingId, Long userId);
}
