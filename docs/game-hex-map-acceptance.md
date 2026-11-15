# Spec 29.3 acceptance evidence

Implementation based on `eb15f59`, tested on 2026-11-15. Production flags and
providers are unchanged. No Mission/reward feature is included.

## Scope and dependency

The graphical map, list fallback and inspection run inside the existing shared
scene boundary. All mutations and recovery remain with the existing controller.
The personal activity honeycomb is unchanged. Public discovered characters and
encounters retain the shared renderer, including text-only adventures.

Spec 29.1's Polish Pack is absent from this baseline. The existing catalog and
start path already accept Pack metadata without hardcoded identities; no second
entry/controller was added. The integrated fixture is explicitly a test-only
HEX presentation of the dungeon, with long Polish/English labels and hostile
plain-text descriptions. It is not the product Polish Pack. Final acceptance
with that Pack remains dependent on implementing/installing 29.1.

Current dynamic profiles forbid places. Public-view component fixtures verify
authored/dynamic map parity; the HTTP suite exercises real dynamic text-only
runs without changing that capability or making paid calls.

## Checks

- Frontend coverage: 120 tests pass; existing coverage thresholds preserved.
- Production UI build and both Spring static locations synced using
  `scripts/dev-workflow.ps1 -Action sync`.
- `mvn -B -f app/pom.xml -pl server -am verify` with the documented browser
  environment: pass, including 88 backend tests, PostgreSQL HTTP/browser/restart
  checks and all existing coverage gates. Engine dependencies also pass in the
  required reactor build; no engine/public server contract was changed.

The new unit cases cover current/visited/legal/disabled list/map parity,
unknown layout kinds, absent/fractional/duplicate/large/sparse coordinates,
optional fields, plain-text hostile strings, inspection focus restoration,
locked movement, source-neutral encounters and unchanged text-only rendering.

The browser extension covers the real catalog/start/controller path, map opening
and inspection without mutations, keyboard Enter/Tab and focus restoration,
360px/desktop, 200% zoom, reduced motion, target sizes, pending locking without
speculative movement, offline MOVE/reload and a stale MOVE after another tab's
accepted command. The existing browser suite retains owner/account, auth,
storage sentinel, cold offline shell and competing-dispatch coverage.

Environment: Linux, Java 25.0.4.1, Node 24.22.1, PostgreSQL 16.16, Chromium
156.0.8078.79; deterministic test authentication/providers only. No live provider,
production enablement or physical assistive-technology/device certification is
claimed.

For this network-restricted environment, Maven used the environment proxy and
system CA trust store. Application-context verification used a process-local
OAuth registration (`verification-fixture`) with loopback authorization, token
and user-info URLs and dummy client credentials, following the pattern in
`scripts/certify-server-postgres.ps1`. Production OAuth/configuration files were
unchanged; no sign-in request was sent. The browser host retains its existing
test-only authenticated filter and deterministic providers.

The final suite includes visual inspection of the desktop HEX map and 360px
200%-zoom list screenshots. `git diff --check` passes. Generated static/build
metadata is excluded from the implementation commit, as in the earlier client
implementation commits.


## Authored Polish follow-up

[29.1 acceptance](game-polish-authored-pack-acceptance.md) adds the real
`capybee-new-hive` 1.0.0 Pack and exercises it through these shared components,
including map movement, exact reload and all resolutions in EN/PL shells. The
original evidence above retains its historical fixture/dependency limits.
Generation and final product release acceptance remain separate.
