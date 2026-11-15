package com.capybee.server.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GameCompletionRepository {
    public record Completion(UUID runId, UUID owner, long revision, Instant completedAt,
            String suggestionKey, int suggestionVersion, UUID missionId, boolean missionAccepted,
            boolean cellRequested, boolean archived) {}
    public record Mission(UUID id, String code, String title, String timeHint) {}
    public record Identity(UUID runId, UUID owner) {}
    private final JdbcTemplate jdbc;
    public GameCompletionRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}

    public List<Identity> endedRuns() {
        return jdbc.query("SELECT id,user_id FROM game_runs WHERE status='ENDED' AND end_reason='COMPLETED'",
                (rs,n)->new Identity(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)));
    }
    public Optional<Instant> lockCompletedRun(UUID runId, UUID owner, long revision) {
        return jdbc.query("""
                SELECT ended_at FROM game_runs WHERE id=? AND user_id=? AND revision=?
                AND status='ENDED' AND end_reason='COMPLETED' AND ended_at IS NOT NULL FOR UPDATE
                """,(rs,n)->rs.getTimestamp(1).toInstant(),runId,owner,revision).stream().findFirst();
    }
    public void insert(UUID runId, UUID owner, long revision, Instant at, GameCompletionPolicy.Suggestion suggestion) {
        jdbc.update("""
                INSERT INTO game_run_completions(run_id,user_id,revision,completed_at,suggestion_key,suggestion_version)
                VALUES(?,?,?,?,?,?) ON CONFLICT(run_id) DO NOTHING
                """,runId,owner,revision,Timestamp.from(at),suggestion.key(),suggestion.version());
    }
    public Optional<Completion> find(UUID runId, UUID owner, boolean lock) {
        return jdbc.query("SELECT * FROM game_run_completions WHERE run_id=? AND user_id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->new Completion(runId,owner,rs.getLong("revision"),rs.getTimestamp("completed_at").toInstant(),
                        rs.getString("suggestion_key"),rs.getInt("suggestion_version"),rs.getObject("mission_id",UUID.class),
                        rs.getBoolean("mission_accepted"),rs.getBoolean("cell_requested"),rs.getBoolean("archived")),runId,owner).stream().findFirst();
    }
    public Optional<Mission> mission(String code, boolean polish) {
        return jdbc.query("""
                SELECT id,code,title_en,title_pl,time_hint_en,time_hint_pl FROM missions WHERE code=? AND active=true
                """,(rs,n)->new Mission(rs.getObject("id",UUID.class),rs.getString("code"),
                        rs.getString(polish?"title_pl":"title_en"),rs.getString(polish?"time_hint_pl":"time_hint_en")),code).stream().findFirst();
    }
    public void acceptMission(UUID runId, UUID missionId) {
        jdbc.update("UPDATE game_run_completions SET mission_accepted=true,mission_id=? WHERE run_id=? AND mission_accepted=false",missionId,runId);
    }
    public void requestCell(UUID runId) {
        jdbc.update("UPDATE game_run_completions SET cell_requested=true WHERE run_id=?",runId);
    }
    public void projectCell(Completion c) {
        if (!c.cellRequested() || c.archived()) return;
        jdbc.update("""
                INSERT INTO game_results(id,user_id,game_key,duration_ms,metrics,completed_at,rpg_run_id)
                VALUES(gen_random_uuid(),?,'capybee_rpg',0,'{}'::jsonb,?,?) ON CONFLICT(rpg_run_id) DO NOTHING
                """,c.owner(),Timestamp.from(c.completedAt()),c.runId());
    }
    public List<UUID> selectedMissions(UUID owner) {
        return jdbc.query("""
                SELECT DISTINCT c.mission_id FROM game_run_completions c
                JOIN missions m ON m.id=c.mission_id AND m.active=true
                JOIN game_runs r ON r.id=c.run_id AND r.user_id=c.user_id
                WHERE c.user_id=? AND c.mission_accepted=true AND c.archived=false
                    AND r.status='ENDED' AND r.end_reason='COMPLETED'
                """,(rs,n)->rs.getObject(1,UUID.class),owner);
    }
    public void archive(UUID runId) {
        jdbc.update("UPDATE game_run_completions SET archived=true,mission_id=NULL WHERE run_id=?",runId);
        jdbc.update("DELETE FROM game_results WHERE rpg_run_id=?",runId);
    }
}
