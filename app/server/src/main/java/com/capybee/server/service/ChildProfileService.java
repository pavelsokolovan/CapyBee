package com.capybee.server.service;

import java.time.Year;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.FamilyProfileRepository;
import com.capybee.server.web.dto.ChildProfileResponse;
import com.capybee.server.web.dto.CreateChildProfileRequest;
import com.capybee.server.web.dto.UpdateChildProfileRequest;

@Service
public class ChildProfileService {

    private final FamilyProfileRepository familyProfileRepository;
    private final UserService userService;

    public ChildProfileService(FamilyProfileRepository familyProfileRepository, UserService userService) {
        this.familyProfileRepository = familyProfileRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public ChildProfileResponse getMyProfile(OAuth2AuthenticationToken token) {
        UserAccount user = userService.getCurrentUser(token);
        FamilyProfile profile = familyProfileRepository.findByParentUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child profile not found"));
        return toResponse(profile);
    }

    @Transactional
    public ChildProfileResponse createProfile(OAuth2AuthenticationToken token, CreateChildProfileRequest request) {
        UserAccount user = userService.getCurrentUser(token);

        if (familyProfileRepository.findByParentUser_Id(user.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A child profile already exists");
        }

        FamilyProfile profile = new FamilyProfile();
        profile.setParentUser(user);
        profile.setNickname(requireNickname(request.nickname()));
        profile.setBirthYear(validateBirthYear(request.birthYear()));
        profile.setPreferredLocale(normalizeLocale(request.preferredLocale()));
        profile.setAvatarSeed(trimToNull(request.avatarSeed()));
        profile.setActive(true);

        return toResponse(Objects.requireNonNull(familyProfileRepository.save(profile)));
    }

    @Transactional
    public ChildProfileResponse updateProfile(OAuth2AuthenticationToken token, UpdateChildProfileRequest request) {
        UserAccount user = userService.getCurrentUser(token);
        FamilyProfile profile = familyProfileRepository.findByParentUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child profile not found"));

        if (request.nickname() != null) {
            profile.setNickname(requireNickname(request.nickname()));
        }
        if (request.birthYear() != null) {
            profile.setBirthYear(validateBirthYear(request.birthYear()));
        }
        if (request.preferredLocale() != null) {
            profile.setPreferredLocale(normalizeLocale(request.preferredLocale()));
        }
        if (request.avatarSeed() != null) {
            profile.setAvatarSeed(trimToNull(request.avatarSeed()));
        }
        if (request.active() != null) {
            profile.setActive(request.active());
        }
        if (request.hasSeenOnboarding() != null) {
            profile.setHasSeenOnboarding(request.hasSeenOnboarding());
        }

        return toResponse(Objects.requireNonNull(familyProfileRepository.save(profile)));
    }

    @Transactional(readOnly = true)
    public FamilyProfile getMyProfileEntity(OAuth2AuthenticationToken token) {
        UserAccount user = userService.getCurrentUser(token);
        return familyProfileRepository.findByParentUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Create child profile first"));
    }

    private String requireNickname(String nickname) {
        String trimmed = trimToNull(nickname);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname is required");
        }
        if (trimmed.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname is too long");
        }
        return trimmed;
    }

    private Integer validateBirthYear(Integer birthYear) {
        if (birthYear == null) {
            return null;
        }
        int currentYear = Year.now().getValue();
        if (birthYear < 2000 || birthYear > currentYear) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Birth year is out of range");
        }
        return birthYear;
    }

    private String normalizeLocale(String locale) {
        String normalized = trimToNull(locale);
        if (normalized == null) {
            return "en";
        }

        String lower = normalized.toLowerCase();
        if (!"en".equals(lower) && !"pl".equals(lower)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported locale");
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

    private ChildProfileResponse toResponse(FamilyProfile profile) {
        return new ChildProfileResponse(
                profile.getId(),
                profile.getNickname(),
                profile.getBirthYear(),
                profile.getPreferredLocale(),
                profile.getAvatarSeed(),
                profile.isActive(),
                profile.isHasSeenOnboarding(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
