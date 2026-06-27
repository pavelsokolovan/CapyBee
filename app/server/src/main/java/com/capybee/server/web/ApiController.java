package com.capybee.server.web;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.service.CheckInService;
import com.capybee.server.service.UserService;
import com.capybee.server.web.dto.CheckInResponse;
import com.capybee.server.web.dto.CreateCheckInRequest;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final UserService userService;
    private final CheckInService checkInService;

    public ApiController(UserService userService, CheckInService checkInService) {
        this.userService = userService;
        this.checkInService = checkInService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/auth-status")
    public Map<String, Object> authStatus(Authentication authentication) {
        if (authentication == null || !(authentication instanceof OAuth2AuthenticationToken)) {
            return Map.of("authenticated", false);
        }

        try {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            UserAccount user = userService.getCurrentUser(oauth2Token);
            return Map.of(
                    "authenticated", true,
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "displayName", user.getDisplayName(),
                    "avatarUrl", user.getAvatarUrl());
        } catch (Exception e) {
            // Log the error but don't fail the request
            System.err.println("Error getting auth status: " + e.getMessage());
            e.printStackTrace();
            return Map.of("authenticated", false);
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null) {
            return Map.of("authenticated", false);
        }

        if (authentication instanceof OAuth2AuthenticationToken oauth2AuthenticationToken) {
            UserAccount user = userService.getCurrentUser(oauth2AuthenticationToken);
            return Map.of(
                    "authenticated", true,
                    "id", user.getId(),
                    "email", user.getEmail(),
                    "displayName", user.getDisplayName(),
                    "avatarUrl", user.getAvatarUrl());
        }

        return Map.of(
                "authenticated", true,
                "name", authentication.getName());
    }

    @PostMapping("/check-ins")
    public CheckInResponse createCheckIn(
            Authentication authentication,
            @RequestBody CreateCheckInRequest request) {
        return checkInService.createCheckIn(requireOAuth2(authentication), request);
    }

    @GetMapping("/check-ins")
    public List<CheckInResponse> getMyCheckIns(Authentication authentication) {
        return checkInService.getMyRecentCheckIns(requireOAuth2(authentication));
    }

    private OAuth2AuthenticationToken requireOAuth2(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            throw new IllegalStateException("Not authenticated with OAuth2");
        }
        return oauth2Token;
    }
}
