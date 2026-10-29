package com.capybee.server.game;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL-only proof for the forward V12-to-V13 Pack identity migration. */
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class PostgresDynamicMigrationTest {
    private static final String URL = "jdbc:postgresql://localhost:5432/capybee";
    private static final String USER = "capybee";
    private static final String PASSWORD = "capybee";

    @Test void migratesPackRowsWithoutSnapshotRewriteAndEnforcesExclusiveIdentity() throws Exception {
        String schema = "dynamic_migration_" + UUID.randomUUID().toString().replace("-", "");
        try {
            migrate(schema, "12");
            UUID userId = UUID.randomUUID(), runId = UUID.randomUUID();
            String snapshot = "{\"legacy\":\"unchanged\"}";
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.createStatement().execute("SET search_path TO " + schema);
                try (var user = connection.prepareStatement("""
                        INSERT INTO users (id, google_subject, email, display_name, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    user.setObject(1, userId); user.setString(2, "subject-" + userId);
                    user.setString(3, userId + "@example.invalid"); user.setString(4, "Migration Test");
                    user.setObject(5, Instant.EPOCH); user.setObject(6, Instant.EPOCH); user.executeUpdate();
                }
                try (var run = connection.prepareStatement("""
                        INSERT INTO game_runs (id, user_id, pack_id, pack_version, locale, status,
                            snapshot, created_at, updated_at)
                        VALUES (?, ?, 'fixture-minimal', '1.0.0', 'en', 'ACTIVE', ?::jsonb, ?, ?)
                        """)) {
                    run.setObject(1, runId); run.setObject(2, userId); run.setString(3, snapshot);
                    run.setObject(4, Instant.EPOCH); run.setObject(5, Instant.EPOCH); run.executeUpdate();
                }
            }
            migrate(schema, "13");
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.createStatement().execute("SET search_path TO " + schema);
                try (var query = connection.prepareStatement(
                        "SELECT source_kind, pack_id, pack_version, snapshot::text FROM game_runs WHERE id = ?")) {
                    query.setObject(1, runId);
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString(1)).isEqualTo("PACK");
                        assertThat(rows.getString(2)).isEqualTo("fixture-minimal");
                        assertThat(rows.getString(3)).isEqualTo("1.0.0");
                        assertThat(rows.getString(4)).contains("unchanged");
                    }
                }
                assertThatThrownBy(() -> connection.createStatement().execute("""
                        INSERT INTO game_runs (id, user_id, source_kind, pack_id, pack_version,
                            profile_id, profile_version, game_fingerprint, locale, status, created_at, updated_at)
                        VALUES (gen_random_uuid(), '%s', 'DYNAMIC', 'fake-pack', '1.0.0',
                            'generic-adventure', '1.0.0', 'fingerprint', 'en', 'ACTIVE', now(), now())
                        """.formatted(userId))).hasMessageContaining("chk_game_runs_source_identity");
            }
        } finally {
            try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private static void migrate(String schema, String target) {
        Flyway.configure().dataSource(URL, USER, PASSWORD).schemas(schema).defaultSchema(schema)
                .createSchemas(true).target(MigrationVersion.fromVersion(target)).load().migrate();
    }
}
