# Web adventure client

The Play entry uses one renderer and the existing authenticated sync loop for
Pack and dynamic adventures. The engine owns mechanics, legal commands,
accepted story text, objectives, places and results. The source-neutral place
map is implemented by Spec 29.3. The CapyBee Polish Pack (29.1) and product
Mission/reward integration (29.4) remain separate dependencies.

## Public place map

`GamePlaces`, within `ScenePanels`, consumes only `PlacesView`. It renders HEX
coordinates as an axial arrangement (`column = 2*x + y`, `row = y`), normalized
to the visible origin. The board accepts at most 24 distinct places, eight
half-columns and six rows, with safe integer coordinates within ±10000.
Missing/malformed coordinates, duplicate positions/IDs, unsupported layouts or
larger spans retain the semantic list. Optional malformed coordinate hints are
normalized to null at the client decoder; command/state contracts stay strict.
At container widths of 28rem or less (including zoom), the same list reflows
vertically. The explicit list toggle is available on wider boards as well.

Current and visited places have text and symbol indicators. Every destination
has a readable movement hint; only `availableMoves` enables its MOVE button.
Inspection reads the public description in memory, dispatches nothing and
returns keyboard focus on Back/Escape. Pending commands leave the accepted
map visible and disable all movement. Commands use the existing controller,
revision, owner guard, offline queue and recovery path. The activity honeycomb
has no connection to this fictional board. Discovered characters and speech
continue through the shared scene renderer for every source.

The existing Play catalog/preparation/start path renders any installed Pack,
including the future Polish Pack, without another controller or source-specific
renderer. This checkout does not contain 29.1's Pack; the test-only HEX variant
of the dungeon validates this path without pretending to ship Polish content.
Current dynamic profiles disable places; synthetic dynamic public-view tests
exercise map parity without changing those profile capabilities or enabling
generation. See [29.3 acceptance evidence](game-hex-map-acceptance.md).

## Configuration and audience

All production flags retain their default `false` values:

| Environment variable | Purpose |
|---|---|
| `CAPYBEE_GAME_WEB_CLIENT_ENABLED` | Show the web entry |
| `CAPYBEE_GAME_ENABLED` | Enable the game API, including saved runs |
| `CAPYBEE_GAME_GENERATION_ENABLED` | Permit generated presentation/new paid starts |
| `CAPYBEE_GAME_DYNAMIC_ENABLED` | Enable new dynamic preparation/start operations |
| `CAPYBEE_GAME_DYNAMIC_PROFILE_ROOT` | Reviewed profile directory |
| `CAPYBEE_GAME_DYNAMIC_PROFILE_IDS` | Reviewed allowlist (default `generic-adventure`) |

To stop new paid starts while preserving saved runs, keep the API and client
available and disable generation and dynamic starts. Do not disable the entire
game API to pause paid creation. Existing authored starts can remain available.
Keep the command HMAC key stable across server restarts. Do not log keys or
private bodies. Deployment does not automatically enable generation.

The server checks profile allowlisting, active family profile and the entire
birth-year age interval. Generic adventure is reviewed for ages 10–14;
`classic-detective` targets 15–17 and is not made available to a child around
12 by this client. Capabilities advertise disclosure revision **1**. A changed
profile identity/fingerprint or disclosure revision invalidates review before
a new paid request. No reviewed dynamic fallback Pack binding is currently
advertised: a fallback offer is not an implicit start.

## Online and offline boundaries

Only a single explicitly selected `CHOOSE`, `ADVANCE`, `MOVE` or `ABANDON` may
be queued for an owner/run. The record contains its UUID, original revision,
allowed IDs and command kind. The accepted scene remains in memory while
waiting for connection; no future scene is predicted. Starts, suggestions and
`SUBMIT_TEXT` are online-only. They never become durable offline actions or
inflate the general pending count. Text drafts disappear after dispatch and
are not reconstructed after an uncertain send.

