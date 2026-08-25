package com.capybee.server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.user.SessionRestoreToken;

public interface SessionRestoreTokenRepository extends JpaRepository<SessionRestoreToken, UUID> {

    Optional<SessionRestoreToken> findByTokenHash(String tokenHash);
}
