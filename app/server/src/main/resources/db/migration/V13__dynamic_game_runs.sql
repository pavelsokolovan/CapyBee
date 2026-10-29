ALTER TABLE game_runs ADD COLUMN source_kind VARCHAR(12);
ALTER TABLE game_runs ADD COLUMN profile_id VARCHAR(64);
ALTER TABLE game_runs ADD COLUMN profile_version VARCHAR(32);
ALTER TABLE game_runs ADD COLUMN game_fingerprint VARCHAR(80);

UPDATE game_runs SET source_kind = 'PACK';

ALTER TABLE game_runs ALTER COLUMN source_kind SET NOT NULL;
ALTER TABLE game_runs ALTER COLUMN pack_id DROP NOT NULL;
ALTER TABLE game_runs ALTER COLUMN pack_version DROP NOT NULL;
ALTER TABLE game_runs DROP CONSTRAINT chk_game_runs_status;
ALTER TABLE game_runs ADD CONSTRAINT chk_game_runs_status
    CHECK (status IN ('PREPARING', 'ACTIVE', 'SUSPENDED', 'ENDED', 'FAILED', 'CANCELLING', 'CANCELLED'));
ALTER TABLE game_runs ADD CONSTRAINT chk_game_runs_source_identity CHECK (
    (source_kind = 'PACK' AND pack_id IS NOT NULL AND pack_version IS NOT NULL
        AND profile_id IS NULL AND profile_version IS NULL AND game_fingerprint IS NULL)
 OR (source_kind = 'DYNAMIC' AND pack_id IS NULL AND pack_version IS NULL
        AND profile_id IS NOT NULL AND profile_version IS NOT NULL AND game_fingerprint IS NOT NULL)
);
