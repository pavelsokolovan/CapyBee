# Spec 28.4 acceptance evidence

Tested 2026-11-14 in the implementation working tree based on
`3c41b0cdb4c9ce4b50cf06c73c718796cfb8a48b`. The implementation commit follows these
checks. No paid provider call or production flag change was made. Candidate
archives honestly identify this as a development/SNAPSHOT working tree; these
checks do not assert clean tagged-release provenance or live-provider quality.

Environment: Java 25.0.5, Node 24.22.1, PostgreSQL 16.11, Chromium
156.0.8078.79 on Linux. Browser viewports: 360×800 and 1280×900, with 200% content
zoom and reduced motion. The browser dependency is pinned by the UI lockfile;
CI installs its matching Chromium rather than assuming a system browser.

## Gates

| Check | Result |
|---|---|
| `npm run test:coverage` in `app/ui` | 109 tests pass; existing coverage thresholds preserved |
| `./scripts/dev-workflow.ps1 -Action sync` | Production UI built and both Spring static destinations synchronized on Linux |
| `mvn -f app/engine/pom.xml clean verify` | Pass, including ordered cancellation tests |
| `mvn -B -f app/pom.xml -pl server -am verify` with browser environment | 88 backend tests pass, including real PostgreSQL HTTP/browser and fresh-host tests |
| Final focused `PreparationHttpIntegrationTest` after UI lifecycle changes | Both HTTP/browser/restart tests pass |
| `actionlint` and `git diff --check` | Pass |

The normal GitHub build job now runs the same deterministic browser suite after
building/syncing the UI and installing Chromium. Its definition is linted;
remote execution requires pushing the implementation commit.

## Acceptance matrix

| Scenario | Evidence |
|---|---|
| Pack choices/advance/places | Polish fixture and English dungeon reach endings through real HTTP; only advertised movement/Continue controls dispatch |
| Text and generated/fallback Pack | Real text fixture dispatches a transient reply, then resumes without replay; generated Pack is explicitly selected/reviewed and uses the deterministic reviewed fallback |
| Delayed dynamic start | HTTP worker barrier produces `202`, one operation reservation and one accepted run |
| Lost start response/reload | Browser holds the confirmed POST response, reloads with a body-free marker and resumes the published run without another start POST |
| Server restart after publication | Spring HTTP host is closed/recreated; same dynamic view, revision and fingerprint survive PostgreSQL; a legal command then applies with generation disabled and no blueprint calls |
| Restart before publication | Fresh operation service has no unpublished identity; frontend unknown-outcome tests require explicit checking/review and never repost a body |
| Cancellation ordering | Before-start, during blueprint/presentation and post-publication cases; HTTP cancellation preserves the prior current run before replacement wins |
| Offline choice/reconnect/reload | One safe command UUID/revision persists, accepted scene stays frozen, acknowledgement reconciles without another intent |
| Offline starts/text | Start controls are disabled; text is neither sent nor durable; original safe intent removal/recovery is tested |
| Competing tabs | Actual two-page fixture with transactional lease fallback; server revision advances once and the other view reconciles without rebasing |
| Auth expiry/account changes | Same-owner restoration is shared; different-owner restoration closes the panel and removes outgoing game work; cross-tab token changes hide old content before rechecking; server owner hint rejects stale-account reads and writes |
| Privacy | Unique setup/gameplay sentinels checked against IndexedDB, local/session storage, Cache Storage, history/URL and console; failure diagnostics contain status/queue metadata only |
| PWA update/cold offline shell | Static service-worker shell remains available; no persisted game view/API response cache or resubmitted effect |
| Ending/reopen and disabled starts | Endings and abandon persist without rewards; saved reading/commands work in the restarted host with dynamic/generation flags disabled |
| Contracts and availability | Decoder, metadata, ownerless migration, owner cleanup and queue/auth regression tests; mini-game/general offline action rules preserved |
| Accessibility/navigation | Keyboard focus/Back/confirmation/cancel, semantic headings, polite status, 360px overflow checks, zoom/reduced motion; visual inspection of generated mobile/desktop screenshots |

## Package and compatibility checks

The frozen candidate is built with `scripts/package-engine.ps1`, verified with
`scripts/verify-engine-package.ps1`, subjected to
`scripts/test-release-package.ps1` failure injection, and tested using
`./dev.ps1 game-certify-offline -SkipBuild` for authored and dynamic suites.
`scripts/certify-server-postgres.ps1 -UseExistingPostgres` verifies migrations
and two starts of the packaged host against PostgreSQL 16.11.

The compatibility harness needed two focused fixes: Linux cannot use
`Start-Process -WindowStyle`, and Spring rejects an empty OAuth issuer. The
harness now uses an explicit loopback test-provider registration; production
OAuth remains unchanged. Existing-postgres evidence no longer claims a Docker
image was used. Package checks use the exact candidate bytes throughout.

Final frozen candidate: `0.1.0-SNAPSHOT-3c41b0cdb4c9-ae86455ecaef`.
Archive SHA-256: `ae86455ecaefc16a402852becd8919aea48efa00e2d174a99e6f035b84eba7c7`.
Packaged server SHA-256: `08ebfdf303678cf573f10b98e0576a43890e1ced6c2e437d0175dceaee6eff40`.
All package/offline/PostgreSQL checks passed against these final bytes.

Archive/candidate hashes and content-free reports are produced under
`app/engine/target/distribution` and `app/engine/target/evidence`; credentials,
private bodies and transcripts are not copied into this note.

## Limits and operator actions

This is deterministic client/persistence acceptance. It does not certify real
Google sign-in, live-provider quality/cost, audio screen-reader output, physical
Android/iOS PWA behavior or Windows execution from this Linux environment.
The automated auth doubles are test-only; production uses ordinary ownership
and authentication. Operator assistive-technology/device checks and explicit
production enablement remain separate. Spec 29 is unimplemented.
