-- Minimal host linkage. Narrative content stays in game_runs, never in results/Missions.
CREATE TABLE game_run_completions (
    run_id UUID PRIMARY KEY REFERENCES game_runs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    completed_at TIMESTAMPTZ NOT NULL,
    suggestion_key VARCHAR(128) NOT NULL,
    suggestion_version INTEGER NOT NULL CHECK (suggestion_version > 0),
    mission_id UUID REFERENCES missions(id) ON DELETE SET NULL,
    mission_accepted BOOLEAN NOT NULL DEFAULT false,
    cell_requested BOOLEAN NOT NULL DEFAULT false,
    archived BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX idx_game_run_completions_owner ON game_run_completions(user_id);
ALTER TABLE game_results ADD COLUMN rpg_run_id UUID UNIQUE REFERENCES game_runs(id) ON DELETE CASCADE;
-- Preserve legacy rows, but exclude client-declared RPG rows from reads.
-- NOT VALID permits existing untrusted rows; every new write must have linkage.
ALTER TABLE game_results ADD CONSTRAINT chk_game_results_rpg_origin
    CHECK ((game_key = 'capybee_rpg') = (rpg_run_id IS NOT NULL)) NOT VALID;
