package com.capybee.server.web;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.service.SessionRestoreTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Restores an authenticated session from a localStorage-backed token when the
 * session cookie itself was dropped by the client (e.g. Android PWA killed
 * from recents clears cookies independent of the cookie's own Max-Age).
 */
@RestController
@RequestMapping("/api/session")
public class SessionRestoreController {

    private final SessionRestoreTokenService tokenService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public SessionRestoreController(SessionRestoreTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restore(@RequestBody TokenRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        Optional<UserAccount> userOpt = tokenService.validate(body.token());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("authenticated", false));
        }

        UserAccount user = userOpt.get();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        OAuth2User oauth2User = new DefaultOAuth2User(authorities,
                Map.of("sub", user.getGoogleSubject(), "email", user.getEmail(), "name", user.getDisplayName()),
                "sub");
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(oauth2User, authorities, "google");

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        String newToken = tokenService.rotate(body.token(), user);

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "id", user.getId(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                "sessionToken", newToken));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(@RequestBody TokenRequest body) {
        tokenService.revoke(body.token());
        return ResponseEntity.noContent().build();
    }

    public record TokenRequest(String token) {
    }
}
