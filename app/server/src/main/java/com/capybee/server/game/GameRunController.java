package com.capybee.server.game;

import com.capybee.engine.port.TextGenerator;
import org.springframework.beans.factory.ObjectProvider;

import com.capybee.engine.api.Command;
import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.Event;
import com.capybee.engine.api.View;
import com.capybee.engine.session.RunSessionService;
import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.wire.CommandOutcomeDto;
import com.capybee.engine.session.wire.CommandRequestDto;
import com.capybee.engine.session.wire.PackInfoDto;
import com.capybee.engine.session.wire.RunSummaryDto;
import com.capybee.server.domain.user.UserAccount;
import com.capybee.server.repository.FamilyProfileRepository;
import com.capybee.server.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.capybee.engine.session.SessionTypes.*;

@RestController
@RequestMapping("/api/game")
public class GameRunController {
    private final RunSessionService sessions;
    private final PackPreparationService preparations;
    private final ReviewedInputGuard inputGuard;
    private final DynamicStartRateLimiter startLimiter;
    private final java.util.Set<String> allowedOrigins;
    private final com.capybee.engine.session.SessionPorts.PackCatalog catalog;
    private final java.time.Clock clock;
    private final UserService users;
    private final FamilyProfileRepository profiles;
    private final boolean enabled;
    private final boolean generationEnabled;
    private final boolean allowSeed;
    private final long syncWaitMs;
    private final GameCommandRateLimiter commandRateLimiter;

    public GameRunController(RunSessionService sessions, UserService users, PackPreparationService preparations, DynamicStartRateLimiter startLimiter, ReviewedInputGuard inputGuard,
                             @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173,https://capybee.fly.dev}") String origins,
                             FamilyProfileRepository profiles,
                             @Value("${capybee.game.enabled:false}") boolean enabled,
                             @Value("${capybee.game.generation-enabled:false}") boolean generationEnabled, ObjectProvider<TextGenerator> generators,
                             @Value("${capybee.game.allow-seed:false}") boolean allowSeed,
                             @Value("${capybee.game.sync-wait-ms:8000}") long syncWaitMs,
                             GameCommandRateLimiter commandRateLimiter,
                             com.capybee.engine.session.SessionPorts.PackCatalog catalog, java.time.Clock clock) {
        this.catalog = catalog; this.clock = clock; this.preparations=preparations; this.inputGuard=inputGuard; this.startLimiter=startLimiter;
        this.allowedOrigins=java.util.Set.copyOf(java.util.Arrays.stream(origins.split(",")).map(String::trim).toList());
        this.sessions = sessions;
        this.users = users;
        this.profiles = profiles;
        this.enabled = enabled;
        this.generationEnabled = generationEnabled && generators.getIfAvailable() != null;
        this.allowSeed = allowSeed;
        this.syncWaitMs = Math.min(20_000, Math.max(0, syncWaitMs));
        this.commandRateLimiter = commandRateLimiter;
    }

    @GetMapping("/packs")
    public List<PackInfoDto> packs(OAuth2AuthenticationToken authentication,
                                   @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        requireEnabled();
        currentOwner(authentication);
        return sessions.packs(locale).stream().map(info -> {
            var pack=catalog.find(info.id()).orElseThrow();
            if(pack.generatedGameProfile().filter(profile -> profile.kind()==com.capybee.engine.generation.GeneratedGameProfile.Kind.AUTHORED_GRAPH).isEmpty())return info;
            var setup=info.setup();
            return new PackInfoDto(info.id(),info.version(),info.locales(),info.defaultLocale(),info.startInputs(),preparations.enabled(),
                new com.capybee.engine.session.wire.PackSetupDto(setup.schemaVersion(),setup.metadataLocale(),setup.title(),setup.description(),preparations.enabled(),setup.steps(),setup.inputs(),PackPreparationService.fingerprint(pack)));
        }).toList();
    }

