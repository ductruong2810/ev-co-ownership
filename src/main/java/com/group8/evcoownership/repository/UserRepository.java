package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.RoleName;
import com.group8.evcoownership.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Find users by role name
    List<User> findByRoleRoleName(RoleName roleName);

    List<User> findByStatus(UserStatus status);

    // Find all users who belong to a given ownership group
    @Query("select os.user from OwnershipShare os where os.group.groupId = :groupId")
    List<User> findUsersByGroupId(@Param("groupId") Long groupId);
}

