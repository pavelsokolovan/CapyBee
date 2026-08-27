package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import com.capybee.server.domain.mission.Mission;
import com.capybee.server.domain.mission.MissionChildState;
import com.capybee.server.domain.mission.MissionCompletion;
import com.capybee.server.domain.mission.MissionInteraction;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.MissionChildStateRepository;
import com.capybee.server.repository.MissionCompletionRepository;
import com.capybee.server.repository.MissionInteractionRepository;
import com.capybee.server.repository.MissionRepository;
import com.capybee.server.web.dto.CreateMissionCompletionRequest;
import com.capybee.server.web.dto.MissionCompletionResponse;
import com.capybee.server.web.dto.MissionInteractionResponse;

/**
 * Covers the completion/skip/undo/delete flows that the pre-existing
 * MissionServiceTest (locale-ordering only) does not exercise.
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceCompletionTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionCompletionRepository missionCompletionRepository;

    @Mock
    private MissionInteractionRepository missionInteractionRepository;

    @Mock
    private MissionChildStateRepository missionChildStateRepository;

    @Mock
    private UserService userService;

    @Mock
    private ChildProfileService childProfileService;

    @Mock
    private OAuth2AuthenticationToken token;

    @InjectMocks
    private MissionService missionService;

    private UserAccount user;
    private UUID userId;
    private FamilyProfile profile;
    private UUID profileId;
    private Mission mission;
    private UUID missionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new UserAccount();
        user.setId(userId);

        profileId = UUID.randomUUID();
        profile = new FamilyProfile();
        setId(profile, profileId);

        missionId = UUID.randomUUID();
        mission = new Mission();
        mission.setId(missionId);
        mission.setActive(true);
    }

    private static void setId(FamilyProfile profile, UUID id) {
        try {
            var field = FamilyProfile.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(profile, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void completeMissionSucceedsForActiveMission() {
        CreateMissionCompletionRequest request = new CreateMissionCompletionRequest(null, "Done!");

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionCompletionRepository.save(any(MissionCompletion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(missionChildStateRepository.findByProfile_IdAndMission_Id(profileId, missionId))
                .thenReturn(Optional.empty());

        MissionCompletionResponse response = missionService.completeMission(token, missionId, request);

        assertEquals(missionId, response.missionId());
        assertEquals("Done!", response.note());
        verify(missionInteractionRepository, times(1)).save(any(MissionInteraction.class));
        verify(missionChildStateRepository, times(1)).save(any(MissionChildState.class));
    }

    @Test
    void completeMissionIsIdempotentForSameOwner() {
        UUID completionId = UUID.randomUUID();
        CreateMissionCompletionRequest request = new CreateMissionCompletionRequest(completionId, "note");

        MissionCompletion existing = new MissionCompletion();
        existing.setId(completionId);
        existing.setMission(mission);
        existing.setUserAccount(user);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionCompletionRepository.findById(completionId)).thenReturn(Optional.of(existing));

        MissionCompletionResponse response = missionService.completeMission(token, missionId, request);

        assertEquals(completionId, response.id());
        verify(missionCompletionRepository, never()).save(any(MissionCompletion.class));
    }

    @Test
    void completeMissionThrowsForbiddenWhenCompletionOwnedByAnotherAccount() {
        UUID completionId = UUID.randomUUID();
        CreateMissionCompletionRequest request = new CreateMissionCompletionRequest(completionId, "note");

        UserAccount otherUser = new UserAccount();
        otherUser.setId(UUID.randomUUID());

        MissionCompletion existing = new MissionCompletion();
        existing.setId(completionId);
        existing.setMission(mission);
        existing.setUserAccount(otherUser);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionCompletionRepository.findById(completionId)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> missionService.completeMission(token, missionId, request));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void completeMissionThrowsBadRequestWhenMissionInactive() {
        mission.setActive(false);
        CreateMissionCompletionRequest request = new CreateMissionCompletionRequest(null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> missionService.completeMission(token, missionId, request));
        assertEquals(400, ex.getStatusCode().value());
        verify(missionCompletionRepository, never()).save(any(MissionCompletion.class));
    }

    @Test
    void completeMissionThrowsNotFoundWhenMissionMissing() {
        CreateMissionCompletionRequest request = new CreateMissionCompletionRequest(null, null);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> missionService.completeMission(token, missionId, request));
    }

    @Test
    void skipMissionRecordsInteractionAndUpdatesState() {
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionInteractionRepository.save(any(MissionInteraction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(missionChildStateRepository.findByProfile_IdAndMission_Id(profileId, missionId))
                .thenReturn(Optional.empty());

        MissionInteractionResponse response = missionService.skipMission(token, missionId);

        assertEquals(missionId, response.missionId());
        assertEquals("skipped", response.action());
        verify(missionChildStateRepository, times(1)).save(any(MissionChildState.class));
    }

    @Test
    void skipMissionThrowsNotFoundWhenMissionMissing() {
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> missionService.skipMission(token, missionId));
    }

    @Test
    void undoSkipMissionClearsStateAndDeletesLatestSkip() {
        MissionChildState state = new MissionChildState();

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionInteractionRepository.save(any(MissionInteraction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(missionChildStateRepository.findByProfile_IdAndMission_Id(profileId, missionId))
                .thenReturn(Optional.of(state));
        when(missionInteractionRepository.findFirstByProfile_IdAndMission_IdAndActionOrderByCreatedAtDesc(
                profileId, missionId, "skipped")).thenReturn(Optional.of(new MissionInteraction()));

        MissionInteractionResponse response = missionService.undoSkipMission(token, missionId);

        assertEquals("undone", response.action());
        verify(missionInteractionRepository, times(1)).delete(any(MissionInteraction.class));
        verify(missionChildStateRepository, times(1)).save(state);
        assertNull(state.getLastActionedAt());
    }

    @Test
    void undoSkipMissionThrowsNotFoundWhenMissionMissing() {
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> missionService.undoSkipMission(token, missionId));
    }

    @Test
    void deleteMissionCompletionSucceedsForOwner() {
        UUID completionId = UUID.randomUUID();
        MissionCompletion completion = new MissionCompletion();
        completion.setId(completionId);
        completion.setUserAccount(user);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(missionCompletionRepository.findById(completionId)).thenReturn(Optional.of(completion));

        missionService.deleteMissionCompletion(token, completionId);

        verify(missionCompletionRepository, times(1)).delete(completion);
    }

    @Test
    void deleteMissionCompletionThrowsNotFoundWhenMissing() {
        UUID completionId = UUID.randomUUID();

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(missionCompletionRepository.findById(completionId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> missionService.deleteMissionCompletion(token, completionId));
    }

    @Test
    void deleteMissionCompletionThrowsForbiddenWhenNotOwner() {
        UUID completionId = UUID.randomUUID();
        UserAccount otherUser = new UserAccount();
        otherUser.setId(UUID.randomUUID());

        MissionCompletion completion = new MissionCompletion();
        completion.setId(completionId);
        completion.setUserAccount(otherUser);

        when(userService.getCurrentUser(token)).thenReturn(user);
        when(missionCompletionRepository.findById(completionId)).thenReturn(Optional.of(completion));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> missionService.deleteMissionCompletion(token, completionId));
        assertEquals(403, ex.getStatusCode().value());
        verify(missionCompletionRepository, never()).delete(any(MissionCompletion.class));
    }
}
