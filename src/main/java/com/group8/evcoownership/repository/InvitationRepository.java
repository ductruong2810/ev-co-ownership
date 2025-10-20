package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.Invitation;
import com.group8.evcoownership.enums.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    boolean existsByGroup_GroupIdAndInviteeEmailIgnoreCaseAndStatus(
            Long groupId, String email, InvitationStatus status
    );

    Page<Invitation> findByGroup_GroupId(Long groupId, Pageable pageable);

    long countByStatusAndExpiresAtBefore(InvitationStatus status, LocalDateTime time);
}