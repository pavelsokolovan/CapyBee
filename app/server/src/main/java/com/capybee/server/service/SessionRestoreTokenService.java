package com.capybee.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capybee.server.domain.user.SessionRestoreToken;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.SessionRestoreTokenRepository;

/**
 * Issues and validates the localStorage-backed restore token used to
 * re-establish a session when the browser/PWA cookie jar has been cleared
 * (e.g. Android dropping the app from recents), independent of the
 * server-side session lifetime.
 */
@Service
public class SessionRestoreTokenService {

    private static final Duration TTL = Duration.ofDays(30);

    private final SessionRestoreTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public SessionRestoreTokenService(SessionRestoreTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String issueToken(UserAccount user) {
        String rawToken = generateRawToken();
        SessionRestoreToken entity = new SessionRestoreToken();
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(Instant.now().plus(TTL));
        repository.save(entity);
        return rawToken;
    }

    @Transactional
    public Optional<UserAccount> validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(rawToken))
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .map(t -> {
                    t.setLastUsedAt(Instant.now());
                    return t.getUser();
                });
    }

    /** Rotates the token used for restore, keeping the 30-day window sliding. */
    @Transactional
    public String rotate(String oldRawToken, UserAccount user) {
        if (oldRawToken != null) {
            repository.findByTokenHash(hash(oldRawToken)).ifPresent(repository::delete);
        }
        return issueToken(user);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(repository::delete);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
