package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Voting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VotingRepository extends JpaRepository<Voting, Long> {
    List<Voting> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    List<Voting> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);

    @Query("SELECT v FROM Voting v WHERE v.groupId = :groupId AND v.status = :status ORDER BY v.createdAt DESC")
    List<Voting> findActiveVotingsByGroupId(@Param("groupId") Long groupId, @Param("status") String status);
}
