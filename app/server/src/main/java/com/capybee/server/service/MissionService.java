package com.capybee.server.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.capybee.server.domain.mission.Mission;
import com.capybee.server.domain.mission.MissionCompletion;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.MissionCompletionRepository;
import com.capybee.server.repository.MissionRepository;
import com.capybee.server.web.dto.CreateMissionCompletionRequest;
import com.capybee.server.web.dto.MissionCompletionResponse;
import com.capybee.server.web.dto.MissionResponse;

@Service
public class MissionService {

    private final MissionRepository missionRepository;
    private final MissionCompletionRepository missionCompletionRepository;
    private final UserService userService;
    private final ChildProfileService childProfileService;

    public MissionService(MissionRepository missionRepository,
            MissionCompletionRepository missionCompletionRepository,
            UserService userService,
            ChildProfileService childProfileService) {
        this.missionRepository = missionRepository;
        this.missionCompletionRepository = missionCompletionRepository;
        this.userService = userService;
        this.childProfileService = childProfileService;
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getMissions(Boolean active) {
        List<Mission> missions = Boolean.TRUE.equals(active)
                ? missionRepository.findTop20ByActiveOrderByCreatedAtDesc(true)
                : missionRepository.findTop20ByOrderByCreatedAtDesc();
        return missions.stream().map(this::toMissionResponse).toList();
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
        return toMissionCompletionResponse(saved, profile.getId());
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

    private MissionResponse toMissionResponse(Mission mission) {
        return new MissionResponse(
                mission.getId(),
                mission.getCode(),
                mission.getTitle(),
                mission.getDescription(),
                mission.isActive());
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
