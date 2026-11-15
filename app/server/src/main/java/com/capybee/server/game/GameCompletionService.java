package com.capybee.server.game;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Host-only bridge. Saved runs are its durable work source; reads never repair writes. */
@Service
public class GameCompletionService {
    public record CompletionDto(UUID runId, long revision, Instant completedAt, String suggestionKey,
            int suggestionVersion, GameCompletionRepository.Mission mission, boolean missionAccepted, boolean cellAdded) {}
    public record AcceptBody(UUID id, long expectedRevision, int suggestionVersion) {}
    private final JdbcSessionStore runs;
    private final GameCompletionRepository completions;
    private final GameCompletionPolicy policy;
    private final TransactionTemplate transaction;
    private final Executor workers;
    private final boolean enabled;
    public GameCompletionService(JdbcSessionStore runs, GameCompletionRepository completions, GameCompletionPolicy policy,
            PlatformTransactionManager transactions, @Qualifier("gameSessionWorkers") Executor workers,
            @Value("${capybee.game.completion.enabled:false}") boolean enabled) {
        this.runs=runs;this.completions=completions;this.policy=policy;
        this.transaction=new TransactionTemplate(transactions);this.workers=workers;this.enabled=enabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {schedule();}
    @TransactionalEventListener(fallbackExecution=true)
    public void saved(JdbcSessionStore.CompletedRunSaved event) {schedule();}
    private void schedule() {
        if (!enabled) return;
        try {workers.execute(()->{
            try {reconcile();}
            catch (RuntimeException failure) {System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"GAME_COMPLETION_RECONCILIATION_PENDING");}
        });} catch (RuntimeException unavailable) { /* Durable runs are retried at the next ending/startup. */ }
    }
    /** Startup and accepted-end reconciliation repair interruptions without a browser POST. */
    public void reconcile() {
        if (!enabled) return;
        for (var identity:completions.endedRuns()) {
            transaction.executeWithoutResult(status->{
                var run=runs.find(identity.runId(),new OwnerRef(identity.owner().toString())).orElse(null);
                if (!eligible(run)) return;
                var snapshot=run.snapshot();
                var suggestion=policy.suggest(run.packId().value(),run.packVersion(),snapshot.state().outcome(),snapshot.state().sceneId());
                if (suggestion.isEmpty()) return;
                var at=completions.lockCompletedRun(run.id(),identity.owner(),run.revision());
                if (at.isEmpty()) return;
                completions.insert(run.id(),identity.owner(),run.revision(),at.get(),suggestion.get());
                completions.find(run.id(),identity.owner(),true).ifPresent(completions::projectCell);
            });
        }
    }
    private boolean eligible(StoredRun run) {
        if (run==null || run.status()!=RunStatus.ENDED || run.packId()==null || run.snapshot()==null) return false;
        var s=run.snapshot();
        return !s.dynamicGame() && s.state().ended() && s.state().endReason()==EndReason.COMPLETED
                && s.runId().equals(run.id().toString()) && s.pack().id().equals(run.packId())
                && s.pack().version().toString().equals(run.packVersion());
    }
    private GameCompletionRepository.Completion require(UUID owner, UUID runId, boolean lock) {
        if (!enabled) throw new SessionException(ErrorCode.GAME_DISABLED);
        var c=completions.find(runId,owner,lock).filter(x->!x.archived())
                .orElseThrow(()->new SessionException(ErrorCode.RUN_NOT_FOUND));
        var run=runs.find(runId,new OwnerRef(owner.toString())).orElse(null);
        if (!eligible(run) || run.revision()!=c.revision()) throw new SessionException(ErrorCode.RUN_NOT_ACTIVE);
        var approved=policy.suggest(run.packId().value(),run.packVersion(),run.snapshot().state().outcome(),run.snapshot().state().sceneId());
        if (approved.isEmpty() || !approved.get().key().equals(c.suggestionKey()) || approved.get().version()!=c.suggestionVersion())
            throw new SessionException(ErrorCode.RUN_NOT_ACTIVE);
        return c;
    }
    public CompletionDto get(UUID owner, UUID runId, boolean polish) {
        if (!enabled) throw new SessionException(ErrorCode.GAME_DISABLED);
        if (completions.find(runId,owner,false).isPresent()) return dto(require(owner,runId,false),polish);
        var run=runs.find(runId,new OwnerRef(owner.toString())).orElse(null);
        if (eligible(run) && policy.suggest(run.packId().value(),run.packVersion(),run.snapshot().state().outcome(),run.snapshot().state().sceneId()).isPresent()) return null;
        throw new SessionException(ErrorCode.RUN_NOT_FOUND);
    }
    public CompletionDto accept(UUID owner, UUID runId, AcceptBody body, boolean mission, boolean polish) {
        if (body==null || body.id()==null || body.expectedRevision()<0 || body.suggestionVersion()<1)
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        return transaction.execute(status->{
            // Lock order matches reconciliation and deletion: run, then completion.
            if (completions.lockCompletedRun(runId,owner,body.expectedRevision()).isEmpty())
                throw new SessionException(ErrorCode.REVISION_CONFLICT);
            var c=require(owner,runId,true);
            if (body.expectedRevision()!=c.revision() || body.suggestionVersion()!=c.suggestionVersion())
                throw new SessionException(ErrorCode.REVISION_CONFLICT);
            if (mission) {
                if (!c.missionAccepted()) {
                    var selected=completions.mission(c.suggestionKey(),polish)
                            .orElseThrow(()->new SessionException(ErrorCode.RUN_NOT_ACTIVE));
                    completions.acceptMission(runId,selected.id());
                }
            } else {completions.requestCell(runId);}
            var saved=require(owner,runId,true);
            completions.projectCell(saved);
            return dto(saved,polish);
        });
    }
    public java.util.List<UUID> selectedMissions(UUID owner) {
        return enabled ? completions.selectedMissions(owner) : java.util.List.of();
    }
    public void archive(UUID owner, UUID runId) {
        transaction.executeWithoutResult(status->{
            var run=runs.find(runId,new OwnerRef(owner.toString())).orElseThrow(()->new SessionException(ErrorCode.RUN_NOT_FOUND));
            if (completions.lockCompletedRun(runId,owner,run.revision()).isEmpty()) throw new SessionException(ErrorCode.RUN_NOT_ACTIVE);
            var c=completions.find(runId,owner,true).orElseThrow(()->new SessionException(ErrorCode.RUN_NOT_FOUND));
            completions.archive(c.runId());
        });
    }
    private CompletionDto dto(GameCompletionRepository.Completion c, boolean polish) {
        var mission=completions.mission(c.suggestionKey(),polish)
                .filter(m->!c.missionAccepted() || m.id().equals(c.missionId())).orElse(null);
        return new CompletionDto(c.runId(),c.revision(),c.completedAt(),c.suggestionKey(),c.suggestionVersion(),
                mission,c.missionAccepted(),c.cellRequested());
    }
}
