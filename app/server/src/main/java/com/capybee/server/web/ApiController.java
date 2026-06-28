package com.capybee.server.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.service.ChildProfileService;
import com.capybee.server.service.CheckInService;
import com.capybee.server.service.FriendshipService;
import com.capybee.server.service.MemoryService;
import com.capybee.server.service.MissionService;
import com.capybee.server.service.UserService;
import com.capybee.server.web.dto.CheckInResponse;
import com.capybee.server.web.dto.ChildProfileResponse;
import com.capybee.server.web.dto.CreateChildProfileRequest;
import com.capybee.server.web.dto.CreateCheckInRequest;
import com.capybee.server.web.dto.CreateFriendshipRequest;
import com.capybee.server.web.dto.CreateMemoryRequest;
import com.capybee.server.web.dto.CreateMissionCompletionRequest;
import com.capybee.server.web.dto.FriendshipResponse;
import com.capybee.server.web.dto.MemoryResponse;
import com.capybee.server.web.dto.MissionCompletionResponse;
import com.capybee.server.web.dto.MissionResponse;
import com.capybee.server.web.dto.UpdateChildProfileRequest;
import com.capybee.server.web.dto.UpdateFriendshipRequest;
import com.capybee.server.web.dto.UpdateMemoryRequest;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final UserService userService;
    private final CheckInService checkInService;
    private final ChildProfileService childProfileService;
    private final MissionService missionService;
    private final FriendshipService friendshipService;
    private final MemoryService memoryService;

    public ApiController(UserService userService,
            CheckInService checkInService,
            ChildProfileService childProfileService,
            MissionService missionService,
            FriendshipService friendshipService,
            MemoryService memoryService) {
        this.userService = userService;
        this.checkInService = checkInService;
        this.childProfileService = childProfileService;
        this.missionService = missionService;
        this.friendshipService = friendshipService;
        this.memoryService = memoryService;
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

    @PostMapping("/child-profile")
    @ResponseStatus(HttpStatus.CREATED)
    public ChildProfileResponse createChildProfile(
            Authentication authentication,
            @RequestBody CreateChildProfileRequest request) {
        return childProfileService.createProfile(requireOAuth2(authentication), request);
    }

    @GetMapping("/child-profile")
    public ChildProfileResponse getChildProfile(Authentication authentication) {
        return childProfileService.getMyProfile(requireOAuth2(authentication));
    }

    @PatchMapping("/child-profile")
    public ChildProfileResponse updateChildProfile(
            Authentication authentication,
            @RequestBody UpdateChildProfileRequest request) {
        return childProfileService.updateProfile(requireOAuth2(authentication), request);
    }

    @GetMapping("/missions")
    public List<MissionResponse> getMissions(@RequestParam(required = false) Boolean active) {
        return missionService.getMissions(active);
    }

    @PostMapping("/missions/{missionId}/completions")
    @ResponseStatus(HttpStatus.CREATED)
    public MissionCompletionResponse completeMission(
            Authentication authentication,
            @PathVariable UUID missionId,
            @RequestBody CreateMissionCompletionRequest request) {
        return missionService.completeMission(requireOAuth2(authentication), missionId, request);
    }

    @GetMapping("/missions/completions")
    public List<MissionCompletionResponse> getMissionCompletions(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before) {
        return missionService.getMissionCompletions(requireOAuth2(authentication), before);
    }

    @PostMapping("/friendships")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendshipResponse createFriendship(
            Authentication authentication,
            @RequestBody CreateFriendshipRequest request) {
        return friendshipService.createFriendship(requireOAuth2(authentication), request);
    }

    @GetMapping("/friendships")
    public List<FriendshipResponse> listFriendships(Authentication authentication) {
        return friendshipService.listFriendships(requireOAuth2(authentication));
    }

    @PatchMapping("/friendships/{entryId}")
    public FriendshipResponse updateFriendship(
            Authentication authentication,
            @PathVariable UUID entryId,
            @RequestBody UpdateFriendshipRequest request) {
        return friendshipService.updateFriendship(requireOAuth2(authentication), entryId, request);
    }

    @DeleteMapping("/friendships/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriendship(Authentication authentication, @PathVariable UUID entryId) {
        friendshipService.deleteFriendship(requireOAuth2(authentication), entryId);
    }

    @PostMapping("/memories")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse createMemory(
            Authentication authentication,
            @RequestBody CreateMemoryRequest request) {
        return memoryService.createMemory(requireOAuth2(authentication), request);
    }

    @GetMapping("/memories")
    public List<MemoryResponse> listMemories(
            Authentication authentication,
            @RequestParam(required = false) String worldType) {
        return memoryService.listMemories(requireOAuth2(authentication), worldType);
    }

    @PatchMapping("/memories/{memoryId}")
    public MemoryResponse updateMemory(
            Authentication authentication,
            @PathVariable UUID memoryId,
            @RequestBody UpdateMemoryRequest request) {
        return memoryService.updateMemory(requireOAuth2(authentication), memoryId, request);
    }

    @DeleteMapping("/memories/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMemory(Authentication authentication, @PathVariable UUID memoryId) {
        memoryService.deleteMemory(requireOAuth2(authentication), memoryId);
    }

    private OAuth2AuthenticationToken requireOAuth2(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            throw new IllegalStateException("Not authenticated with OAuth2");
        }
        return oauth2Token;
    }
}
