package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    
    // Find users by role name
    List<User> findByRoleRoleName(RoleName roleName);
}

