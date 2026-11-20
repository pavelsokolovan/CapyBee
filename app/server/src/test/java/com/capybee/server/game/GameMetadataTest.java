package com.capybee.server.game;

import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.domain.profile.FamilyProfile;
import com.capybee.server.repository.FamilyProfileRepository;
import com.capybee.server.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameMetadataTest {
    @Test void serializedProductionDtosMatchBrowserFixtures() throws Exception {
        var fixtures = com.capybee.engine.session.wire.SessionJson.tree(java.nio.file.Files.readString(
                Path.of("../ui/src/game/fixtures/contracts.json")));
        var profile = com.capybee.engine.session.wire.SessionJson.convert(fixtures.get("profile"), DynamicGameController.ProfileDto.class);
        assertThat(com.capybee.engine.session.wire.SessionJson.tree(com.capybee.engine.session.wire.SessionJson.encode(profile))).isEqualTo(fixtures.get("profile"));
        for (String name : List.of("pack", "dynamic", "placeEntry")) {
            var dto = com.capybee.engine.session.wire.SessionJson.convert(fixtures.get(name),
                    com.capybee.engine.session.wire.RunSummaryDto.class);
            assertThat(com.capybee.engine.session.wire.SessionJson.tree(
                    com.capybee.engine.session.wire.SessionJson.encode(dto))).isEqualTo(fixtures.get(name));
        }
        for (String name : List.of("processing", "started", "failed")) {
            var dto = com.capybee.engine.session.wire.SessionJson.convert(fixtures.get(name), DynamicGameController.OperationDto.class);
            assertThat(com.capybee.engine.session.wire.SessionJson.tree(
                    com.capybee.engine.session.wire.SessionJson.encode(dto))).isEqualTo(fixtures.get(name));
        }
    }

    @Test void discoveryIsLocalizedAudienceBoundAndIndependentOfSavedRunAccess() throws Exception {
        var catalog = new GeneratorProfileCatalog(true, null, List.of("generic-adventure", "classic-detective"));
        var users = mock(UserService.class); var profiles = mock(FamilyProfileRepository.class);
        var auth = mock(OAuth2AuthenticationToken.class); var user = new UserAccount();user.setId(UUID.randomUUID());
        when(users.getCurrentUser(auth)).thenReturn(user);
        var child = new FamilyProfile();child.setBirthYear(2014);
        when(profiles.findByParentUser_Id(user.getId())).thenReturn(Optional.of(child));
        @SuppressWarnings("unchecked") ObjectProvider<DynamicStartOperationService> operations=mock(ObjectProvider.class);
        var service = mock(DynamicStartOperationService.class);
        when(operations.getIfAvailable()).thenReturn(service);
        var controller = new DynamicGameController(catalog,operations,users,profiles,mock(DynamicStartRateLimiter.class),false,0,
                "https://example.invalid",Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"),ZoneOffset.UTC),false,true,true,new org.springframework.beans.factory.ObjectProvider<com.capybee.engine.port.TextGenerator>() { @Override public com.capybee.engine.port.TextGenerator getIfAvailable() { return mock(com.capybee.engine.port.TextGenerator.class); } });
        var http = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/game/capabilities").principal(auth))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        var result = controller.profiles(auth,Locale.forLanguageTag("pl-PL"));
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getBody()).hasSize(1);
        var metadata = result.getBody().getFirst();
        assertThat(metadata.metadataLocale()).isEqualTo("pl");assertThat(metadata.steps()).isNotEmpty();
        assertThat(metadata.inputs()).anyMatch(input -> input.kind().equals("ENUM_SET") && input.maxItems() == 2);
        assertThat(controller.capabilities(auth).getBody()).containsEntry("clientEnabled",false).containsEntry("dynamicStartEnabled",true);
        assertThat(controller.capabilities(auth).getBody()).containsEntry("viewFeatures",List.of("canAdvance","presentation-v1","place-entry-v1"))
                .containsEntry("supportedViewSchemas",List.of(1,2));
        assertThatThrownBy(()->controller.profile(auth,"classic-detective",Locale.ENGLISH)).isInstanceOf(com.capybee.engine.session.SessionException.class);
        var detectiveProfile = catalog.find("classic-detective").orElseThrow().identity();
        var request = new DynamicGameController.DynamicStartBody(UUID.randomUUID(), "classic-detective",
                detectiveProfile.version().toString(), detectiveProfile.fingerprint(), "pl", "medium", "expert",
                Map.of(), null, 1, true, null, null);
        assertThatThrownBy(() -> controller.start(auth, "https://example.invalid", request))
                .isInstanceOf(com.capybee.engine.session.SessionException.class);
        verifyNoInteractions(service);

        child.setBirthYear(2010);
        assertThat(controller.profiles(auth, Locale.ENGLISH).getBody())
                .extracting(DynamicGameController.ProfileDto::id).containsExactly("classic-detective");
        assertThat(controller.capabilities(auth).getBody()).containsEntry("dynamicStartEnabled", true);
        var detective = controller.profile(auth, "classic-detective", Locale.forLanguageTag("pl-PL")).getBody();
        assertThat(detective.metadataLocale()).isEqualTo("pl");
        assertThat(detective.title()).isEqualTo("Klasyczna zagadka detektywistyczna");
        assertThat(detective.supportedLocales()).containsExactlyInAnyOrder("en", "pl");
        assertThat(detective.inputs()).extracting(DynamicGameController.InputDto::id)
                .containsExactly("genre", "setting", "tone", "interests", "length", "challenge", "game-locale", "story-wish");

        when(service.submit(any(), eq(request.requestId()), any(), isNull(), isNull(), any()))
                .thenReturn(new DynamicStartOperationService.Processing(100));
        assertThat(controller.start(auth, "https://example.invalid", request).getStatusCode().value()).isEqualTo(202);

        child.setBirthYear(null);
        assertThat(controller.profiles(auth,Locale.ENGLISH).getBody()).isEmpty();
        assertThat(controller.capabilities(auth).getBody()).containsEntry("dynamicStartEnabled",false).containsEntry("gameEnabled",true).containsEntry("packGenerationEnabled",false);
    }
}
