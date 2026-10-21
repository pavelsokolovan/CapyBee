package com.capybee.engine.session.adapter;

import com.capybee.engine.api.Event;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.GenerationCatalog;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.session.MeteredTextGenerator;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.StoreException;
import com.capybee.engine.session.wire.SessionJson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static com.capybee.engine.session.SessionPorts.*;
import static com.capybee.engine.session.SessionTypes.*;

/** One durable, process-locked document for all local session ports. */
public final class LocalSessionStore implements SessionPorts.SessionStore, AutoCloseable {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Path dir, file, tmp, keyFile;
    private final Clock clock; private final ReentrantLock lock = new ReentrantLock();
    private final FileChannel lockChannel; private final FileLock fileLock; private final byte[] commandKey;
    private final ThreadLocal<Integer> transactions = ThreadLocal.withInitial(() -> 0);
    private boolean closed; private byte[] committed;
    private State state;

    public static LocalSessionStore open(Path directory, Clock clock) {
        Path dir = directory.toAbsolutePath().normalize(); FileChannel channel = null; FileLock held = null;
        try {
            if (Files.exists(dir) && !Files.isDirectory(dir)) throw new StoreException(StoreException.Code.STORE_READ_FAILED);
            Files.createDirectories(dir); channel = FileChannel.open(dir.resolve("session-store.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try { held = channel.tryLock(); } catch (OverlappingFileLockException ex) { throw new StoreException(StoreException.Code.STORE_LOCKED); }
            if (held == null) throw new StoreException(StoreException.Code.STORE_LOCKED);
            return new LocalSessionStore(dir, clock, channel, held);
        } catch (StoreException ex) { closeQuietly(held, channel); throw ex;
        } catch (IOException ex) { closeQuietly(held, channel); throw new StoreException(StoreException.Code.STORE_READ_FAILED, ex); }
    }
    private LocalSessionStore(Path dir, Clock clock, FileChannel channel, FileLock held) throws IOException {
        this.dir=dir; this.file=dir.resolve("session-store.json"); this.tmp=dir.resolve("session-store.json.tmp"); this.keyFile=dir.resolve("session-store.key");
        this.clock=clock; this.lockChannel=channel; this.fileLock=held;
        if (!Files.exists(file) && hasLegacyFiles(dir)) throw new StoreException(StoreException.Code.STORE_LEGACY_LAYOUT);
        this.state = Files.exists(file) ? decode(Files.readAllBytes(file)) : new State();
        this.committed = Files.exists(file) ? Files.readAllBytes(file) : null;
        this.commandKey = readOrCreateKey();
        Files.deleteIfExists(tmp);
    }
    public byte[] commandKey() { guard(); return commandKey.clone(); }
    @Override public void close() { lock.lock(); try { if (!closed) { closed=true; fileLock.release(); lockChannel.close(); } } catch(IOException e){ throw new StoreException(StoreException.Code.STORE_WRITE_FAILED,e); } finally { lock.unlock(); } }

    @Override public CreateOutcome createPreparing(NewRun n) { return readWrite(() -> { StoredRun old=state.runs.get(n.id()); if(old!=null)return new CreateOutcome(old.owner().equals(n.owner())?CreateKind.EXISTS_SAME_OWNER:CreateKind.EXISTS_OTHER_OWNER,old.id());
        for(StoredRun r:state.runs.values()) if(r.owner().equals(n.owner())&&(r.status()==RunStatus.PREPARING||r.status()==RunStatus.ACTIVE)) return new CreateOutcome(CreateKind.OWNER_HAS_ACTIVE,r.id());
        state.runs.put(n.id(),new StoredRun(n.id(),n.owner(),n.packId(),n.locale(),RunStatus.PREPARING,0,null,new StartRequest(n.id(),n.packId(),n.locale(),n.startInputs(),n.seed(),n.generated()),null,null,0,n.createdAt(),n.createdAt())); return new CreateOutcome(CreateKind.CREATED,n.id()); }); }
    @Override public Optional<StoredRun> find(UUID id, OwnerRef owner) { return read(() -> Optional.ofNullable(state.runs.get(id)).filter(r->r.owner().equals(owner)).map(this::withCalls)); }
    @Override public Optional<StoredRun> findCurrent(OwnerRef owner) { return read(() -> state.runs.values().stream().filter(r->r.owner().equals(owner)&&(r.status()==RunStatus.PREPARING||r.status()==RunStatus.ACTIVE)).max(Comparator.comparing(StoredRun::updatedAt)).map(this::withCalls)); }
    @Override public Optional<Lease> tryClaim(UUID id, OwnerRef owner, Duration ttl) { return readWrite(() -> { StoredRun r=state.runs.get(id); Instant now=clock.instant(); if(r==null||!r.owner().equals(owner)||(r.status()!=RunStatus.PREPARING&&r.status()!=RunStatus.ACTIVE)||(r.lease()!=null&&r.lease().until().isAfter(now)))return Optional.empty(); Lease l=new Lease(UUID.randomUUID(),now.plus(ttl)); put(copy(r,r.status(),r.revision(),r.snapshot(),r.pendingStart(),l,r.failureCode(),now)); return Optional.of(l); }); }
    @Override public boolean saveActive(UUID id, Lease l,long expected,RunSnapshot snap,List<Event> ignored){return readWrite(()->{StoredRun r=state.runs.get(id);if(!hasLease(r,l)||r.revision()!=expected)return false;put(copy(r,snap.state().ended()?RunStatus.ENDED:RunStatus.ACTIVE,r.status()==RunStatus.PREPARING?expected:expected+1,snap,null,null,null,clock.instant()));return true;});}
    @Override public boolean markFailed(UUID id,Lease l,String code){return readWrite(()->{StoredRun r=state.runs.get(id);if(!hasLease(r,l))return false;put(copy(r,RunStatus.FAILED,r.revision(),null,null,null,code,clock.instant()));return true;});}
    @Override public void releaseLease(UUID id,Lease l){readWrite(()->{StoredRun r=state.runs.get(id);if(hasLease(r,l))put(copy(r,r.status(),r.revision(),r.snapshot(),r.pendingStart(),null,r.failureCode(),clock.instant()));return null;});}
    @Override public void delete(UUID id,OwnerRef owner){readWrite(()->{StoredRun r=state.runs.get(id);if(r!=null&&r.owner().equals(owner)){state.runs.remove(id);state.commands.remove(id);state.events.remove(id);state.prefetch.remove(id);state.runReservations.remove(id.toString());state.runPrefetchCalls.remove(id.toString());}return null;});}
    @Override public Optional<LoggedCommand> find(UUID r,UUID c){return read(()->Optional.ofNullable(state.commands.getOrDefault(r,Map.of()).get(c)));}
    @Override public InsertKind begin(UUID r,UUID c,byte[] mac,long before){return readWrite(()->{if(!state.runs.containsKey(r))throw new IllegalStateException("RUN_NOT_FOUND");Map<UUID,LoggedCommand> m=state.commands.computeIfAbsent(r,x->new LinkedHashMap<>());if(m.containsKey(c))return InsertKind.EXISTS;m.put(c,new LoggedCommand(mac,CommandState.IN_PROGRESS,null,before,null,List.of(),null));return InsertKind.INSERTED;});}
    @Override public void complete(UUID r,UUID c,boolean accepted,long after,List<Event> es,Long latencyMillis,SceneSource sceneSource){readWrite(()->{Map<UUID,LoggedCommand> m=state.commands.get(r);LoggedCommand p=m==null?null:m.get(c);if(p==null)throw new IllegalStateException("COMMAND_NOT_BEGUN");m.put(c,new LoggedCommand(p.payloadMac(),CommandState.DONE,accepted,p.revisionBefore(),after,es,null,latencyMillis,sceneSource));return null;});}
    @Override public void fail(UUID r,UUID c,String code){readWrite(()->{Map<UUID,LoggedCommand> m=state.commands.get(r);if(m!=null&&m.containsKey(c)){LoggedCommand p=m.get(c);m.put(c,new LoggedCommand(p.payloadMac(),CommandState.FAILED,false,p.revisionBefore(),p.revisionAfter(),p.events(),code));}return null;});}
    @Override public List<CommandSummary> list(UUID r){return read(()->state.commands.getOrDefault(r,Map.of()).entrySet().stream().map(e->{LoggedCommand c=e.getValue();return new CommandSummary(e.getKey(),c.state(),c.accepted(),c.revisionBefore(),c.revisionAfter(),c.latencyMillis(),c.sceneSource());}).toList());}
    @Override public void append(UUID r,UUID c,long rev,List<Event> es){readWrite(()->{if(!state.runs.containsKey(r))throw new IllegalStateException("RUN_NOT_FOUND");List<OutboxEvent> list=state.events.computeIfAbsent(r,x->new ArrayList<>());for(Event e:es)list.add(new OutboxEvent(r,list.size()+1L,c,rev,e,clock.instant()));return null;});}
    @Override public List<OutboxEvent> readAfter(UUID r,long after,int limit){return read(()->{if(limit<1)throw new IllegalArgumentException("limit must be positive");return state.events.getOrDefault(r,List.of()).stream().filter(e->e.seq()>after).limit(limit).toList();});}
    @Override public List<PrefetchedArtifact> load(UUID r){return read(()->List.copyOf(state.prefetch.getOrDefault(r,new LinkedHashMap<>()).values()));}
    @Override public boolean insertIfAbsent(UUID r,String scene,long rev,GenerationArtifact a){return readWrite(()->{if(!state.runs.containsKey(r)||a.outcome()==GenerationArtifact.Outcome.FALLBACK)return false;return state.prefetch.computeIfAbsent(r,x->new LinkedHashMap<>()).putIfAbsent(scene,new PrefetchedArtifact(scene,rev,a))==null;});}
    @Override public void deleteForScenes(UUID r,Collection<String> ids){readWrite(()->{Map<String,PrefetchedArtifact> m=state.prefetch.get(r);if(m!=null)ids.forEach(m::remove);return null;});}
    @Override public boolean tryReserveCall(LedgerScope p,LedgerScope g,LocalDate d,UUID r,int limit,MeteredTextGenerator.Priority pri,int pr,int gr,int rr){return readWrite(()->{String pk=key(p.value(),d),gk=key(g.value(),d),rk=r.toString();int pc=state.reservations.getOrDefault(pk,0),gc=state.reservations.getOrDefault(gk,0),rc=state.runReservations.getOrDefault(rk,0);boolean denied=pri==MeteredTextGenerator.Priority.PREFETCH?(pc>=Math.max(0,p.dailyCallLimit()-pr)||gc>=Math.max(0,g.dailyCallLimit()-gr)||rc>=Math.max(0,limit-rr)):(pc>=p.dailyCallLimit()||gc>=g.dailyCallLimit()||rc>=limit);if(denied)return false;state.reservations.put(pk,pc+1);state.reservations.put(gk,gc+1);state.runReservations.put(rk,rc+1);if(pri==MeteredTextGenerator.Priority.PREFETCH){state.usage.computeIfAbsent(pk,x->new long[3])[2]++;state.usage.computeIfAbsent(gk,x->new long[3])[2]++;state.runPrefetchCalls.merge(rk,1,Integer::sum);}return true;});}
    @Override public void recordUsage(LedgerScope p,LedgerScope g,LocalDate d,UUID r,TextGenerationResult.TokenUsage u,MeteredTextGenerator.Priority pri){readWrite(()->{usage(key(p.value(),d),u);usage(key(g.value(),d),u);return null;});}
    @Override public void releaseCall(LedgerScope p,LedgerScope g,LocalDate d,UUID r){readWrite(()->{state.reservations.computeIfPresent(key(p.value(),d),(x,v)->Math.max(0,v-1));state.reservations.computeIfPresent(key(g.value(),d),(x,v)->Math.max(0,v-1));state.runReservations.computeIfPresent(r.toString(),(x,v)->Math.max(0,v-1));return null;});}
    @Override public DayUsage dayUsage(LedgerScope scope,LocalDate day){return read(()->{String k=key(scope.value(),day);long[] a=state.usage.getOrDefault(k,new long[3]);return new DayUsage(state.reservations.getOrDefault(k,0),a[0],a[1],Math.toIntExact(a[2]));});}
    @Override public int runPrefetchCalls(UUID runId){return read(()->state.runPrefetchCalls.getOrDefault(runId.toString(),0));}
    @Override public void execute(Runnable work){lock.lock();try{guard();int depth=transactions.get();transactions.set(depth+1);byte[] before=encode();try{work.run();if(depth==0)commit();}catch(Throwable x){if(depth==0)restore(before);throw x;}finally{transactions.set(depth);}}finally{lock.unlock();}}

    private <T>T read(java.util.function.Supplier<T> f){lock.lock();try{guard();return f.get();}finally{lock.unlock();}} private <T>T readWrite(java.util.function.Supplier<T> f){lock.lock();try{guard();T v=f.get();if(transactions.get()==0)commit();return v;}finally{lock.unlock();}}
    private void commit(){byte[] bytes=encode();if(bytes.length>MAX_BYTES)throw new StoreException(StoreException.Code.STORE_TOO_LARGE);if(Arrays.equals(bytes,committed))return;try{Files.write(tmp,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}committed=bytes;}catch(IOException e){try{Files.deleteIfExists(tmp);}catch(IOException ignored){}restore(committed);throw new StoreException(StoreException.Code.STORE_WRITE_FAILED,e);}}
    private byte[] encode(){List<RunData> rs=new ArrayList<>();for(StoredRun r:state.runs.values())rs.add(new RunData(r, state.commands.getOrDefault(r.id(),Map.of()),state.events.getOrDefault(r.id(),List.of()),state.prefetch.getOrDefault(r.id(),Map.of())));return SessionJson.encode(new Document("capybee-session-store",2,rs,state.reservations,state.runReservations,state.usage,state.runPrefetchCalls)).getBytes(StandardCharsets.UTF_8);}
    private State decode(byte[] bytes){if(bytes==null)return new State();if(bytes.length>MAX_BYTES)throw new StoreException(StoreException.Code.STORE_CORRUPT);try{Document d=SessionJson.decode(new String(bytes,StandardCharsets.UTF_8),Document.class);if(!"capybee-session-store".equals(d.format()))throw new StoreException(StoreException.Code.STORE_CORRUPT);if(d.version()>2)throw new StoreException(StoreException.Code.STORE_VERSION_UNSUPPORTED,Map.of("found",d.version(),"supported",2));if(d.version()<1)throw new StoreException(StoreException.Code.STORE_CORRUPT);d=StoreMigrations.toCurrent(d);State s=new State();s.reservations.putAll(d.reservations());s.runReservations.putAll(d.runReservations());s.usage.putAll(d.usage());s.runPrefetchCalls.putAll(d.runPrefetchCalls());for(RunData x:d.runs()){StoredRun r=x.run();if(r==null||s.runs.putIfAbsent(r.id(),r)!=null)throw new StoreException(StoreException.Code.STORE_CORRUPT);Map<UUID,LoggedCommand> runCommands=new LinkedHashMap<>(x.commands()==null?Map.of():x.commands());for(LoggedCommand c:runCommands.values())validateCommand(c);s.commands.put(r.id(),runCommands);s.events.put(r.id(),new ArrayList<>(x.events()==null?List.of():x.events()));s.prefetch.put(r.id(),new LinkedHashMap<>(x.prefetch()==null?Map.of():x.prefetch()));}for(var entry:s.runPrefetchCalls.entrySet())if(!s.runs.containsKey(UUID.fromString(entry.getKey()))||entry.getValue()<0)throw new StoreException(StoreException.Code.STORE_CORRUPT);for(long[] a:s.usage.values())if(a==null||a.length!=3||Arrays.stream(a).anyMatch(v->v<0))throw new StoreException(StoreException.Code.STORE_CORRUPT);return s;}catch(StoreException e){throw e;}catch(RuntimeException e){throw new StoreException(StoreException.Code.STORE_CORRUPT);}}
    private void validateCommand(LoggedCommand command){if(command==null||command.latencyMillis()!=null&&command.latencyMillis()<0)throw new StoreException(StoreException.Code.STORE_CORRUPT);if(command.state()!=CommandState.DONE&&(command.latencyMillis()!=null||command.sceneSource()!=null))throw new StoreException(StoreException.Code.STORE_CORRUPT);}
    private void usage(String k,TextGenerationResult.TokenUsage u){long[] a=state.usage.computeIfAbsent(k,x->new long[3]);if(u!=null){a[0]+=u.inputTokens();a[1]+=u.outputTokens();}}
    private void restore(byte[] bytes){state=decode(bytes);}
    private byte[] readOrCreateKey() throws IOException {if(Files.exists(keyFile)){byte[] k=Base64.getDecoder().decode(Files.readString(keyFile).trim());if(k.length!=32)throw new StoreException(StoreException.Code.STORE_CORRUPT);return k;}byte[] k=new byte[32];new SecureRandom().nextBytes(k);Files.writeString(keyFile,Base64.getEncoder().encodeToString(k),StandardOpenOption.CREATE_NEW);return k;}
    private void put(StoredRun r){state.runs.put(r.id(),r);}
    private StoredRun withCalls(StoredRun r) { return new StoredRun(r.id(),r.owner(),r.packId(),r.locale(),r.status(),r.revision(),r.snapshot(),r.pendingStart(),r.lease(),r.failureCode(),state.runReservations.getOrDefault(r.id().toString(),0),r.createdAt(),r.updatedAt()); }
    private static StoredRun copy(StoredRun r,RunStatus st,long rev,RunSnapshot sn,StartRequest ps,Lease l,String fail,Instant now){return new StoredRun(r.id(),r.owner(),r.packId(),r.locale(),st,rev,sn,ps,l,fail,r.generationCalls(),r.createdAt(),now);} private static boolean hasLease(StoredRun r,Lease l){return r!=null&&r.lease()!=null&&l!=null&&r.lease().token().equals(l.token());} private static String key(String s,LocalDate d){return s+":"+d;} private void usage(String k,TextGenerationResult.TokenUsage u,MeteredTextGenerator.Priority p){long[] a=state.usage.computeIfAbsent(k,x->new long[3]);a[0]+=u.inputTokens();a[1]+=u.outputTokens();if(p==MeteredTextGenerator.Priority.PREFETCH)a[2]++;} private void guard(){if(closed)throw new StoreException(StoreException.Code.STORE_CLOSED);} private static boolean hasLegacyFiles(Path d)throws IOException{try(var files=Files.list(d)){return files.anyMatch(p->p.getFileName().toString().matches("[0-9a-fA-F-]{36}\\\\.json"));}} private static void closeQuietly(FileLock l,FileChannel c){try{if(l!=null)l.release();}catch(IOException ignored){}try{if(c!=null)c.close();}catch(IOException ignored){}}
    private static final class State {final Map<UUID,StoredRun> runs=new LinkedHashMap<>();final Map<UUID,Map<UUID,LoggedCommand>> commands=new HashMap<>();final Map<UUID,List<OutboxEvent>> events=new HashMap<>();final Map<UUID,Map<String,PrefetchedArtifact>> prefetch=new HashMap<>();final Map<String,Integer> reservations=new HashMap<>(),runReservations=new HashMap<>(),runPrefetchCalls=new HashMap<>();final Map<String,long[]> usage=new HashMap<>();}
    public record Document(String format,int version,List<RunData> runs,Map<String,Integer> reservations,Map<String,Integer> runReservations,Map<String,long[]> usage,Map<String,Integer> runPrefetchCalls){public Document(String format,int version,List<RunData> runs,Map<String,Integer> reservations,Map<String,Integer> runReservations,Map<String,long[]> usage){this(format,version,runs,reservations,runReservations,usage,Map.of());}} public record RunData(StoredRun run,Map<UUID,LoggedCommand> commands,List<OutboxEvent> events,Map<String,PrefetchedArtifact> prefetch){}
}
