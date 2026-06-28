package com.capybee.server.service;

import java.util.Optional;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.UserAccountRepository;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;

    public UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserAccount findOrCreateFromOAuth2(OAuth2AuthenticationToken token) {
        String googleSubject = token.getPrincipal().getName();
        String email = (String) token.getPrincipal().getAttributes().get("email");
        String displayName = (String) token.getPrincipal().getAttributes().get("name");
        String avatarUrl = (String) token.getPrincipal().getAttributes().get("picture");

        // First, try to find existing user
        Optional<UserAccount> existing = userAccountRepository.findByGoogleSubject(googleSubject);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new user only if not found
        UserAccount newUser = new UserAccount();
        newUser.setGoogleSubject(googleSubject);
        newUser.setEmail(email);
        newUser.setDisplayName(displayName);
        newUser.setAvatarUrl(avatarUrl);
        newUser.setLocale("en");
        return userAccountRepository.save(newUser);
    }

    public UserAccount getCurrentUser(OAuth2AuthenticationToken token) {
        return findOrCreateFromOAuth2(token);
    }
}
