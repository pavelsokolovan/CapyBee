package com.capybee.server.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.capybee.server.domain.friendship.FriendshipEntry;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.FriendshipEntryRepository;
import com.capybee.server.web.dto.CreateFriendshipRequest;
import com.capybee.server.web.dto.FriendshipResponse;
import com.capybee.server.web.dto.UpdateFriendshipRequest;

@Service
public class FriendshipService {

    private static final Set<String> ALLOWED_STAGES = Set.of("noticed", "was_nice", "talked", "want_to_know_better");

    private final FriendshipEntryRepository friendshipEntryRepository;
    private final ChildProfileService childProfileService;

    public FriendshipService(FriendshipEntryRepository friendshipEntryRepository, ChildProfileService childProfileService) {
        this.friendshipEntryRepository = friendshipEntryRepository;
        this.childProfileService = childProfileService;
    }

    @Transactional
    public FriendshipResponse createFriendship(OAuth2AuthenticationToken token, CreateFriendshipRequest request) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);

        if (request.id() != null) {
            Optional<FriendshipEntry> existing = friendshipEntryRepository.findByIdAndProfile_Id(request.id(), profile.getId());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }

        FriendshipEntry entry = new FriendshipEntry();
        if (request.id() != null) {
            entry.setId(request.id());
        }
        entry.setProfile(profile);
        entry.setPersonLabel(requirePersonLabel(request.personLabel()));
        entry.setStage(requireStage(request.stage()));
        entry.setNote(trimToNull(request.note()));

        return toResponse(Objects.requireNonNull(friendshipEntryRepository.save(entry)));
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> listFriendships(OAuth2AuthenticationToken token) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        return friendshipEntryRepository.findAllByProfile_IdOrderByCreatedAtAsc(profile.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FriendshipResponse updateFriendship(OAuth2AuthenticationToken token, UUID entryId, UpdateFriendshipRequest request) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        FriendshipEntry entry = friendshipEntryRepository.findByIdAndProfile_Id(entryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friendship entry not found"));

        if (request.stage() != null) {
            entry.setStage(requireStage(request.stage()));
        }
        if (request.note() != null) {
            entry.setNote(trimToNull(request.note()));
        }

        return toResponse(friendshipEntryRepository.save(entry));
    }

    @Transactional
    public void deleteFriendship(OAuth2AuthenticationToken token, UUID entryId) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        FriendshipEntry entry = friendshipEntryRepository.findByIdAndProfile_Id(entryId, profile.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friendship entry not found"));
        friendshipEntryRepository.delete(Objects.requireNonNull(entry));
    }

    private String requirePersonLabel(String personLabel) {
        String trimmed = trimToNull(personLabel);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personLabel is required");
        }
        if (trimmed.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personLabel is too long");
        }
        return trimmed;
    }

    private String requireStage(String stage) {
        String normalized = trimToNull(stage);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stage is required");
        }
        String lower = normalized.toLowerCase();
        if (!ALLOWED_STAGES.contains(lower)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported stage");
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

    private FriendshipResponse toResponse(FriendshipEntry entry) {
        return new FriendshipResponse(
                entry.getId(),
                entry.getPersonLabel(),
                entry.getStage(),
                entry.getNote(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }
}