    @PostMapping("/runs")
    public ResponseEntity<StartResponse> start(OAuth2AuthenticationToken authentication,
                                                @RequestBody StartBody body) {
        requireEnabled();
        OwnerRef owner = currentOwner(authentication);
        if (body == null || body.id() == null || body.packId() == null || body.locale() == null) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        boolean generated = Boolean.TRUE.equals(body.generated());
        var selected=catalog.find(body.packId()).orElseThrow(()->new SessionException(ErrorCode.REQUEST_INVALID));
        if(generated && selected.generatedGameProfile().filter(profile->profile.kind()==com.capybee.engine.generation.GeneratedGameProfile.Kind.AUTHORED_GRAPH).isPresent())
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        if(body.startInputs()!=null)body.startInputs().forEach((key,value)->{
            if(selected.manifest().startInputs().stream().noneMatch(input->input.id().equals(key)&&!(input instanceof com.capybee.engine.pack.StartInputDef.FreeTextInput)))
                throw new SessionException(ErrorCode.REQUEST_INVALID);
        });
        if(selected.manifest().locales().stream().noneMatch(tag->tag.value().equals(body.locale())))throw new SessionException(ErrorCode.REQUEST_INVALID);
        var validatedInputs=new com.capybee.engine.input.StartInputProcessor().prepare(selected,new com.capybee.engine.pack.LocaleTag(body.locale()),body.startInputs(),inputGuard);
        if(!validatedInputs.ok())throw new SessionException(ErrorCode.REQUEST_INVALID);
        if (generated) {
            var policy = catalog.find(body.packId()).orElseThrow(() -> new SessionException(ErrorCode.REQUEST_INVALID)).manifest().contentPolicy();
            var child = profiles.findByParentUser_Id(users.getCurrentUser(authentication).getId()).filter(p -> p.isActive());
            Integer birthYear = child.map(p -> p.getBirthYear()).orElse(null);
            if (birthYear == null || (policy.audience() != null && !DynamicGameController.audienceEligible(birthYear,
                    java.time.Year.now(clock).getValue(), policy.audience().minAge(), policy.audience().maxAge())))
                throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        }
        if (generated && !generationEnabled) throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        StartAccepted accepted = sessions.start(owner, new StartRequest(body.id(),
                new com.capybee.engine.pack.PackId(body.packId()),
                new com.capybee.engine.pack.LocaleTag(body.locale()), validatedInputs.persistentValues(),
                allowSeed ? body.seed() : null, generated), Duration.ofMillis(syncWaitMs));
        return ResponseEntity.status(accepted.status() == RunStatus.ACTIVE ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new StartResponse(accepted.runId(), accepted.status().name(), accepted.revision(),
                        accepted.retryAfterMs()));
    }

