package com.capybee.server.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.capybee.server.domain.mission.Mission;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.MissionChildStateRepository;
import com.capybee.server.repository.MissionCompletionRepository;
import com.capybee.server.repository.MissionInteractionRepository;
import com.capybee.server.repository.MissionRepository;
import com.capybee.server.web.dto.MissionResponse;

class MissionServiceTest {

    @Test
    void getMissionsUsesChildScopedOrderingForActiveMissions() {
        FamilyProfile profile = new FamilyProfile();
        setField(profile, "id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        profile.setPreferredLocale("en");

        Mission mission = new Mission();
        mission.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        mission.setCode("test_mission");
        mission.setTitle("Test mission");
        mission.setTitleEn("Test mission");
        mission.setDescription("Test description");
        mission.setActive(true);

        MissionRepository missionRepository = (MissionRepository) Proxy.newProxyInstance(
                MissionRepository.class.getClassLoader(),
                new Class<?>[]{MissionRepository.class},
                (proxy, method, args) -> {
                    if ("findTop20ByActiveOrderByCreatedAtDesc".equals(method.getName())) {
                        return List.of(mission);
                    }
                    if ("findTop20ByOrderByCreatedAtDesc".equals(method.getName())) {
                        return List.of(mission);
                    }
                    return null;
                });

        MissionChildStateRepository missionChildStateRepository = (MissionChildStateRepository) Proxy.newProxyInstance(
                MissionChildStateRepository.class.getClassLoader(),
                new Class<?>[]{MissionChildStateRepository.class},
                (proxy, method, args) -> {
                    if ("findByProfile_IdAndMission_Id".equals(method.getName())) {
                        return java.util.Optional.empty();
                    }
                    return null;
                });

        MissionService missionService = new MissionService(
                missionRepository,
                (MissionCompletionRepository) Proxy.newProxyInstance(
                        MissionCompletionRepository.class.getClassLoader(),
                        new Class<?>[]{MissionCompletionRepository.class},
                        (proxy, method, args) -> null),
                (MissionInteractionRepository) Proxy.newProxyInstance(
                        MissionInteractionRepository.class.getClassLoader(),
                        new Class<?>[]{MissionInteractionRepository.class},
                        (proxy, method, args) -> null),
                missionChildStateRepository,
                new UserService(null) {
                    @Override
                    public com.capybee.server.domain.user.UserAccount getCurrentUser(OAuth2AuthenticationToken token) {
                        return new com.capybee.server.domain.user.UserAccount();
                    }
                },
                new ChildProfileService(null, null) {
                    @Override
                    public FamilyProfile getMyProfileEntity(OAuth2AuthenticationToken token) {
                        return profile;
                    }
                });

        OAuth2User principal = new DefaultOAuth2User(List.of(), java.util.Map.of("sub", "subject"), "sub");
        List<MissionResponse> result = missionService.getMissions(new OAuth2AuthenticationToken(
                principal,
                List.of(),
                "name"), true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(mission.getId());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to set field " + fieldName, ex);
        }
    }
}
