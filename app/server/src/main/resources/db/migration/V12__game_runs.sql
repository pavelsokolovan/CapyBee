CREATE TABLE game_runs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pack_id VARCHAR(64) NOT NULL,
    pack_version VARCHAR(32) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    end_reason VARCHAR(16),
    failure_code VARCHAR(48),
    revision BIGINT NOT NULL DEFAULT 0,
    generation_calls INTEGER NOT NULL DEFAULT 0,
    snapshot JSONB,
    pending_start JSONB,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT chk_game_runs_status CHECK (status IN ('PREPARING', 'ACTIVE', 'ENDED', 'FAILED')),
    CONSTRAINT chk_game_runs_snapshot_size CHECK (snapshot IS NULL OR pg_column_size(snapshot) <= 262144),
    CONSTRAINT chk_game_runs_generation_calls CHECK (generation_calls >= 0)
);

CREATE UNIQUE INDEX uq_game_runs_one_open_per_user
    ON game_runs (user_id) WHERE status IN ('PREPARING', 'ACTIVE');
CREATE INDEX idx_game_runs_user_updated
    ON game_runs (user_id, updated_at DESC);

CREATE TABLE game_run_commands (
    run_id UUID NOT NULL REFERENCES game_runs(id) ON DELETE CASCADE,
    command_id UUID NOT NULL,
    payload_mac BYTEA NOT NULL,
    state VARCHAR(12) NOT NULL,
    accepted BOOLEAN,
    revision_before BIGINT NOT NULL,
    revision_after BIGINT,
    scene_source VARCHAR(20),
    latency_ms INTEGER,
    failure_code VARCHAR(48),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, command_id),
    CONSTRAINT chk_game_run_commands_state CHECK (state IN ('IN_PROGRESS', 'DONE', 'FAILED'))
);

CREATE TABLE game_run_events (
    run_id UUID NOT NULL REFERENCES game_runs(id) ON DELETE CASCADE,
    seq BIGINT NOT NULL,
    command_id UUID,
    revision BIGINT NOT NULL,
    event_id VARCHAR(96) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, seq)
);

CREATE TABLE game_run_prefetch (
    run_id UUID NOT NULL REFERENCES game_runs(id) ON DELETE CASCADE,
    scene_id VARCHAR(96) NOT NULL,
    based_on_revision BIGINT NOT NULL,
    artifact JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, scene_id)
);

CREATE TABLE game_generation_usage (
    scope VARCHAR(80) NOT NULL,
    day DATE NOT NULL,
    calls INTEGER NOT NULL DEFAULT 0,
    prefetch_calls INTEGER NOT NULL DEFAULT 0,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (scope, day),
    CONSTRAINT chk_game_generation_usage_nonnegative
        CHECK (calls >= 0 AND prefetch_calls >= 0 AND input_tokens >= 0 AND output_tokens >= 0)
);
