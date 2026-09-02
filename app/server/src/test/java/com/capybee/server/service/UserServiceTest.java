package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private UserService userService;

    private OAuth2AuthenticationToken tokenFor(String subject, String email, String name, String picture) {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(),
                Map.of("sub", subject, "email", email, "name", name, "picture", picture),
                "sub");
        return new OAuth2AuthenticationToken(principal, List.of(), "google");
    }

    @Test
    void findOrCreateFromOAuth2ReturnsExistingUser() {
        UserAccount existing = new UserAccount();
        existing.setId(UUID.randomUUID());
        existing.setGoogleSubject("google-123");

        when(userAccountRepository.findByGoogleSubject("google-123")).thenReturn(Optional.of(existing));

        UserAccount result = userService.findOrCreateFromOAuth2(
                tokenFor("google-123", "a@b.com", "Alice", "http://pic"));

        assertSame(existing, result);
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void findOrCreateFromOAuth2CreatesNewUserWithMappedFields() {
        when(userAccountRepository.findByGoogleSubject("google-999")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount result = userService.findOrCreateFromOAuth2(
                tokenFor("google-999", "new@user.com", "New User", "http://avatar"));

        assertEquals("google-999", result.getGoogleSubject());
        assertEquals("new@user.com", result.getEmail());
        assertEquals("New User", result.getDisplayName());
        assertEquals("http://avatar", result.getAvatarUrl());
        assertEquals("en", result.getLocale());
        verify(userAccountRepository, times(1)).save(any(UserAccount.class));
    }

    @Test
    void getCurrentUserDelegatesToFindOrCreate() {
        when(userAccountRepository.findByGoogleSubject("google-1")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount result = userService.getCurrentUser(tokenFor("google-1", "e@e.com", "Name", "pic"));

        assertEquals("google-1", result.getGoogleSubject());
    }

    @Test
    void findOrCreateFromOAuth2TruncatesOverlongAvatarUrl() {
        when(userAccountRepository.findByGoogleSubject("google-child")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String overlongPicture = "http://pic/" + "a".repeat(3000);

        UserAccount result = userService.findOrCreateFromOAuth2(
                tokenFor("google-child", "child@family.com", "Child", overlongPicture));

        assertEquals(2048, result.getAvatarUrl().length());
        assertTrue(overlongPicture.startsWith(result.getAvatarUrl()));
    }
}
