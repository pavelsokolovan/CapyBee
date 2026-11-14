# Web adventure preparation

Spec 28.2 adds the Adventures entry and preparation/start flow to Play. It is
hidden unless `capybee.game.web-client-enabled=true`; production defaults remain
false. Existing Pack, generation and dynamic-start flags still govern their
respective capabilities. The server's allowlist and audience checks determine
which generator profiles a family can select.

The panel discovers the current run independently of new-game availability.
It renders public setup metadata, separates the shell locale from the story
locale, and requires a final review before every start. A dynamic replacement
needs a separate confirmation and preserves the current run on failure. Pack
starts cannot replace an active run automatically.

Optional ideas remain in memory and are masked during review. Only owner and
operation IDs are retained for recovery. Returning, reconnecting, restoring auth
or reloading never resubmits a lost body. An unknown operation requires checking
the saved game or a fresh, explicit review. Cancelling preparation is best effort;
leaving merely detaches the panel. The read-only saved-run handoff is intentional:
Spec 28.3 owns mechanical gameplay controls.

## PostgreSQL persistence correction

Real HTTP acceptance exposed two existing JDBC defects. All SQL timestamp
arguments now use `java.sql.Timestamp`. PostgreSQL JSONB normalizes object-key
order, while existing dynamic definition fingerprints preserve ordered maps.
The JDBC adapter therefore stores new dynamic snapshots in a bounded JSONB
wrapper:

```json
{"snapshotEncoding":"canonical-json-v1","canonicalSnapshot":"<unchanged engine JSON>"}
```

This is an adapter envelope, not a new engine snapshot format. The engine still
validates its original bytes and fingerprints. Pack snapshots retain their
object layout; reads continue to support legacy object snapshots. No migration
or identity-column change is needed. The decoded payload is limited to 1 MiB and
the existing database size constraint remains in force.

An application rollback must retain support for this adapter envelope to read
new dynamic saves. Disabling new starts/generation while keeping the game API
on remains supported. Corrupt historical snapshots are not silently regenerated.

## Verification

Normal gates are frontend coverage/build/static sync and the exact backend
reactor verification command in the build instructions. With `CI=true`,
`PreparationHttpIntegrationTest` uses PostgreSQL 16, an isolated temporary schema,
a local HTTP server, production controllers/coordinator/JDBC, reviewed fixture
content and a deterministic provider. It verifies Pack start, delayed dynamic
replacement, replay, publication/cancellation ordering, persisted identity and
fresh-adapter provider-free resume. Test-only authentication and provider beans
live under `src/test`; they are never packaged in the application.

For the additional Chromium check, install the UI development dependencies,
ensure PostgreSQL and Java 25 are available, and run from the repository root:

```sh
CI=true \
CAPYBEE_PREPARATION_BROWSER_CHECK="$PWD/app/ui/scripts/check-adventure-preparation.mjs" \
CHROMIUM_EXECUTABLE=/usr/bin/chromium \
mvn -B -f app/pom.xml -pl server -am verify
```

Build and sync the current UI first. The script runs against the test's
Spring-served assets, not a Vite mock. It checks 360px and desktop views,
private-text review/storage, explicit start, keyboard focus, reload/Continue and
browser Back. It writes local screenshots under `/tmp` and emits a content-free
result. No real OAuth sign-in, provider credential or paid provider call is used.
