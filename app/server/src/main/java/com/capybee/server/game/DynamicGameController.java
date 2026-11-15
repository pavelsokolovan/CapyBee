package com.capybee.server.game;

import com.capybee.engine.generation.GenerationSetup.SetupOption;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.session.ConfirmedDynamicPreparation;
import com.capybee.engine.session.DynamicStartCoordinator;
import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes.*;
import com.capybee.engine.session.wire.RunSummaryDto;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.FamilyProfileRepository;
import com.capybee.server.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/game")
public class DynamicGameController {
    private final GeneratorProfileCatalog catalog;
    private final ObjectProvider<DynamicStartOperationService> operations;
    private final UserService users;
    private final FamilyProfileRepository familyProfiles;
    private final boolean allowSeed;
    private final boolean webEnabled, gameEnabled, generationEnabled;
    private final java.time.Clock clock;
    private final DynamicStartRateLimiter rateLimiter;
    private final long syncWaitMs;
    private final Set<String> allowedOrigins;

    public DynamicGameController(GeneratorProfileCatalog catalog,
            ObjectProvider<DynamicStartOperationService> operations, UserService users,
            FamilyProfileRepository familyProfiles,
            DynamicStartRateLimiter rateLimiter,
            @Value("${capybee.game.allow-seed:false}") boolean allowSeed,
            @Value("${capybee.game.sync-wait-ms:8000}") long syncWaitMs,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173,https://capybee.fly.dev}")
            String configuredOrigins, java.time.Clock clock,
            @Value("${capybee.game.web-client-enabled:false}") boolean webEnabled,
            @Value("${capybee.game.enabled:false}") boolean gameEnabled,
            @Value("${capybee.game.generation-enabled:false}") boolean generationEnabled) {
        this.clock = clock; this.webEnabled = webEnabled; this.gameEnabled = gameEnabled; this.generationEnabled = generationEnabled;
        this.catalog = catalog; this.operations = operations; this.users = users;
        this.familyProfiles = familyProfiles; this.rateLimiter = rateLimiter; this.allowSeed = allowSeed;
        this.syncWaitMs = Math.max(0, Math.min(20_000, syncWaitMs));
        this.allowedOrigins = Set.copyOf(Arrays.stream(configuredOrigins.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList());
    }

    @GetMapping("/generator-profiles")
    public ResponseEntity<List<ProfileDto>> profiles(OAuth2AuthenticationToken authentication,
            @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        owner(authentication);
        List<ProfileDto> result = catalog.enabled() ? catalog.all().stream()
                .filter(profile -> eligible(authentication, profile)).map(profile -> metadata(profile, locale)).toList() : List.of();
        return noStore(result);
    }

    @GetMapping("/generator-profiles/{profileId}")
    public ResponseEntity<ProfileDto> profile(OAuth2AuthenticationToken authentication,
                                               @PathVariable String profileId,
            @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        owner(authentication);
        if (!catalog.enabled()) throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        var selected = catalog.find(profileId).orElseThrow(() -> new SessionException(ErrorCode.RUN_NOT_FOUND));
        if (!eligible(authentication, selected)) throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        return noStore(metadata(selected, locale));
    }

    @PostMapping(value = "/dynamic-starts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> start(OAuth2AuthenticationToken authentication,
                                   @RequestHeader("Origin") String origin,
                                   @RequestBody DynamicStartBody body) {
        requireSameOrigin(origin);
        OwnerRef owner = owner(authentication);
        if (!catalog.enabled() || body == null || body.requestId() == null || !body.confirmed()
                || body.disclosureRevision() != 1 || body.profileId() == null || body.profileVersion() == null
                || body.profileFingerprint() == null || body.locale() == null
                || body.lengthPreset() == null || body.challengePreset() == null
                || body.selections() != null && body.selections().size() > 8
                || body.seed() != null && !allowSeed) throw new SessionException(ErrorCode.REQUEST_INVALID);
        ValidatedGeneratorProfile profile = catalog.find(body.profileId())
                .orElseThrow(() -> new SessionException(ErrorCode.REQUEST_INVALID));
        if (!gameEnabled || !generationEnabled || !eligible(authentication, profile))
            throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        if (!profile.identity().version().toString().equals(body.profileVersion())
                || !profile.identity().fingerprint().equals(body.profileFingerprint()))
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        if (body.suggestion() != null && (body.suggestion().codePointCount(0, body.suggestion().length())
                > profile.profile().policy().maxSuggestionCodePoints()
                || !profile.profile().policy().freeTextAllowed())) throw new SessionException(ErrorCode.REQUEST_INVALID);
        Map<String, PreparationAnswer> answers = new LinkedHashMap<>();
        if (body.selections() != null) body.selections().forEach((key, values) -> {
            if (key == null || values == null || values.size() > 4)
                throw new SessionException(ErrorCode.REQUEST_INVALID);
            answers.put(key, values.isEmpty() ? new PreparationAnswer.Cleared()
                    : new PreparationAnswer.Selected(values));
        });
        answers.put("length", new PreparationAnswer.Selected(List.of(body.lengthPreset())));
        answers.put("challenge", new PreparationAnswer.Selected(List.of(body.challengePreset())));
        answers.put("game-locale", new PreparationAnswer.Selected(List.of(body.locale())));
        PreparationResolution resolved = new PreparationPresetResolver().resolve(profile, answers,
                new LocaleTag(body.locale()), HostGenerationCeilings.none());
        if (!resolved.valid()) throw new SessionException(ErrorCode.REQUEST_INVALID);
        var preparation = new ConfirmedDynamicPreparation(profile, resolved.preferences().orElseThrow(),
                resolved.constraints().orElseThrow(), body.disclosureRevision(), body.suggestion());
        DynamicStartOperationService service = operations.getIfAvailable();
        if (service == null) throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        if (!service.exists(owner, body.requestId())) rateLimiter.check(owner);
        return response(service.submit(owner, body.requestId(), preparation, body.seed(),
                body.expectedCurrentRunId(), Duration.ofMillis(syncWaitMs)));
    }

    @GetMapping("/dynamic-starts/{requestId}")
    public ResponseEntity<?> status(OAuth2AuthenticationToken authentication, @PathVariable UUID requestId) {
        DynamicStartOperationService service = operations.getIfAvailable();
        if (service == null) throw new SessionException(ErrorCode.RUN_NOT_FOUND);
        var result = service.status(owner(authentication), requestId);
        if (result == null) throw new SessionException(ErrorCode.RUN_NOT_FOUND);
        return response(result);
    }

    @DeleteMapping("/dynamic-starts/{requestId}")
    public ResponseEntity<?> cancel(OAuth2AuthenticationToken authentication,
                                                @PathVariable UUID requestId,
                                                @RequestHeader("Origin") String origin) {
        requireSameOrigin(origin);
        DynamicStartOperationService service = operations.getIfAvailable();
        if (service == null || !service.cancel(owner(authentication), requestId))
            throw new SessionException(ErrorCode.RUN_NOT_FOUND);
        return response(service.status(owner(authentication), requestId));
    }

    private ResponseEntity<?> response(DynamicStartOperationService.Submission submission) {
        if (submission instanceof DynamicStartOperationService.Conflict)
            return noStore(HttpStatus.CONFLICT, new OperationDto("FAILED", 0, null, "DYNAMIC_REQUEST_CONFLICT"));
        if (submission instanceof DynamicStartOperationService.Processing processing)
            return noStore(HttpStatus.ACCEPTED, new OperationDto("PROCESSING", processing.retryAfterMs(), null, null));
        DynamicStartCoordinator.Result result = ((DynamicStartOperationService.Terminal) submission).result();
        if (result instanceof DynamicStartCoordinator.Started started)
            return noStore(new OperationDto("STARTED", 0, GameRunController.toSummary(started.run()), null));
        if (result instanceof DynamicStartCoordinator.NeedsInput value)
            return noStore(HttpStatus.UNPROCESSABLE_ENTITY, new OperationDto("NEEDS_INPUT", 0, null, value.code()));
        if (result instanceof DynamicStartCoordinator.FallbackOffered value)
            return noStore(new OperationDto("FALLBACK_OFFERED", 0, null, value.code()));
        if (result instanceof DynamicStartCoordinator.Cancelled value)
            return noStore(new OperationDto("CANCELLED", 0, null, value.code()));
        return noStore(HttpStatus.SERVICE_UNAVAILABLE, new OperationDto("FAILED", 0, null,
                ((DynamicStartCoordinator.Failed) result).code()));
    }

    private OwnerRef owner(OAuth2AuthenticationToken authentication) {
        UserAccount user = users.getCurrentUser(authentication);
        if (familyProfiles.findByParentUser_Id(user.getId()).filter(p -> p.isActive()).isEmpty())
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        return new OwnerRef(user.getId().toString());
    }

    private boolean eligible(OAuth2AuthenticationToken authentication, ValidatedGeneratorProfile profile) {
        var user = users.getCurrentUser(authentication);
        Integer year = familyProfiles.findByParentUser_Id(user.getId()).filter(p -> p.isActive())
                .map(p -> p.getBirthYear()).orElse(null);
        return audienceEligible(year, java.time.Year.now(clock).getValue(),
                profile.profile().policy().minAge(), profile.profile().policy().maxAge());
    }
    static boolean audienceEligible(Integer birthYear, int currentYear, int minAge, int maxAge) {
        return birthYear != null && currentYear - birthYear - 1 >= minAge && currentYear - birthYear <= maxAge;
    }
    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> capabilities(OAuth2AuthenticationToken authentication) {
        owner(authentication);
        boolean ageKnown = familyProfiles.findByParentUser_Id(users.getCurrentUser(authentication).getId())
                .filter(p -> p.isActive()).map(p -> p.getBirthYear()).isPresent();
        boolean dynamic = gameEnabled && generationEnabled && catalog.enabled() && operations.getIfAvailable() != null
                && catalog.all().stream().anyMatch(p -> eligible(authentication, p));
        return noStore(Map.of("schemaVersion", 1, "clientEnabled", webEnabled, "gameEnabled", gameEnabled,
                "packStartEnabled", gameEnabled, "packGenerationEnabled", gameEnabled && generationEnabled && ageKnown,
                "dynamicStartEnabled", dynamic, "supportedViewSchemas", List.of(1, 2),
                "viewFeatures", List.of("canAdvance", "presentation-v1"), "disclosureRevision", 1, "dynamicCancelMode", "BEST_EFFORT"));
    }

    private void requireSameOrigin(String origin) {
        if (origin == null || !allowedOrigins.contains(origin)) throw new SessionException(ErrorCode.REQUEST_INVALID);
    }

    private static ProfileDto metadata(ValidatedGeneratorProfile value, Locale requested) {
        var profile = value.profile();
        LocaleTag locale = profile.supportedLocales().stream()
                .filter(item -> requested != null && item.value().equalsIgnoreCase(requested.getLanguage()))
                .findFirst().orElse(profile.defaultLocale());
        Map<String, String> strings = profile.strings().get(locale);
        List<QuestionDto> questions = profile.setup().questions().stream().map(question ->
                new QuestionDto(question.inputId(), strings.get(question.helpKey()), question.defaultValues(),
                        question.options().stream().map(option -> option(option, strings)).toList(),
                        question.freeTextNoticeKey() == null ? null : strings.get(question.freeTextNoticeKey()))).toList();
        return new ProfileDto(value.identity().id().value(), value.identity().version().toString(),
                value.identity().fingerprint(), strings.get(profile.titleKey()), strings.get(profile.descriptionKey()),
                profile.supportedLocales().stream().map(LocaleTag::value).toList(), profile.defaultLocale().value(),
                questions, profile.lengthPresets().stream().map(p -> new PresetDto(p.id(), strings.get(p.labelKey()))).toList(),
                profile.challengePresets().stream().map(p -> new PresetDto(p.id(), strings.get(p.labelKey()))).toList(),
                profile.policy().freeTextAllowed(), profile.policy().maxSuggestionCodePoints(), 1, locale.value(),
                profile.setup().steps().stream().map(step -> new StepDto(step.id(), strings.get(step.titleKey()), step.inputIds())).toList(),
                profile.setup().inputs().stream().map(input -> {
                    var question = profile.setup().questions().stream().filter(q -> q.inputId().equals(input.id())).findFirst().orElseThrow();
                    return new InputDto(input.id(), input instanceof StartInputDef.EnumInput ? "ENUM" : input instanceof StartInputDef.EnumSetInput ? "ENUM_SET" : "FREE_TEXT",
                            strings.get(input.labelKey()), strings.get(question.helpKey()), input.required(),
                            input instanceof StartInputDef.EnumSetInput set ? set.maxItems() : 1,
                            input instanceof StartInputDef.FreeTextInput text ? Math.min(text.maxChars(), profile.policy().maxSuggestionCodePoints()) : null,
                            question.defaultValues(), question.options().stream().map(o -> option(o, strings)).toList(),
                            question.freeTextNoticeKey() == null ? null : strings.get(question.freeTextNoticeKey()));
                }).toList());
    }

    private static OptionDto option(SetupOption option, Map<String, String> strings) {
        return new OptionDto(option.value(), strings.get(option.labelKey()));
    }
    private static <T> ResponseEntity<T> noStore(T body) { return noStore(HttpStatus.OK, body); }
    private static <T> ResponseEntity<T> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }

    public record DynamicStartBody(UUID requestId, String profileId, String profileVersion,
            String profileFingerprint, String locale, String lengthPreset, String challengePreset,
            Map<String, List<String>> selections, String suggestion, long disclosureRevision,
            boolean confirmed, UUID expectedCurrentRunId, Long seed) {}
    public record ProfileDto(String id, String version, String fingerprint, String title, String description,
            List<String> supportedLocales, String defaultLocale, List<QuestionDto> questions,
            List<PresetDto> lengthPresets, List<PresetDto> challengePresets,
            boolean suggestionAllowed, int maxSuggestionCodePoints, int setupSchemaVersion, String metadataLocale,
            List<StepDto> steps, List<InputDto> inputs) {}
    public record StepDto(String id, String title, List<String> inputIds) {}
    public record InputDto(String id, String kind, String label, String help, boolean required,
            int maxItems, Integer maxCodePoints, List<String> defaults, List<OptionDto> options, String notice) {}
    public record QuestionDto(String id, String help, List<String> defaults, List<OptionDto> options, String notice) {}
    public record OptionDto(String id, String label) {}
    public record PresetDto(String id, String label) {}
    public record OperationDto(String status, long retryAfterMs, RunSummaryDto run, String code) {}
}
