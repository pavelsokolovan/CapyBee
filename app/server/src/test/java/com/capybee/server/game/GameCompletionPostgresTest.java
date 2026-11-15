package com.capybee.server.game;

import com.capybee.engine.api.*;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.*;
import com.capybee.engine.session.SessionException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="CI",matches="true")
class GameCompletionPostgresTest {
    String schema;JdbcTemplate jdbc;GameCompletionService service;GameCompletionRepository repository;
    UUID owner,runId; final Instant ended=Instant.parse("2026-11-15T23:59:59Z");
    @BeforeEach void setup() {
        schema="completion_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource("jdbc:postgresql://localhost:5432/capybee","capybee","capybee")
                .schemas(schema).defaultSchema(schema).target("13").load().migrate();
        var ds=new DriverManagerDataSource("jdbc:postgresql://localhost:5432/capybee?currentSchema="+schema,"capybee","capybee");
        jdbc=new JdbcTemplate(ds);
        owner=UUID.randomUUID();runId=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,google_subject,email,display_name,created_at,updated_at) VALUES(?,?,?,'Fixture',now(),now())",owner,owner.toString(),owner+"@example.invalid");
        // A forward upgrade preserves untrusted legacy rows without treating them as proof.
        jdbc.update("INSERT INTO game_results(id,user_id,game_key,duration_ms,metrics,completed_at) VALUES(?,?,'capybee_rpg',99,'{}',now())",UUID.randomUUID(),owner);
        Flyway.configure().dataSource(ds).schemas(schema).defaultSchema(schema).load().migrate();
        repository=new GameCompletionRepository(jdbc);
        service=newService(true);
    }
    GameCompletionService newService(boolean enabled) {
        var tx=new DataSourceTransactionManager(jdbc.getDataSource());
        return new GameCompletionService(new JdbcSessionStore(jdbc,tx,Clock.systemUTC()),repository,new GameCompletionPolicy(),tx,Runnable::run,enabled);
    }
    @AfterEach void cleanup() {
        jdbc.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    void run(String status,String reason,String outcome) {
        var state=new RunState("ending",List.of("ending"),true,EndReason.valueOf(reason),Map.of(),Map.of(),Map.of(),Map.of(),outcome);
        var snapshot=new RunSnapshot(1,runId.toString(),new PackRef(new PackId("lanterns-of-willow-marsh"),new PackVersion(1,0,0)),new LocaleTag("en"),1,Map.of(),ended.minusSeconds(30),ended,3,state);
        jdbc.update("""
                INSERT INTO game_runs(id,user_id,source_kind,pack_id,pack_version,locale,status,end_reason,revision,snapshot,created_at,updated_at,ended_at)
                VALUES(?,?,'PACK','lanterns-of-willow-marsh','1.0.0','en',?,?,3,?::jsonb,?,?,?)
                """,runId,owner,status,reason,SnapshotCodec.toJson(snapshot),java.sql.Timestamp.from(ended.minusSeconds(30)),java.sql.Timestamp.from(ended),java.sql.Timestamp.from(ended));
    }
    GameCompletionService.AcceptBody body(long revision) {return new GameCompletionService.AcceptBody(UUID.randomUUID(),revision,1);}
    int projections() {return jdbc.queryForObject("SELECT count(*) FROM game_results WHERE rpg_run_id IS NOT NULL",Integer.class);}
    @Test void reconstructsAfterInterruptionAndKeepsReadsPure() {
        run("ENDED","COMPLETED","radiant-festival");
        assertThat(service.get(owner,runId,false)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_run_completions",Integer.class)).isZero();
        service=newService(true);service.ready();
        var offer=service.get(owner,runId,true);
        assertThat(offer.completedAt()).isEqualTo(ended);assertThat(offer.mission().code()).isEqualTo("say_thank_you_helper");
        assertThat(offer.mission().title()).contains("Podziękuj");assertThat(projections()).isZero();
        service.accept(owner,runId,body(3),false,false);
        jdbc.update("DELETE FROM game_results WHERE rpg_run_id=?",runId);
        assertThat(service.get(owner,runId,false).cellAdded()).isTrue();assertThat(projections()).isZero();
        newService(true).ready();assertThat(projections()).isOne();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM game_results WHERE rpg_run_id=?",java.sql.Timestamp.class,runId).toInstant()).isEqualTo(ended);
        assertThat(jdbc.queryForObject("SELECT metrics::text FROM game_results WHERE rpg_run_id=?",String.class,runId)).isEqualTo("{}");
    }
    @Test void concurrentDevicesAndDuplicateAcceptanceNeverCompleteMissionOrDuplicateCell() throws Exception {
        run("ENDED","COMPLETED","gentle-lighting");service.reconcile();
        try(var workers=Executors.newFixedThreadPool(4)) {
            var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<12;i++){final boolean mission=i%2==0;jobs.add(workers.submit(()->service.accept(owner,runId,body(3),mission,false)));}
            for(var job:jobs)job.get(10,TimeUnit.SECONDS);
        }
        assertThat(projections()).isOne();
        var offer=service.get(owner,runId,false);assertThat(offer.missionAccepted()).isTrue();assertThat(offer.cellAdded()).isTrue();
        assertThat(service.selectedMissions(owner)).containsExactly(offer.mission().id());
        assertThat(service.selectedMissions(UUID.randomUUID())).isEmpty();
        assertThat(newService(false).selectedMissions(owner)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_completions",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_interactions",Integer.class)).isZero();
    }
    @Test void rejectsOtherOwnersRevisionsVersionsAbandonAndUnreviewedSources() {
        run("ENDED","COMPLETED","radiant-festival");service.reconcile();
        assertThatThrownBy(()->service.get(UUID.randomUUID(),runId,false)).isInstanceOf(SessionException.class);
        assertThatThrownBy(()->service.accept(UUID.randomUUID(),runId,body(3),true,false)).isInstanceOf(SessionException.class);
        assertThatThrownBy(()->service.accept(owner,runId,body(2),false,false)).isInstanceOf(SessionException.class);
        assertThatThrownBy(()->service.accept(owner,runId,new GameCompletionService.AcceptBody(UUID.randomUUID(),3,99),false,false)).isInstanceOf(SessionException.class);
        assertThatThrownBy(()->service.accept(owner,runId,null,false,false)).isInstanceOf(SessionException.class);
        assertThatThrownBy(()->newService(false).get(owner,runId,false)).isInstanceOf(SessionException.class);
        assertThat(projections()).isZero();
        jdbc.update("DELETE FROM game_runs WHERE id=?",runId);
        for(String status:List.of("ACTIVE","PREPARING","FAILED","SUSPENDED","CANCELLED")) {
            run(status,"COMPLETED","radiant-festival");service.reconcile();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM game_run_completions",Integer.class)).isZero();
            jdbc.update("DELETE FROM game_runs WHERE id=?",runId);
        }
        run("ENDED","ABANDONED","radiant-festival");service.reconcile();assertThat(projections()).isZero();
        assertThatThrownBy(()->service.get(owner,runId,false)).isInstanceOf(SessionException.class);
        jdbc.update("DELETE FROM game_runs WHERE id=?",runId);
        run("ENDED","COMPLETED","unknown-generated-prose");service.reconcile();
        assertThatThrownBy(()->service.get(owner,runId,false)).isInstanceOf(SessionException.class);
        assertThat(projections()).isZero();
    }
    @Test void restEndingQualifiesEquallyAndArchivalOrRunDeletionCannotResurrectProjection() {
        run("ENDED","COMPLETED","festival-postponed");service.reconcile();
        assertThat(service.get(owner,runId,false).suggestionKey()).isEqualTo("rest");
        assertThatThrownBy(()->service.accept(owner,runId,body(3),true,false)).isInstanceOf(SessionException.class);
        service.accept(owner,runId,body(3),false,false);assertThat(projections()).isOne();
        service.archive(owner,runId);service.archive(owner,runId);newService(true).ready();
        assertThat(projections()).isZero();
        assertThatThrownBy(()->service.get(owner,runId,false)).isInstanceOf(SessionException.class);
        assertThatThrownBy(()->service.accept(owner,runId,body(3),false,false)).isInstanceOf(SessionException.class);
        jdbc.update("DELETE FROM game_runs WHERE id=?",runId);newService(true).ready();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_run_completions",Integer.class)).isZero();
    }
    @Test void deletedCatalogMissionIsNotRecreatedByReplay() {
        run("ENDED","COMPLETED","radiant-festival");service.reconcile();service.accept(owner,runId,body(3),true,false);
        UUID selected=service.get(owner,runId,false).mission().id();jdbc.update("DELETE FROM missions WHERE id=?",selected);
        service.accept(owner,runId,body(3),true,false);service.reconcile();
        assertThat(service.get(owner,runId,false).mission()).isNull();
        assertThat(service.selectedMissions(owner)).isEmpty();
        assertThat(service.get(owner,runId,false).missionAccepted()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM missions WHERE id=?",Integer.class,selected)).isZero();
    }
    @Test void databaseRejectsForgedNewRpgResultsButPreservesMiniGames() {
        assertThatThrownBy(()->jdbc.update("INSERT INTO game_results(id,user_id,game_key,duration_ms,metrics,completed_at) VALUES(?,?,'capybee_rpg',0,'{}',now())",UUID.randomUUID(),owner)).hasMessageContaining("chk_game_results_rpg_origin");
        jdbc.update("INSERT INTO game_results(id,user_id,game_key,duration_ms,metrics,completed_at) VALUES(?,?,'hive_builder_tap',3,'{}',now())",UUID.randomUUID(),owner);
        assertThat(projections()).isZero();
    }
}
