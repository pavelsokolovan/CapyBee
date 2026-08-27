package com.capybee.server.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
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

import com.capybee.server.domain.friendship.FriendshipEntry;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.FriendshipEntryRepository;
import com.capybee.server.web.dto.CreateFriendshipRequest;
import com.capybee.server.web.dto.FriendshipResponse;
import com.capybee.server.web.dto.UpdateFriendshipRequest;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipEntryRepository friendshipEntryRepository;

    @Mock
    private ChildProfileService childProfileService;

    @Mock
    private OAuth2AuthenticationToken token;

    @InjectMocks
    private FriendshipService friendshipService;

    private FamilyProfile profile;
    private UUID profileId;

    @BeforeEach
    void setUp() {
        profileId = UUID.randomUUID();
        profile = new FamilyProfile();
        setId(profile, profileId);
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
    void createFriendshipSucceedsWithValidRequest() {
        CreateFriendshipRequest request = new CreateFriendshipRequest(null, "Alex", "talked", "met at school");

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.save(any(FriendshipEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FriendshipResponse response = friendshipService.createFriendship(token, request);

        assertEquals("Alex", response.personLabel());
        assertEquals("talked", response.stage());
        assertEquals("met at school", response.note());
    }

    @Test
    void createFriendshipIsIdempotentWhenIdAlreadyExists() {
        UUID entryId = UUID.randomUUID();
        CreateFriendshipRequest request = new CreateFriendshipRequest(entryId, "Alex", "talked", null);

        FriendshipEntry existing = new FriendshipEntry();
        existing.setId(entryId);
        existing.setPersonLabel("Alex");
        existing.setStage("talked");

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.findByIdAndProfile_Id(entryId, profileId)).thenReturn(Optional.of(existing));

        FriendshipResponse response = friendshipService.createFriendship(token, request);

        assertEquals(entryId, response.id());
        verify(friendshipEntryRepository, never()).save(any(FriendshipEntry.class));
    }

    @Test
    void createFriendshipThrowsBadRequestWhenPersonLabelMissing() {
        CreateFriendshipRequest request = new CreateFriendshipRequest(null, "  ", "talked", null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);

        assertThrows(ResponseStatusException.class, () -> friendshipService.createFriendship(token, request));
    }

    @Test
    void createFriendshipThrowsBadRequestWhenPersonLabelTooLong() {
        CreateFriendshipRequest request = new CreateFriendshipRequest(null, "x".repeat(121), "talked", null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);

        assertThrows(ResponseStatusException.class, () -> friendshipService.createFriendship(token, request));
    }

    @Test
    void createFriendshipThrowsBadRequestForUnsupportedStage() {
        CreateFriendshipRequest request = new CreateFriendshipRequest(null, "Alex", "best_friends_forever", null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);

        assertThrows(ResponseStatusException.class, () -> friendshipService.createFriendship(token, request));
    }

    @Test
    void listFriendshipsReturnsMappedEntries() {
        FriendshipEntry entry = new FriendshipEntry();
        entry.setId(UUID.randomUUID());
        entry.setPersonLabel("Alex");
        entry.setStage("noticed");

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.findAllByProfile_IdOrderByCreatedAtAsc(profileId)).thenReturn(List.of(entry));

        List<FriendshipResponse> result = friendshipService.listFriendships(token);

        assertEquals(1, result.size());
        assertEquals("Alex", result.get(0).personLabel());
    }

    @Test
    void updateFriendshipUpdatesStageAndNote() {
        UUID entryId = UUID.randomUUID();
        FriendshipEntry entry = new FriendshipEntry();
        entry.setId(entryId);
        entry.setPersonLabel("Alex");
        entry.setStage("noticed");

        UpdateFriendshipRequest request = new UpdateFriendshipRequest("talked", "updated note");

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.findByIdAndProfile_Id(entryId, profileId)).thenReturn(Optional.of(entry));
        when(friendshipEntryRepository.save(entry)).thenReturn(entry);

        FriendshipResponse response = friendshipService.updateFriendship(token, entryId, request);

        assertEquals("talked", response.stage());
        assertEquals("updated note", response.note());
    }

    @Test
    void updateFriendshipThrowsNotFoundWhenMissing() {
        UUID entryId = UUID.randomUUID();
        UpdateFriendshipRequest request = new UpdateFriendshipRequest("talked", null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.findByIdAndProfile_Id(entryId, profileId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> friendshipService.updateFriendship(token, entryId, request));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void deleteFriendshipDeletesExistingEntry() {
        UUID entryId = UUID.randomUUID();
        FriendshipEntry entry = new FriendshipEntry();
        entry.setId(entryId);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.findByIdAndProfile_Id(entryId, profileId)).thenReturn(Optional.of(entry));

        friendshipService.deleteFriendship(token, entryId);

        verify(friendshipEntryRepository, times(1)).delete(entry);
    }

    @Test
    void deleteFriendshipThrowsNotFoundWhenMissing() {
        UUID entryId = UUID.randomUUID();

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(friendshipEntryRepository.findByIdAndProfile_Id(entryId, profileId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> friendshipService.deleteFriendship(token, entryId));
        verify(friendshipEntryRepository, never()).delete(any(FriendshipEntry.class));
    }
}
