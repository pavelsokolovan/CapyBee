package com.capybee.engine.session;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.api.*;
import com.capybee.engine.generation.blueprint.*;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.InputGuard;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.SessionTypes.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Session-owned trusted dynamic start, including identity, metering, single-flight and activation. */
public final class DynamicStartCoordinator {
    public sealed interface Result permits Started,NeedsInput,FallbackOffered,Failed,Cancelled {}
    public record Started(RunSummary run) implements Result {}
    public record NeedsInput(String code) implements Result {}
    public record FallbackOffered(String code) implements Result {}
    public record Failed(String code) implements Result {}
    public record Cancelled(String code) implements Result {}
    private record Operation(String signature,CompletableFuture<Result> result) {}
    private final RunSessionService sessions; private final SessionGenerators generators;
    private final BlueprintOutputReviewer reviewer; private final InputGuard guard; private final IdSource ids;
    private final Clock clock; private final ConcurrentHashMap<UUID,Operation> operations=new ConcurrentHashMap<>();
    public DynamicStartCoordinator(RunSessionService sessions,SessionGenerators generators,
            BlueprintOutputReviewer reviewer,InputGuard guard,IdSource ids,Clock clock){
        this.sessions=Objects.requireNonNull(sessions);this.generators=Objects.requireNonNull(generators);
        this.reviewer=Objects.requireNonNull(reviewer);this.guard=guard;this.ids=Objects.requireNonNull(ids);this.clock=Objects.requireNonNull(clock);
    }
    public Result startDynamic(OwnerRef owner,UUID requestId,ConfirmedDynamicPreparation preparation,
            Long seed,UUID expectedCurrentRunId,Duration wait){
        if(owner==null||requestId==null||preparation==null||wait==null||wait.isNegative()||wait.isZero())return new Failed("DYNAMIC_REQUEST_INVALID");
        String signature=preparation.signature()+"|"+seed+"|"+expectedCurrentRunId;
        Operation candidate=new Operation(signature,new CompletableFuture<>()), operation=operations.putIfAbsent(requestId,candidate);
        if(operation!=null){if(!operation.signature.equals(signature))return new Failed("DYNAMIC_REQUEST_CONFLICT");return operation.result.join();}
        try{Result result=execute(owner,requestId,preparation,seed,expectedCurrentRunId,wait);candidate.result.complete(result);return result;}
        catch(RuntimeException failure){Result result=new Failed("DYNAMIC_START_UNAVAILABLE");candidate.result.complete(result);return result;}
    }
    private Result execute(OwnerRef owner,UUID requestId,ConfirmedDynamicPreparation p,Long seed,UUID expected,Duration wait){
        UUID runId=UUID.nameUUIDFromBytes(("dynamic:"+requestId).getBytes(StandardCharsets.UTF_8));
        var scope=new LedgerScope("profile:"+p.profile().identity().id().value()+":"+p.profile().identity().version(),
                p.profile().profile().generationBudget().totalCallsPerRun());
        TextGenerator metered=generators.forRun(scope,LocalDate.now(clock),runId,MeteredTextGenerator.Priority.DEMAND);
        var request=new BlueprintGenerationRequest(requestId.toString(),p.revision(),p.revision(),p.profile(),p.preferences(),p.constraints(),
                p.suggestion(),guard,BlueprintCancellation.NEVER,clock.instant().plus(wait));
        var service=new BlueprintGenerationService(metered,reviewer,new DefaultBlueprintSemanticValidator(),ids,BlueprintFallbackResolver.NONE,clock);
        Engine engine=Engine.builder(new PackRegistry(List.of())).clock(clock)
                .random(Randoms.seeded(seed==null?runId.getMostSignificantBits():seed)).ids(ids)
                .textGenerator(metered).inputGuard(guard).dynamicBlueprints(service).build();
        DynamicGameBootstrapResult boot=engine.startDynamic(new DynamicGameBootstrapRequest(request,seed));
        if(boot instanceof DynamicGameBootstrapResult.FallbackOffered)return new FallbackOffered("DYNAMIC_FALLBACK_OFFERED");
        if(boot instanceof DynamicGameBootstrapResult.Failed failed){String code=failed.code();if(code.startsWith("INPUT_")||code.contains("INPUT"))return new NeedsInput(code);if(code.contains("CANCEL"))return new Cancelled(code);return new Failed(code);}
        RunSnapshot snapshot=((DynamicGameBootstrapResult.Started)boot).run().snapshot();
        if(!snapshot.dynamicGame()||snapshot.dynamic()==null)return new Failed("DYNAMIC_SNAPSHOT_INVALID");
        RunSummary summary=expected==null?sessions.activateDynamic(owner,runId,snapshot):sessions.replaceWithDynamic(owner,expected,runId,snapshot);
        return new Started(summary);
    }
}
