package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Year;
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
import org.springframework.web.server.ResponseStatusException;

import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.FamilyProfileRepository;
import com.capybee.server.web.dto.ChildProfileResponse;
import com.capybee.server.web.dto.CreateChildProfileRequest;
import com.capybee.server.web.dto.UpdateChildProfileRequest;

@ExtendWith(MockitoExtension.class)
class ChildProfileServiceTest {

    @Mock
    private FamilyProfileRepository familyProfileRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChildProfileService childProfileService;

    private OAuth2AuthenticationToken token;
    private UserAccount user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserAccount();
        user.setId(userId);

        DefaultOAuth2User principal = new DefaultOAuth2User(List.of(), Map.of("sub", "subject"), "sub");
        token = new OAuth2AuthenticationToken(principal, List.of(), "google");
    }

    @Test
    void createProfileSucceedsWithValidRequest() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("Kiddo", 2014, "PL", "seed-1");

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());
        when(familyProfileRepository.save(any(FamilyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChildProfileResponse response = childProfileService.createProfile(token, request);

        assertEquals("Kiddo", response.nickname());
        assertEquals(2014, response.birthYear());
        assertEquals("pl", response.preferredLocale());
        assertEquals("seed-1", response.avatarSeed());
        assertTrue(response.active());
    }

    @Test
    void createProfileThrowsConflictWhenProfileAlreadyExists() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("Kiddo", null, null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.of(new FamilyProfile()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> childProfileService.createProfile(token, request));
        assertEquals(409, ex.getStatusCode().value());
        verify(familyProfileRepository, never()).save(any(FamilyProfile.class));
    }

    @Test
    void createProfileThrowsBadRequestWhenNicknameMissing() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("   ", null, null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> childProfileService.createProfile(token, request));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void createProfileThrowsBadRequestWhenNicknameTooLong() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("x".repeat(81), null, null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> childProfileService.createProfile(token, request));
    }

    @Test
    void createProfileThrowsBadRequestWhenBirthYearOutOfRange() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("Kiddo", Year.now().getValue() + 1, null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> childProfileService.createProfile(token, request));
    }

    @Test
    void createProfileThrowsBadRequestForUnsupportedLocale() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("Kiddo", null, "fr", null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> childProfileService.createProfile(token, request));
    }

    @Test
    void createProfileDefaultsLocaleToEnWhenBlank() {
        CreateChildProfileRequest request = new CreateChildProfileRequest("Kiddo", null, "  ", null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());
        when(familyProfileRepository.save(any(FamilyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChildProfileResponse response = childProfileService.createProfile(token, request);

        assertEquals("en", response.preferredLocale());
    }

    @Test
    void updateProfileUpdatesOnlyProvidedFields() {
        FamilyProfile existing = new FamilyProfile();
        existing.setParentUser(user);
        existing.setNickname("Old Name");
        existing.setPreferredLocale("en");

        UpdateChildProfileRequest request = new UpdateChildProfileRequest("New Name", null, null, null, null, true);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.of(existing));
        when(familyProfileRepository.save(any(FamilyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChildProfileResponse response = childProfileService.updateProfile(token, request);

        assertEquals("New Name", response.nickname());
        assertEquals("en", response.preferredLocale());
        assertTrue(response.hasSeenOnboarding());
    }

    @Test
    void updateProfileThrowsNotFoundWhenProfileMissing() {
        UpdateChildProfileRequest request = new UpdateChildProfileRequest("New Name", null, null, null, null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> childProfileService.updateProfile(token, request));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getMyProfileThrowsNotFoundWhenProfileMissing() {
        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> childProfileService.getMyProfile(token));
    }

    @Test
    void getMyProfileEntityThrowsBadRequestWhenProfileMissing() {
        when(userService.getCurrentUser(token)).thenReturn(user);
        when(familyProfileRepository.findByParentUser_Id(userId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> childProfileService.getMyProfileEntity(token));
        assertEquals(400, ex.getStatusCode().value());
    }
}
