package com.group8.evcoownership.repository;

import com.group8.evcoownership.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findByEmail(String email);

    void deleteByEmail(String email);
}
