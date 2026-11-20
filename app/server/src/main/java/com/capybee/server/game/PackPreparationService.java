package com.capybee.server.game;

import com.capybee.engine.port.TextGenerator;
import org.springframework.beans.factory.ObjectProvider;

import com.capybee.engine.api.*;
import com.capybee.engine.input.*;
import com.capybee.engine.pack.*;
import com.capybee.engine.session.*;
import com.capybee.engine.session.SessionTypes.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Transient preparation: only public operation identity and accepted content can be saved. */
@Service
public final class PackPreparationService {
    public record Body(UUID requestId,String packId,String packVersion,String packFingerprint,String locale,
                       Map<String,List<String>> selections,String suggestion,long disclosureRevision,
                       boolean confirmed,UUID expectedCurrentRunId) {
        @Override public String toString(){return "PackPreparation[redacted]";}
    }
    public record Result(String status,long retryAfterMs,UUID runId,String code) {}
    private record Key(OwnerRef owner,UUID id) {}
    private static final class Operation {
        final CompletableFuture<Result> future=new CompletableFuture<>();
        final long created=System.nanoTime();
        final String signature;
        boolean cancelled,published;
        Operation(String signature){this.signature=signature;}
    }
    private final ConcurrentHashMap<Key,Operation> operations=new ConcurrentHashMap<>();
    private final SessionPorts.EngineFactory engines;
    private final SessionPorts.PackCatalog packs;
    private final JdbcSessionStore store;
    private final ReviewedInputGuard guard;
    private final Executor workers;
    private final boolean enabled;
    private final Semaphore slots;
    public PackPreparationService(SessionPorts.EngineFactory engines,SessionPorts.PackCatalog packs,
            JdbcSessionStore store,ReviewedInputGuard guard,@Qualifier("gameSessionWorkers") Executor workers,
            @Value("${capybee.game.pack-preparation.provider-approved:false}") boolean approved,
            @Value("${capybee.game.generation-enabled:false}") boolean generation, ObjectProvider<TextGenerator> generators,
            @Value("${capybee.game.max-concurrent-generation:2}") int concurrent) {
        this.engines=engines;this.packs=packs;this.store=store;this.guard=guard;this.workers=workers;
        this.enabled=approved&&generation&&generators.getIfAvailable()!=null;this.slots=new Semaphore(Math.max(1,concurrent));
    }
    public boolean enabled(){return enabled;}
    public static String fingerprint(Pack pack){
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            var manifest=pack.manifest();
            hash.update((manifest.id().value()+":"+manifest.version()+":"+manifest.schemaVersion()).getBytes(StandardCharsets.UTF_8));
            for(Object field:java.util.Arrays.asList(manifest.name(),manifest.description(),manifest.locales(),manifest.defaultLocale(),manifest.startInputs(),manifest.events(),manifest.contentPolicy(),manifest.features().stream().map(Enum::name).sorted().toList())){
                hash.update(com.capybee.engine.session.wire.SessionJson.encode(field).getBytes(StandardCharsets.UTF_8));hash.update((byte)0);
            }
            new TreeMap<>(pack.files()).forEach((key,value)->{hash.update(key.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);hash.update(value.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);});
            return HexFormat.of().formatHex(hash.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public boolean exists(OwnerRef owner,UUID id){return operations.containsKey(new Key(owner,id));}
    public Result submit(OwnerRef owner,Body body,Duration wait){
        if(!enabled)throw new SessionException(ErrorCode.GENERATION_UNAVAILABLE);
        if(body==null||body.requestId()==null||!body.confirmed()||body.disclosureRevision()!=1||body.locale()==null||body.packId()==null)
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        Pack pack=packs.find(body.packId()).orElseThrow(()->new SessionException(ErrorCode.REQUEST_INVALID));
        if(!pack.manifest().version().toString().equals(body.packVersion())||!fingerprint(pack).equals(body.packFingerprint())
                ||pack.generatedGameProfile().filter(p->p.kind()==com.capybee.engine.generation.GeneratedGameProfile.Kind.AUTHORED_GRAPH).isEmpty()
                ||pack.manifest().locales().stream().noneMatch(tag->tag.value().equals(body.locale()))
                ||body.suggestion()!=null&&body.suggestion().codePointCount(0,body.suggestion().length())>240)
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        Map<String,String> inputs=new LinkedHashMap<>();
        if(body.selections()!=null)body.selections().forEach((key,values)->{
            var definition=pack.manifest().startInputs().stream().filter(i->i.id().equals(key)&&!(i instanceof StartInputDef.FreeTextInput)).findFirst();
            if(definition.isEmpty()||values==null||values.size()>4)throw new SessionException(ErrorCode.REQUEST_INVALID);
            inputs.put(key,String.join(",",values));
        });
        // Validate public selections before creating a registry entry; never hash or retain the wish there.
        var publicInputs=new StartInputProcessor().prepare(pack,new LocaleTag(body.locale()),inputs,guard);
        if(!publicInputs.ok())throw new SessionException(ErrorCode.REQUEST_INVALID);
        UUID durableId=UUID.nameUUIDFromBytes((owner.value()+":pack:"+body.requestId()).getBytes(StandardCharsets.UTF_8));
        var accepted=store.find(durableId,owner);
        if(accepted.isPresent()){
            var snapshot=accepted.get().snapshot();
            if(snapshot==null||snapshot.dynamicGame()||!snapshot.pack().id().equals(pack.manifest().id())||!snapshot.pack().version().equals(pack.manifest().version())||!snapshot.locale().value().equals(body.locale())||!snapshot.startInputs().equals(publicInputs.persistentValues()))throw new SessionException(ErrorCode.REQUEST_INVALID);
            return new Result("STARTED",0,durableId,null);
        }
        String signature=pack.manifest().version()+":"+fingerprint(pack)+":"+body.locale()+":"+new TreeMap<>(inputs)+":"+body.expectedCurrentRunId();
        long now=System.nanoTime();operations.entrySet().removeIf(e->e.getValue().future.isDone()&&now-e.getValue().created>Duration.ofMinutes(10).toNanos());
        if(operations.size()>=1000&&!exists(owner,body.requestId()))return failed("PACK_START_BUSY");
        var candidate=new Operation(signature);var existing=operations.putIfAbsent(new Key(owner,body.requestId()),candidate);
        var operation=existing==null?candidate:existing;
        if(!operation.signature.equals(signature))throw new SessionException(ErrorCode.REQUEST_INVALID);
        if(existing==null){
            try{workers.execute(()->prepare(owner,body,pack,inputs,operation));}
            catch(RuntimeException rejected){operation.future.complete(failed("PACK_START_BUSY"));}
        }
        try{return operation.future.get(Math.max(0,wait.toMillis()),TimeUnit.MILLISECONDS);}
        catch(TimeoutException timeout){return processing();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();return processing();}
        catch(ExecutionException failure){return failed("PACK_START_UNAVAILABLE");}
    }
    private void prepare(OwnerRef owner,Body body,Pack pack,Map<String,String> inputs,Operation operation){
        UUID runId=UUID.nameUUIDFromBytes((owner.value()+":pack:"+body.requestId()).getBytes(StandardCharsets.UTF_8));
        boolean acquired=false;
        try{
            if(operation.future.isDone())return;
            if(body.suggestion()!=null)inputs.put("story-wish",body.suggestion());
            var decision=new java.util.concurrent.atomic.AtomicReference<InputSafetyDecision>();
            var prepared=new StartInputProcessor().prepare(pack,new LocaleTag(body.locale()),inputs,(text,policy)->{
                var inspected=guard.inspect(text,policy);decision.set(inspected);return inspected;
            });
            inputs.clear();
            if(!prepared.ok()){
                operation.future.complete(new Result("NEEDS_INPUT",0,null,decision.get()==null?"INPUT_BLOCKED":decision.get().reasonCode()));return;
            }
            if(!(acquired=slots.tryAcquire())){operation.future.complete(failed("PACK_START_BUSY"));return;}
            if(operation.future.isDone())return;
            var engine=engines.create(owner,runId,MeteredTextGenerator.Priority.DEMAND);
            var result=TransientGenerationPrivacy.within(body.suggestion(),()->operation.future.isDone()||System.nanoTime()-operation.created>Duration.ofMinutes(3).toNanos(),()->engine.start(new RunRequest(pack.manifest().id(),new LocaleTag(body.locale()),prepared.persistentValues(),null,true),prepared));
            if(!result.ok()){operation.future.complete(failed("PACK_START_UNAVAILABLE"));return;}
            var s=result.run().snapshot();
            var snapshot=new RunSnapshot(s.snapshotVersion(),runId.toString(),s.pack(),s.locale(),s.seed(),s.startInputs(),s.createdAt(),s.updatedAt(),s.step(),s.state());
            if(!engine.resume(snapshot).ok())throw new IllegalStateException("PACK_SNAPSHOT_INVALID");
            synchronized(operation){
                if(operation.cancelled||System.nanoTime()-operation.created>Duration.ofMinutes(3).toNanos()){
                    operation.future.complete(new Result("CANCELLED",0,null,"PACK_START_CANCELLED"));return;
                }
                store.publishPreparedPack(owner,runId,body.expectedCurrentRunId(),snapshot);
                operation.published=true;operation.future.complete(new Result("STARTED",0,runId,null));
            }
        }catch(Exception failure){operation.future.complete(failed("PACK_START_UNAVAILABLE"));}
        finally{inputs.clear();store.forgetProvisional(runId);if(acquired)slots.release();}
    }
    public Result status(OwnerRef owner,UUID id){
        var operation=operations.get(new Key(owner,id));
        if(operation==null){
            UUID runId=UUID.nameUUIDFromBytes((owner.value()+":pack:"+id).getBytes(StandardCharsets.UTF_8));
            if(store.find(runId,owner).filter(r->r.snapshot()!=null&&!r.snapshot().dynamicGame()).isPresent())return new Result("STARTED",0,runId,null);
            throw new SessionException(ErrorCode.RUN_NOT_FOUND);
        }
        if(!operation.future.isDone() && System.nanoTime()-operation.created>Duration.ofMinutes(3).toNanos())
            synchronized(operation){if(!operation.published){operation.cancelled=true;operation.future.complete(new Result("CANCELLED",0,null,"PACK_START_CANCELLED"));}}
        return operation.future.getNow(processing());
    }
    public Result cancel(OwnerRef owner,UUID id){
        var operation=operations.get(new Key(owner,id));if(operation==null)throw new SessionException(ErrorCode.RUN_NOT_FOUND);
        synchronized(operation){if(!operation.published){operation.cancelled=true;operation.future.complete(new Result("CANCELLED",0,null,"PACK_START_CANCELLED"));}}
        return status(owner,id);
    }
    private static Result processing(){return new Result("PROCESSING",1000,null,null);}
    private static Result failed(String code){return new Result("FAILED",0,null,code);}
}