A provably unsent action can be removed. An ambiguous action first uses GET
status for its original ID; a missing command is reconciled against the run's
original revision before the safe payload can be retried with the same UUID.
A newer revision/non-active run discards stale intent; the client does not
rebase it. Exhaustion offers status checking and, for a proved unsent action,
explicit retry. Cold offline launch shows the shell/reconnect state, not a
persisted story or an offline engine.

Web Locks coordinate dispatch when available. The fallback uses transactional
IndexedDB leases with a ten-second expiry and renewal, never a localStorage
read/write lock. A crashed page releases its claim through expiry. Failure to
coordinate pauses background game dispatch. Server ownership, revision and
idempotency remain authoritative across tabs/devices. Broadcasts contain only
owner/run IDs and revisions, or an outgoing-owner logout notice.

## Authentication and privacy

The stable server user ID scopes actions/operation markers. `X-Game-Owner` is a
stale-account guard, not authorization: the server derives the actual user from
the authenticated session and rejects a mismatch before executing a command.
Temporary expiry pauses work and uses the existing restore token mechanism,
with one same-owner restoration shared by foreground and background callers.
Changing accounts aborts requests and removes outgoing game actions/markers;
logout also closes the game and clears in-memory story/drafts. Check-ins,
memories, friendships and mini-game offline results retain their own policies.

Ownerless legacy game actions are removed without dispatch or assigning a new
owner. The UI asks the user to check saved state/reconfirm. Private payloads are
never displayed or logged during migration.

All `/api/game/` responses (including errors) carry `Cache-Control: no-store`.
The service worker preserves `/api/` NetworkOnly and excludes API/auth paths
from navigation fallback. Only static shell/assets are precached. Do not add
story/query caches, transcripts, drafts or payload hashes to browser storage.

## Interrupted starts and restart

A confirmed start has one body-free marker. On reload the client checks that
operation, then known/current run identities as needed. A vanished unpublished
operation after server restart is unknown, not cancellation or permission to
POST its private body again. Return to review explicitly after checking saved
state. Cancellation is best effort and cannot promise reversal of a charge.
Publication/replacement is atomic: cancellation/failure before publication
preserves the previous run; a publication winner remains resumable.

Published dynamic snapshots include accepted definition/content and fingerprint
in PostgreSQL. Reading them does not regenerate a blueprint. Keep the canonical
snapshot envelope described in [web preparation](engine/web-preparation.md).
Use forward migrations and preserve compatible saved-run reading in rollbacks.

## Verification

Use Java 25, PostgreSQL 16, Node and the UI development dependencies. Run the
repository engine/backend gates and frontend coverage, then the documented
PowerShell static-sync workflow. For browser acceptance, use:

```sh
CI=true \
CAPYBEE_PREPARATION_BROWSER_CHECK="$PWD/app/ui/scripts/check-adventure-preparation.mjs" \
CHROMIUM_EXECUTABLE=/usr/bin/chromium \
mvn -B -f app/pom.xml -pl server -am verify
```

The host uses test-only authentication and deterministic providers, isolated
PostgreSQL schemas and production controllers/persistence. It must never be
shipped as a production authentication bypass. `game-recovery.mjs` checks
network/state/storage effects, fallback leases, offline intent/reload,
competing tabs, uncertain text response, private sentinels, account guards and
a cold service-worker shell. Browser failures report only stable status and
queue metadata, never prose or private bodies. Package/verifier/offline and
PostgreSQL compatibility gates are separate from paid live certification.

No automated result certifies current live-provider quality/cost. Enabling
production generation and hands-on assistive-technology/device acceptance
remain explicit operator responsibilities.

Content-free outcomes and environment details are recorded in
[28.4 acceptance evidence](game-web-client-acceptance.md). GitHub Actions runs
the deterministic suite on pushes and pull requests; its browser installation
and the portable UI sync are part of the ordinary build job.
