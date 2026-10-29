# Dynamic web sessions

New paid dynamic starts are disabled by default. Existing dynamic runs remain readable and commandable when this feature is disabled or the provider is unavailable.

Enable starts with `capybee.game.dynamic.enabled=true`, `capybee.game.generation-enabled=true`, a deployment-owned `capybee.game.dynamic.profile-root`, and a comma-separated `capybee.game.dynamic.profile-ids` allowlist. The default operation timeout is 180 seconds and the default per-owner admission limit is three starts per hour. Profile directories are validated once at startup and clients can select IDs only; paths and internal generation resources are never returned.

Authenticated clients discover profiles at `GET /api/game/generator-profiles`, submit final confirmed preparations to `POST /api/game/dynamic-starts`, and poll or cancel with `GET`/`DELETE /api/game/dynamic-starts/{requestId}`. Responses are `Cache-Control: no-store`. Suggestions are transient, guarded in memory, and never written to JDBC or operation responses.

Flyway V13 converts `game_runs` to an exclusive `PACK`/`DYNAMIC` identity. Rollback of the application must use a version that understands the migrated nullable Pack columns; the migration itself is forward-only. To stop new charges without interrupting play, disable `capybee.game.dynamic.enabled` while leaving `capybee.game.enabled` on.
