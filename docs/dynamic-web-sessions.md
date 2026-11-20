# Dynamic web sessions

New paid dynamic starts are enabled by default when the provider is configured. Existing dynamic runs remain readable and commandable when this feature is disabled or the provider is unavailable.

Set `CAPYBEE_GAME_GENERATION_ENABLED=false` to pause new starts. The packaged `generic-adventure` profile is enabled by default. A deployment may configure a custom `CAPYBEE_GAME_DYNAMIC_PROFILE_ROOT` and a comma-separated `CAPYBEE_GAME_DYNAMIC_PROFILE_IDS` allowlist. The default operation timeout is 180 seconds and the default per-owner admission limit is three starts per hour. Profile directories are validated once at startup and clients can select IDs only; paths and internal generation resources are never returned.

Authenticated clients discover profiles at `GET /api/game/generator-profiles`, submit final confirmed preparations to `POST /api/game/dynamic-starts`, and poll or cancel with `GET`/`DELETE /api/game/dynamic-starts/{requestId}`. Responses are `Cache-Control: no-store`. Suggestions are transient, guarded in memory, and never written to JDBC or operation responses.

Flyway V13 converts `game_runs` to an exclusive `PACK`/`DYNAMIC` identity. Rollback of the application must use a version that understands the migrated nullable Pack columns; the migration itself is forward-only. To stop new charges without interrupting play, disable `CAPYBEE_GAME_GENERATION_ENABLED` while leaving `capybee.game.enabled` on.
