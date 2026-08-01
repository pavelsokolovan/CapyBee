package com.capybee.server.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.capybee.server.web.dto.MissionResponse;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionCompletionRepository missionCompletionRepository;
    private final MissionInteractionRepository missionInteractionRepository;
    private final MissionChildStateRepository missionChildStateRepository;
    private final UserService userService;
    private final ChildProfileService childProfileService;

    public MissionService(MissionRepository missionRepository,
            MissionCompletionRepository missionCompletionRepository,
            MissionInteractionRepository missionInteractionRepository,
            MissionChildStateRepository missionChildStateRepository,
            UserService userService,
            ChildProfileService childProfileService) {
        this.missionRepository = missionRepository;
        this.missionCompletionRepository = missionCompletionRepository;
        this.missionInteractionRepository = missionInteractionRepository;
        this.missionChildStateRepository = missionChildStateRepository;
        this.userService = userService;
        this.childProfileService = childProfileService;
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getMissions(OAuth2AuthenticationToken token, Boolean active) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        boolean polish = isPolishLocale(profile.getPreferredLocale());
        List<Mission> missions = Boolean.TRUE.equals(active)
                ? missionRepository.findTop20ByActiveOrderByCreatedAtDesc(true)
                : missionRepository.findTop20ByOrderByCreatedAtDesc();

        if (Boolean.TRUE.equals(active)) {
            missions = missions.stream()
                    .sorted(Comparator.comparing(
                            mission -> getLastActionedAt(profile, mission),
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
        }

        return missions.stream().map(mission -> toMissionResponse(mission, polish)).toList();
    }

    @Transactional
    public MissionCompletionResponse completeMission(OAuth2AuthenticationToken token, UUID missionId,
            CreateMissionCompletionRequest request) {
        UserAccount user = userService.getCurrentUser(token);
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        Mission mission = missionRepository.findById(Objects.requireNonNull(missionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found"));

        if (!mission.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mission is not active");
        }

        MissionCompletion completion = new MissionCompletion();
        completion.setMission(mission);
        completion.setUserAccount(user);
        completion.setNote(trimToNull(request.note()));

        MissionCompletion saved = Objects.requireNonNull(missionCompletionRepository.save(completion));
        missionInteractionRepository.save(newMissionInteraction(profile, mission, "completed"));
        updateMissionChildState(profile, mission, Instant.now());
        return toMissionCompletionResponse(saved, profile.getId());
    }

    @Transactional
    public MissionInteractionResponse skipMission(OAuth2AuthenticationToken token, UUID missionId) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        Mission mission = missionRepository.findById(Objects.requireNonNull(missionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found"));

        Instant now = Instant.now();
        MissionInteraction interaction = missionInteractionRepository.save(newMissionInteraction(profile, mission, "skipped"));
        updateMissionChildState(profile, mission, now);
        return toMissionInteractionResponse(interaction);
    }

    @Transactional
    public MissionInteractionResponse undoSkipMission(OAuth2AuthenticationToken token, UUID missionId) {
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);
        Mission mission = missionRepository.findById(Objects.requireNonNull(missionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found"));

        MissionInteraction interaction = missionInteractionRepository.save(newMissionInteraction(profile, mission, "undone"));

        MissionChildState state = missionChildStateRepository.findByProfile_IdAndMission_Id(profile.getId(), mission.getId())
                .orElse(null);
        if (state != null) {
            missionInteractionRepository.findFirstByProfile_IdAndMission_IdAndActionOrderByCreatedAtDesc(
                    profile.getId(), mission.getId(), "skipped")
                    .ifPresent(latestSkip -> {
                        missionInteractionRepository.delete(latestSkip);
                    });
            state.setLastActionedAt(null);
            missionChildStateRepository.save(state);
        }

        return toMissionInteractionResponse(interaction);
    }

    @Transactional(readOnly = true)
    public List<MissionCompletionResponse> getMissionCompletions(OAuth2AuthenticationToken token, Instant before) {
        UserAccount user = userService.getCurrentUser(token);
        FamilyProfile profile = childProfileService.getMyProfileEntity(token);

        List<MissionCompletion> completions = before == null
                ? missionCompletionRepository.findAllByUserAccount_IdOrderByCompletedAtAsc(user.getId())
                : missionCompletionRepository.findTop20ByUserAccount_IdAndCompletedAtLessThanOrderByCompletedAtDesc(
                        user.getId(), before);

        return completions.stream().map(entry -> toMissionCompletionResponse(entry, profile.getId())).toList();
    }

    @Transactional
    public void deleteMissionCompletion(OAuth2AuthenticationToken token, UUID completionId) {
        UserAccount user = userService.getCurrentUser(token);
        MissionCompletion completion = missionCompletionRepository.findById(completionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission completion not found"));
        
        // Ensure user owns this completion
        if (!completion.getUserAccount().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to delete this mission completion");
        }
        
        missionCompletionRepository.delete(completion);
    }

    private Instant getLastActionedAt(FamilyProfile profile, Mission mission) {
        return missionChildStateRepository.findByProfile_IdAndMission_Id(profile.getId(), mission.getId())
                .map(MissionChildState::getLastActionedAt)
                .orElse(null);
    }

    private void updateMissionChildState(FamilyProfile profile, Mission mission, Instant lastActionedAt) {
        MissionChildState state = missionChildStateRepository.findByProfile_IdAndMission_Id(profile.getId(), mission.getId())
                .orElseGet(() -> {
                    MissionChildState created = new MissionChildState();
                    created.setProfile(profile);
                    created.setMission(mission);
                    return created;
                });
        state.setLastActionedAt(lastActionedAt);
        missionChildStateRepository.save(state);
    }

    private MissionResponse toMissionResponse(Mission mission, boolean polish) {
        String localizedTitle = polish
                ? coalesce(mission.getTitlePl(), mission.getTitleEn(), mission.getTitle())
                : coalesce(mission.getTitleEn(), mission.getTitlePl(), mission.getTitle());
        String localizedTimeHint = polish
                ? coalesce(mission.getTimeHintPl(), mission.getTimeHintEn(), "to zajmie 2 minuty")
                : coalesce(mission.getTimeHintEn(), mission.getTimeHintPl(), "this takes 2 minutes");

        return new MissionResponse(
                mission.getId(),
                mission.getCode(),
                localizedTitle,
                localizedTimeHint,
                mission.getDescription(),
                mission.isActive());
    }

    private MissionInteractionResponse toMissionInteractionResponse(MissionInteraction interaction) {
        return new MissionInteractionResponse(
                interaction.getMission().getId(),
                interaction.getAction(),
                interaction.getCreatedAt());
    }

    @NonNull
    private MissionInteraction newMissionInteraction(FamilyProfile profile, Mission mission, String action) {
        MissionInteraction interaction = new MissionInteraction();
        interaction.setProfile(profile);
        interaction.setMission(mission);
        interaction.setAction(action);
        return interaction;
    }

    private boolean isPolishLocale(String locale) {
        return locale != null && locale.toLowerCase().startsWith("pl");
    }

    private String coalesce(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private MissionCompletionResponse toMissionCompletionResponse(MissionCompletion completion, UUID profileId) {
        return new MissionCompletionResponse(
                completion.getId(),
                completion.getMission().getId(),
                completion.getMission().getCode(),
                completion.getMission().getTitle(),
                profileId,
                completion.getCompletedAt(),
                completion.getNote());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
