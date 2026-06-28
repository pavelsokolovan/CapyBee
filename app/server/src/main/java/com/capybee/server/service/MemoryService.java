package com.capybee.server.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.capybee.server.domain.memory.MemoryEntry;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.MemoryEntryRepository;
import com.capybee.server.web.dto.CreateMemoryRequest;
import com.capybee.server.web.dto.MemoryResponse;
import com.capybee.server.web.dto.UpdateMemoryRequest;

@Service
public class MemoryService {

    private static final Set<String> ALLOWED_WORLDS = Set.of("old_world", "new_world");

    private final MemoryEntryRepository memoryEntryRepository;
    private final ChildProfileService childProfileService;

    public MemoryService(MemoryEntryRepository memoryEntryRepository, ChildProfileService childProfileService) {
        this.memoryEntryRepository = memoryEntryRepository;
        this.childProfileService = childProfileService;
    }

    @Transactional
    public MemoryResponse createMemory(OAuth2AuthenticationToken token, CreateMemoryRequest request) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);

        String textContent = trimToNull(request.textContent());
        String mediaUrl = trimToNull(request.mediaUrl());
        if (textContent == null && mediaUrl == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "textContent or mediaUrl is required");
        }

        MemoryEntry entry = new MemoryEntry();
        entry.setProfile(profile);
        entry.setWorldType(requireWorldType(request.worldType()));
        entry.setTitle(trimToNull(request.title()));
        entry.setTextContent(textContent);
        entry.setMediaUrl(mediaUrl);
        entry.setFavorite(Boolean.TRUE.equals(request.isFavorite()));

        return toResponse(Objects.requireNonNull(memoryEntryRepository.save(entry)));
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> listMemories(OAuth2AuthenticationToken token, String worldType) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);

        List<MemoryEntry> entries;
        if (trimToNull(worldType) == null) {
            entries = memoryEntryRepository.findTop20ByProfile_IdOrderByCreatedAtDesc(profile.getId());
        } else {
            entries = memoryEntryRepository.findTop20ByProfile_IdAndWorldTypeOrderByCreatedAtDesc(profile.getId(),
                    requireWorldType(worldType));
        }

        return entries.stream().map(this::toResponse).toList();
    }

    @Transactional
    public MemoryResponse updateMemory(OAuth2AuthenticationToken token, UUID memoryId, UpdateMemoryRequest request) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        MemoryEntry entry = memoryEntryRepository.findByIdAndProfile_Id(memoryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));

        if (request.worldType() != null) {
            entry.setWorldType(requireWorldType(request.worldType()));
        }
        if (request.title() != null) {
            entry.setTitle(trimToNull(request.title()));
        }
        if (request.textContent() != null) {
            entry.setTextContent(trimToNull(request.textContent()));
        }
        if (request.mediaUrl() != null) {
            entry.setMediaUrl(trimToNull(request.mediaUrl()));
        }
        if (request.isFavorite() != null) {
            entry.setFavorite(request.isFavorite());
        }

        if (trimToNull(entry.getTextContent()) == null && trimToNull(entry.getMediaUrl()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory must include textContent or mediaUrl");
        }

        return toResponse(Objects.requireNonNull(memoryEntryRepository.save(entry)));
    }

    @Transactional
    public void deleteMemory(OAuth2AuthenticationToken token, UUID memoryId) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        MemoryEntry entry = memoryEntryRepository.findByIdAndProfile_Id(memoryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));
        memoryEntryRepository.delete(Objects.requireNonNull(entry));
    }

    private String requireWorldType(String worldType) {
        String normalized = trimToNull(worldType);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "worldType is required");
        }
        String lower = normalized.toLowerCase();
        if (!ALLOWED_WORLDS.contains(lower)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported worldType");
        }
        return lower;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MemoryResponse toResponse(MemoryEntry entry) {
        return new MemoryResponse(
                entry.getId(),
                entry.getWorldType(),
                entry.getTitle(),
                entry.getTextContent(),
                entry.getMediaUrl(),
                entry.isFavorite(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