    @PostMapping(value="/pack-starts", consumes="application/json")
    public ResponseEntity<?> prepare(OAuth2AuthenticationToken authentication, @RequestHeader("Origin") String origin,
                                    @RequestBody PackPreparationService.Body body) {
        requireEnabled(); requireOrigin(origin); var owner=currentOwner(authentication);
        if(body==null||body.requestId()==null)throw new SessionException(ErrorCode.REQUEST_INVALID);
        var pack=catalog.find(body.packId()).orElseThrow(()->new SessionException(ErrorCode.REQUEST_INVALID));
        var policy=pack.manifest().contentPolicy();
        Integer year=profiles.findByParentUser_Id(users.getCurrentUser(authentication).getId()).filter(p->p.isActive()).map(p->p.getBirthYear()).orElse(null);
        if(policy.audience()==null||!DynamicGameController.audienceEligible(year,java.time.Year.now(clock).getValue(),policy.audience().minAge(),policy.audience().maxAge()))
            throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        if(!preparations.exists(owner,body.requestId()))startLimiter.check(owner);
        return preparationResponse(owner,preparations.submit(owner,body,Duration.ofMillis(syncWaitMs)));
    }
    @GetMapping("/pack-starts/{id}")
    public ResponseEntity<?> preparationStatus(OAuth2AuthenticationToken authentication,@PathVariable UUID id){
        requireEnabled();var owner=currentOwner(authentication);return preparationResponse(owner,preparations.status(owner,id));
    }
    @DeleteMapping("/pack-starts/{id}")
    public ResponseEntity<?> cancelPreparation(OAuth2AuthenticationToken authentication,@RequestHeader("Origin") String origin,@PathVariable UUID id){
        requireEnabled();requireOrigin(origin);var owner=currentOwner(authentication);return preparationResponse(owner,preparations.cancel(owner,id));
    }
    private void requireOrigin(String origin){if(!allowedOrigins.contains(origin))throw new SessionException(ErrorCode.REQUEST_INVALID);}
    private ResponseEntity<?> preparationResponse(OwnerRef owner,PackPreparationService.Result result){
        var run=result.runId()==null?null:toSummary(sessions.get(owner,result.runId()));
        var status=result.status().equals("PROCESSING")?HttpStatus.ACCEPTED:result.status().equals("NEEDS_INPUT")?HttpStatus.UNPROCESSABLE_ENTITY:result.status().equals("FAILED")?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.OK;
        return ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore())
            .body(new DynamicGameController.OperationDto(result.status(),result.retryAfterMs(),run,result.code()));
    }

    @GetMapping("/runs/current")
    public RunSummaryDto current(OAuth2AuthenticationToken authentication) {
        requireEnabled();
        return summary(sessions.current(currentOwner(authentication)));
    }

    @GetMapping("/runs/{runId}")
    public RunSummaryDto get(OAuth2AuthenticationToken authentication, @PathVariable UUID runId) {
        requireEnabled();
        return summary(sessions.get(currentOwner(authentication), runId));
    }

    @PostMapping("/runs/{runId}/commands")
    public ResponseEntity<?> command(OAuth2AuthenticationToken authentication,
                                     @PathVariable UUID runId, @RequestBody CommandRequestDto body) {
        requireEnabled();
        CommandRequest request = commandRequest(body);
        OwnerRef owner = currentOwner(authentication);
        commandRateLimiter.check(owner);
        if(request.command() instanceof Command.SubmitText text && text.text()!=null){
            var current=sessions.get(owner,runId);
            if(current.status()==RunStatus.ACTIVE && current.revision()==request.expectedRevision()){
                com.capybee.engine.input.InputSafetyDecision decision;
                try {decision=inputGuard.inspect(new com.capybee.engine.input.PiiScrubber().process(text.text()).input(),
                    new com.capybee.engine.input.InputPolicy(true,500,280,3,null,java.util.Set.of(),"1"));}
                catch(RuntimeException invalid){throw new SessionException(ErrorCode.REQUEST_INVALID);}
                if(decision.action()==com.capybee.engine.input.InputSafetyAction.BLOCK){
                    var issue=new com.capybee.engine.api.Issue(com.capybee.engine.api.Issue.Severity.ERROR,decision.reasonCode(),"Text input was not accepted");
                    return ResponseEntity.ok(new CommandOutcomeDto(request.id(),"REJECTED",false,current.revision(),current.revision(),current.status().name(),current.view(),View.CURRENT_VIEW_SCHEMA,List.of(),List.of(issue)));
                }
            }
        }
        CommandOutcome outcome = sessions.command(owner, runId, request,
                Duration.ofMillis(syncWaitMs));
        if (outcome.outcome() == Outcome.PROCESSING) {
            return ResponseEntity.accepted().body(new ProcessingResponse(outcome.commandId(),
                    "PROCESSING", outcome.revision(), 1500));
        }
        return ResponseEntity.ok(commandOutcome(outcome));
    }

    @GetMapping("/runs/{runId}/commands/{commandId}")
    public ResponseEntity<?> commandStatus(OAuth2AuthenticationToken authentication,
                                           @PathVariable UUID runId, @PathVariable UUID commandId) {
        requireEnabled();
        CommandStatus status = sessions.commandStatus(currentOwner(authentication), runId, commandId);
        if (status.processing()) {
            return ResponseEntity.accepted().body(new ProcessingResponse(commandId, "PROCESSING",
                    sessions.get(currentOwner(authentication), runId).revision(), status.retryAfterMs()));
        }
        return ResponseEntity.ok(commandOutcome(status.outcome()));
    }

    @GetMapping("/runs/{runId}/events")
    public List<EventResponse> events(OAuth2AuthenticationToken authentication, @PathVariable UUID runId,
                                      @RequestParam(defaultValue = "0") long after,
                                      @RequestParam(defaultValue = "50") int limit) {
        requireEnabled();
        return sessions.events(currentOwner(authentication), runId, after, limit).stream()
                .map(item -> new EventResponse(item.seq(), item.revision(), item.event())).toList();
    }

    @DeleteMapping("/runs/{runId}")
    public ResponseEntity<Void> delete(OAuth2AuthenticationToken authentication, @PathVariable UUID runId) {
        requireEnabled();
        sessions.delete(currentOwner(authentication), runId);
        return ResponseEntity.noContent().build();
    }

    private OwnerRef currentOwner(OAuth2AuthenticationToken authentication) {
        UserAccount user = users.getCurrentUser(authentication);
        var profile = profiles.findByParentUser_Id(user.getId())
                .filter(com.capybee.server.domain.profile.FamilyProfile::isActive);
        if (profile.isEmpty()) throw new SessionException(ErrorCode.REQUEST_INVALID);
        return new OwnerRef(user.getId().toString());
    }

    private void requireEnabled() {
        if (!enabled) throw new SessionException(ErrorCode.GAME_DISABLED);
    }

    private static CommandRequest commandRequest(CommandRequestDto body) {
        if (body == null || body.id() == null || body.expectedRevision() == null || body.command() == null) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        CommandRequestDto.CommandDto command = body.command();
        Command mapped = switch (command.type() == null ? "" : command.type()) {
            case "CHOOSE" -> new Command.Choose(command.optionId());
            case "ADVANCE" -> new Command.Advance();
            case "SUBMIT_TEXT" -> new Command.SubmitText(command.text());
            case "MOVE" -> new Command.Move(command.placeId());
            case "ABANDON" -> new Command.Abandon();
            default -> throw new SessionException(ErrorCode.REQUEST_INVALID);
        };
        if (mapped instanceof Command.SubmitText submit
                && submit.text() != null && submit.text().codePointCount(0, submit.text().length()) > 1000) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        return new CommandRequest(body.id(), body.expectedRevision(), mapped);
    }

    static RunSummaryDto toSummary(RunSummary summary) {
        RunSummaryDto.SourceDto source;
        if (summary.source() instanceof PackGameRef pack) {
            source = new RunSummaryDto.SourceDto("PACK", pack.packId().value(),
                    pack.packVersion().toString(), null, null, null);
        } else if (summary.source() instanceof DynamicGameRef dynamic) {
            source = new RunSummaryDto.SourceDto("DYNAMIC", null, null,
                    dynamic.profile().id().value(), dynamic.profile().version().toString(),
                    dynamic.gameFingerprint());
        } else {
            throw new SessionException(ErrorCode.RUN_UNRECOVERABLE);
        }
        return new RunSummaryDto(summary.runId().toString(), summary.status().name(), summary.revision(),
                source, summary.packId() == null ? null : summary.packId().value(), summary.packVersion(),
                summary.locale().value(),
                summary.endReason() == null ? null : EndReason.valueOf(summary.endReason()),
                summary.failureCode(), View.CURRENT_VIEW_SCHEMA, summary.view(), summary.updatedAt());
    }

    private static RunSummaryDto summary(RunSummary summary) { return toSummary(summary); }

    private static CommandOutcomeDto commandOutcome(CommandOutcome outcome) {
        return new CommandOutcomeDto(outcome.commandId(), outcome.outcome().name(), outcome.accepted(),
                outcome.appliedRevision(), outcome.revision(), outcome.status().name(), outcome.view(),
                View.CURRENT_VIEW_SCHEMA, outcome.events(), outcome.issues());
    }

    public record StartBody(UUID id, String packId, String locale, Boolean generated,
                            Map<String, String> startInputs, Long seed) {}
    public record StartResponse(UUID runId, String status, long revision, long retryAfterMs) {}
    public record ProcessingResponse(UUID commandId, String status, long revision, long retryAfterMs) {}
    public record EventResponse(long seq, long revision, Event event) {}
}
