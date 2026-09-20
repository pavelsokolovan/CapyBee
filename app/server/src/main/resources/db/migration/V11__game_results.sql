CREATE TABLE game_results (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    game_key VARCHAR(64) NOT NULL,
    duration_ms INTEGER NOT NULL,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_game_results_user_completed
    ON game_results (user_id, completed_at DESC);
