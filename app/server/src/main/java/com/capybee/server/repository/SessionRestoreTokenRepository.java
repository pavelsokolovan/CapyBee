package com.capybee.server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.capybee.server.domain.user.SessionRestoreToken;

public interface SessionRestoreTokenRepository extends JpaRepository<SessionRestoreToken, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<SessionRestoreToken> findByTokenHash(String tokenHash);
}
