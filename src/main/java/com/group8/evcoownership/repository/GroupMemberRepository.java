package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
}
