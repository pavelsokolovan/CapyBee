# Public game presentation

Spec [30.2](specifications/30.2-public-presentation-contracts-instruction.md)
adds an optional `presentation` envelope to public view schema **2**. The
existing capabilities endpoint advertises `presentation-v1`; no new endpoint,
command or view-schema number is introduced. Both run summaries and command
outcomes carry the same engine view. Existing narrative, options, legal moves
and result fields keep their meaning.

## Public data

`presentation.schemaVersion` is 1. `catalogVersion` identifies the logical art
catalog. `playerAssetId` and `backdropAssetId` are nullable references; `places`
contains public place/terrain references. `characters` enriches discovered
`view.characters` by public `id`, with `assetId`, nullable `trait`/`need`, a fixed
`expression` and `recruited`. `party` contains only recruited public character
IDs. Names, descriptions and `inCurrentScene` stay in `view.characters`.

The engine projects this from entered scenes and accepted flags. Moving to or
inspecting a location does not reveal a character. Helping a character changes
the existing authored flag; reading, replaying or rendering metadata cannot
recruit anyone. Returning to an encounter does not duplicate party members.
Completed runs retain known companions; abandoned views keep the existing
cleared-narrative behavior.

Do not inspect snapshots, flags, blueprints, Pack IDs, prose or option IDs to
reconstruct this information in React. Do not treat terrain, artwork or visual
adjacency as movement authority. No raw flag names, reveal rules, unseen cast,
future scene bindings or full asset catalog are returned in the envelope.

## Authored source and identity

The optional `presentation.json` requires `ASSETS` and indexed
`assets/catalog.json`. The catalog declares bounded logical IDs and universal
`CHARACTER`/`TERRAIN`/`BACKDROP` kinds, with no URLs or artwork payloads. Legacy
`ASSETS` Packs without `presentation.json` retain their previous behavior.
The parser validates fields, bounds, kinds, duplicates and references through
the normal Pack validation boundary before a definition can be used.

Character definitions bind localized string keys, appearance scenes, art and
an optional existing boolean flag. Optional `slotId` binds an existing declared
generated cast slot. Generated cast IDs are assigned by the engine and saved
with accepted content; projection preserves these IDs and accepted names rather
than adding duplicate authored characters. Authored play uses the declared
public actor reference. Nothing is matched through text or inferred species.

CapyBee Pack **1.2.0** adds this data to the unchanged 1.1.0 mechanics and
generation slots. Server and CLI packaging retain exact 1.0.0 and 1.1.0 content.
Server resume resolves the saved version. When using the CLI with an older
saved session, select that version's retained Pack directory, as with the
existing version-pinned CLI workflow. Explicit directories resolve identity
from the bounded, validated manifest, so a version basename is supported. The
host's expected-ID loader continues to enforce its supplied ID. No snapshot/store schema changes or
presentation-specific persistence are needed: metadata is immutable Pack
content, and the view is rebuilt from the pinned definition and accepted state.

## Client degradation

`decodeView` validates core gameplay as before. It normalizes presentation
separately through `presentationDecoder.ts`, constructing a whitelist object
with bounded lists/strings and references to already-public entities. Unknown
fields are stripped. Unseen character/place metadata and inconsistent party IDs
are filtered; only `recruited === true` can support a party reference.

Malformed/unsupported presentation envelopes become null without invalidating
valid text/actions. Missing metadata remains absent on older view objects; v1
views ignore it. Unsupported catalog versions preserve safe text/membership but
use neutral local artwork. Unknown, missing, hostile or wrong-family asset IDs
use 30.1's registry fallback; unknown expressions use neutral. Text containing
markup remains plain text data and must be rendered as React text children.

Types are exported from `gameApi.ts`. A consumer can use `view.presentation`
and join character IDs to `view.characters` with no source-specific branch.
This part exposes data; 30.3–30.5 own the playable board, party and illustrated
encounter UI. Dynamic blueprints retain their text-first view without a
presentation envelope until a later contract supplies validated metadata.

## Verification

Generic engine fixtures exercise validation, discovery, recruitment,
rejection, short replies, immutable collections and exact provider-disabled
resume. The reviewed reference routes cover both authored and generated
fallback modes, preserving accepted generated IDs and both retained versions.
A shared wire golden is consumed by Java session tests and TypeScript decoding
tests. The frontend additionally checks canonical catalog/Pack allowlist parity.
The existing integrated browser suite asserts encounter/recruitment metadata,
no discovery from MOVE, replay parity and exact reload through actual HTTP.

See [acceptance evidence](game-presentation-contracts-acceptance.md) for executed
checks, resource fingerprints, artifact verification and limits.

## Movement and encounter entry

Spec 30.3 keeps view schema 2 and artwork schema 1. Optional public place fields
`moveBehavior` (`LOCATION_ONLY` or `ENTER_SCENE`) and `moveHint`, plus map-level
`movementHint`, describe accepted movement behavior without exposing scene
bindings, allowed scene IDs, rule conditions or flag names. Capabilities include
`place-entry-v1`. Unknown behavior uses generic move wording; invalid optional
hints are omitted independently of valid story/actions.

Only `availableMoves` authorizes destination controls. A queued `MOVE` leaves
the accepted location, encounter, character metadata and party unchanged until
one accepted server revision arrives. Pack 1.3.0 opts into bound encounters;
retained Packs keep location-only movement. React does not branch on versions.
