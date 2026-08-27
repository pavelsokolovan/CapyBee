package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capybee.server.domain.user.SessionRestoreToken;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.SessionRestoreTokenRepository;

@ExtendWith(MockitoExtension.class)
class SessionRestoreTokenServiceTest {

    @Mock
    private SessionRestoreTokenRepository repository;

    @InjectMocks
    private SessionRestoreTokenService sessionRestoreTokenService;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        user = new UserAccount();
        user.setEmail("child@example.com");
    }

    @Test
    void issueTokenSavesHashedTokenWithFutureExpiry() {
        when(repository.save(any(SessionRestoreToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = sessionRestoreTokenService.issueToken(user);

        assertNotNull(rawToken);
        verify(repository).save(argThat(entity ->
                entity.getUser() == user
                        && entity.getTokenHash() != null
                        && !entity.getTokenHash().equals(rawToken)
                        && entity.getExpiresAt().isAfter(Instant.now())));
    }

    @Test
    void validateReturnsUserForValidNonExpiredToken() {
        String rawToken = "raw-token-value";
        SessionRestoreToken stored = new SessionRestoreToken();
        stored.setUser(user);
        stored.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        when(repository.findByTokenHash(any(String.class))).thenReturn(Optional.of(stored));

        Optional<UserAccount> result = sessionRestoreTokenService.validate(rawToken);

        assertTrue(result.isPresent());
        assertSame(user, result.get());
        assertNotNull(stored.getLastUsedAt());
    }

    @Test
    void validateReturnsEmptyForExpiredToken() {
        SessionRestoreToken stored = new SessionRestoreToken();
        stored.setUser(user);
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(repository.findByTokenHash(any(String.class))).thenReturn(Optional.of(stored));

        assertTrue(sessionRestoreTokenService.validate("expired-token").isEmpty());
    }

    @Test
    void validateReturnsEmptyForUnknownToken() {
        when(repository.findByTokenHash(any(String.class))).thenReturn(Optional.empty());

        assertTrue(sessionRestoreTokenService.validate("unknown-token").isEmpty());
    }

    @Test
    void validateReturnsEmptyForBlankOrNullToken() {
        assertTrue(sessionRestoreTokenService.validate(null).isEmpty());
        assertTrue(sessionRestoreTokenService.validate("  ").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void rotateDeletesOldTokenAndIssuesNew() {
        SessionRestoreToken old = new SessionRestoreToken();
        when(repository.findByTokenHash(any(String.class))).thenReturn(Optional.of(old));
        when(repository.save(any(SessionRestoreToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String newToken = sessionRestoreTokenService.rotate("old-raw-token", user);

        assertNotNull(newToken);
        verify(repository).delete(old);
        verify(repository).save(any(SessionRestoreToken.class));
    }

    @Test
    void rotateWithNullOldTokenOnlyIssuesNew() {
        when(repository.save(any(SessionRestoreToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(sessionRestoreTokenService.rotate(null, user));

        verify(repository, never()).delete(any(SessionRestoreToken.class));
        verify(repository, never()).findByTokenHash(any(String.class));
    }

    @Test
    void revokeDeletesMatchingToken() {
        SessionRestoreToken stored = new SessionRestoreToken();
        when(repository.findByTokenHash(any(String.class))).thenReturn(Optional.of(stored));

        sessionRestoreTokenService.revoke("some-token");

        verify(repository).delete(stored);
    }

    @Test
    void revokeNoOpsOnBlankOrNullToken() {
        sessionRestoreTokenService.revoke(null);
        sessionRestoreTokenService.revoke("");

        verifyNoInteractions(repository);
    }
}
