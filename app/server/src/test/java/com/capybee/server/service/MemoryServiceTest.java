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

import com.capybee.server.domain.memory.MemoryEntry;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.MemoryEntryRepository;
import com.capybee.server.web.dto.CreateMemoryRequest;
import com.capybee.server.web.dto.MemoryResponse;
import com.capybee.server.web.dto.UpdateMemoryRequest;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private MemoryEntryRepository memoryEntryRepository;

    @Mock
    private ChildProfileService childProfileService;

    @Mock
    private OAuth2AuthenticationToken token;

    @InjectMocks
    private MemoryService memoryService;

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
    void createMemorySucceedsWithTextContent() {
        CreateMemoryRequest request = new CreateMemoryRequest(null, "old_world", "Title", "Some story", null, true);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.save(any(MemoryEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemoryResponse response = memoryService.createMemory(token, request);

        assertEquals("old_world", response.worldType());
        assertEquals("Some story", response.textContent());
        assertTrue(response.isFavorite());
    }

    @Test
    void createMemoryIsIdempotentWhenIdAlreadyExists() {
        UUID memoryId = UUID.randomUUID();
        CreateMemoryRequest request = new CreateMemoryRequest(memoryId, "new_world", null, "Text", null, null);

        MemoryEntry existing = new MemoryEntry();
        existing.setId(memoryId);
        existing.setWorldType("new_world");
        existing.setTextContent("Text");

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findByIdAndProfile_Id(memoryId, profileId)).thenReturn(Optional.of(existing));

        MemoryResponse response = memoryService.createMemory(token, request);

        assertEquals(memoryId, response.id());
        verify(memoryEntryRepository, never()).save(any(MemoryEntry.class));
    }

    @Test
    void createMemoryThrowsBadRequestForUnsupportedWorldType() {
        CreateMemoryRequest request = new CreateMemoryRequest(null, "future_world", null, "Text", null, null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);

        assertThrows(ResponseStatusException.class, () -> memoryService.createMemory(token, request));
    }

    @Test
    void createMemoryThrowsBadRequestWhenNoTextOrMedia() {
        CreateMemoryRequest request = new CreateMemoryRequest(null, "old_world", "Title", "   ", "  ", null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);

        assertThrows(ResponseStatusException.class, () -> memoryService.createMemory(token, request));
        verify(memoryEntryRepository, never()).save(any(MemoryEntry.class));
    }

    @Test
    void listMemoriesWithoutWorldTypeFilterReturnsAll() {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID());
        entry.setWorldType("old_world");
        entry.setTextContent("Story");

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findTop20ByProfile_IdOrderByCreatedAtDesc(profileId)).thenReturn(List.of(entry));

        List<MemoryResponse> result = memoryService.listMemories(token, null);

        assertEquals(1, result.size());
        verify(memoryEntryRepository, never())
                .findTop20ByProfile_IdAndWorldTypeOrderByCreatedAtDesc(any(UUID.class), any(String.class));
    }

    @Test
    void listMemoriesWithWorldTypeFilterUsesFilteredQuery() {
        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findTop20ByProfile_IdAndWorldTypeOrderByCreatedAtDesc(profileId, "new_world"))
                .thenReturn(List.of());

        List<MemoryResponse> result = memoryService.listMemories(token, "new_world");

        assertTrue(result.isEmpty());
        verify(memoryEntryRepository, times(1))
                .findTop20ByProfile_IdAndWorldTypeOrderByCreatedAtDesc(profileId, "new_world");
    }

    @Test
    void updateMemoryUpdatesProvidedFields() {
        UUID memoryId = UUID.randomUUID();
        MemoryEntry entry = new MemoryEntry();
        entry.setId(memoryId);
        entry.setWorldType("old_world");
        entry.setTextContent("Old text");

        UpdateMemoryRequest request = new UpdateMemoryRequest(null, "New title", null, null, true);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findByIdAndProfile_Id(memoryId, profileId)).thenReturn(Optional.of(entry));
        when(memoryEntryRepository.save(entry)).thenReturn(entry);

        MemoryResponse response = memoryService.updateMemory(token, memoryId, request);

        assertEquals("New title", response.title());
        assertEquals("Old text", response.textContent());
        assertTrue(response.isFavorite());
    }

    @Test
    void updateMemoryThrowsBadRequestWhenResultHasNoContent() {
        UUID memoryId = UUID.randomUUID();
        MemoryEntry entry = new MemoryEntry();
        entry.setId(memoryId);
        entry.setWorldType("old_world");
        entry.setTextContent("Old text");

        UpdateMemoryRequest request = new UpdateMemoryRequest(null, null, "   ", null, null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findByIdAndProfile_Id(memoryId, profileId)).thenReturn(Optional.of(entry));

        assertThrows(ResponseStatusException.class, () -> memoryService.updateMemory(token, memoryId, request));
        verify(memoryEntryRepository, never()).save(any(MemoryEntry.class));
    }

    @Test
    void updateMemoryThrowsNotFoundWhenMissing() {
        UUID memoryId = UUID.randomUUID();
        UpdateMemoryRequest request = new UpdateMemoryRequest(null, "New title", null, null, null);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findByIdAndProfile_Id(memoryId, profileId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memoryService.updateMemory(token, memoryId, request));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void deleteMemoryDeletesExistingEntry() {
        UUID memoryId = UUID.randomUUID();
        MemoryEntry entry = new MemoryEntry();
        entry.setId(memoryId);

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findByIdAndProfile_Id(memoryId, profileId)).thenReturn(Optional.of(entry));

        memoryService.deleteMemory(token, memoryId);

        verify(memoryEntryRepository, times(1)).delete(entry);
    }

    @Test
    void deleteMemoryThrowsNotFoundWhenMissing() {
        UUID memoryId = UUID.randomUUID();

        when(childProfileService.getMyProfileEntity(token)).thenReturn(profile);
        when(memoryEntryRepository.findByIdAndProfile_Id(memoryId, profileId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> memoryService.deleteMemory(token, memoryId));
        verify(memoryEntryRepository, never()).delete(any(MemoryEntry.class));
    }
}
