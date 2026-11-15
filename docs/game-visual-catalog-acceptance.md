# Spec 30.1 — Visual catalog acceptance

Baseline: `473e96f81022bce26a72217d6bd74b6475790fc5`. Implemented 2026-11-15.
See the [art guide](game-visual-art-guide.md),
[specification](specifications/30.1-visual-design-and-svg-catalog-instruction.md)
and [browser report](evidence/30.1/browser-report.json).

## Delivered inventory

Catalog version **1** contains **23 stable IDs**: five characters, seven terrain
tiles, eight backdrops and three motifs. Characters have portrait/figure and
neutral/attentive/pleased combinations; Capy has an additional simplified neutral
map marker, giving **31 required character combinations**. All seven authored
locations and Mela/Borys/Luna species are covered. The neutral guide, terrain
and backdrop render without a provider; unknown motifs are omitted.

The source manifest exports serializable metadata with kinds, variants,
dimensions, safe areas, fallbacks and provenance. React renders fixed local SVG
geometry. Source validation excludes active/external/embedded SVG constructs.
The source has no arbitrary import paths or runtime markup interpretation.

| Canonical shared source | Uncompressed UTF-8 bytes |
|---|---:|
| `CharacterArtwork.tsx` — all five characters | 5,020 |
| `EnvironmentArtwork.tsx` — all terrain/backdrops | 4,411 |
| `MotifArtwork.tsx` — all three motifs | 1,156 |
| `catalog.ts` — manifest/types/palette/lookup | 3,640 |
| **Total artwork and manifest** | **14,227** |

Shared sources are counted once in the total. Even charging the complete shared
source to each asset stays below 20 KiB/character, 8 KiB/terrain or motif and
30 KiB/backdrop. Tests also bound individual rendered SVG sizes. The total stays
well below 500 KiB; screenshot and gallery code sizes are excluded as specified.

## Verification

- Frontend: **19 test files / 146 tests passed**, including seven new catalog,
  renderer and gallery tests. The existing coverage gate passed: 89.45%
  statements, 86.58% branches, 96% functions and 97.32% lines. These percentages
  describe the repository's configured offline/session coverage scope, not an
  invented whole-application coverage metric.
- Registry/fallback checks cover all IDs and combinations, absent/unknown/
  wrong-family/prototype/hostile references, invalid expressions/variants,
  neutral defaults, repeated instance title/clip IDs, named/decorative semantics
  and source safety/budgets. Gallery tests verify EN/PL and text-content parity.
- Browser: Chromium on Linux, 1280px and 360px, 200% zoom, reduced motion,
  keyboard selection/focus, forced colors, 44px control targets, no horizontal
  overflow, text-only encounter parity and no browser errors. All eleven tile
  state examples and the required state combinations are present.
  Final screenshot review also verified visible control borders after decorative
  art is removed in forced colors; the browser assertion now checks every
  button/select boundary. The authenticated gallery/offline test was rerun and
  passed after this final CSS adjustment.
- The **Spring-served built art-preview** passed through the repository's
  existing authenticated `PreparationHttpIntegrationTest` host with PostgreSQL
  16.4 and deterministic providers. Cached offline reload displayed all eight
  settings. The gallery made **zero API calls and zero external requests**.
  No production authentication, provider approval or feature flag was changed.
- The art-preview build was followed by the ordinary production build and the
  official `scripts/dev-workflow.ps1 -Action sync`, which synchronized both
  server static directories. The normal output has no gallery JavaScript/CSS
  chunk; `?preview=art` cannot enable it in production.

The first optional full Maven run reached and passed the authenticated catalog
browser/offline test, but its unrelated `CapyBeeApplicationTests.contextLoads`
attempted Google's discovery URL and failed in the restricted network. Final
verification uses test-process-only placeholder OAuth endpoints instead of
external discovery; no repository security configuration is modified. The final
`mvn -B -f app/pom.xml -pl server -am verify` run passed all module and coverage
gates with those local test settings. Its Spring browser smoke confirmed that
the ordinary production build ignores `?preview=art`, opens Play/Adventures and
resumes an existing game at 360px without browser errors. See the
[production smoke capture](evidence/30.1/production-smoke.png). Byte-for-byte
directory comparison confirmed that both Spring static directories match the
normal production `dist/` after sync. No preview build is left in `dist/`.

## Contrast and visual evidence

Ratios measured from the actual fixed palette and opaque preview surfaces:

| Pair | Ratio |
|---|---:|
| Ink / cream | 14.13:1 |
| Muted text / cream | 6.16:1 |
| Ink / paper | 14.98:1 |
| Muted text / paper | 6.53:1 |
| Focus / cream | 6.43:1 |
| Ink / primary choice | 11.03:1 |
| Tile boundary / meadow | 5.14:1 |
| Tile boundary / water | 4.26:1 |

Visual inspection covered the original Capy raster reference and captured
vector results. Capy's muzzle was lengthened to distinguish the capybara from
the beaver, and Luna's wing geometry was made symmetric before final capture.
The current double-outline tile, selected inset, dashed unavailable boundary,
visited check, pending text and unclipped keyboard outline remain distinct.

Evidence: [desktop](evidence/30.1/desktop.png),
[board](evidence/30.1/board.png),
[Capy](evidence/30.1/character-capy.png),
[Mela](evidence/30.1/character-mela-bee.png),
[Borys](evidence/30.1/character-borys-beaver.png),
[Luna](evidence/30.1/character-luna-moth.png),
[neutral guide](evidence/30.1/character-abstract-guide.png),
[lantern grove](evidence/30.1/lantern-grove.png),
[encounter](evidence/30.1/encounter.png),
[party and empty party](evidence/30.1/party.png),
[mobile board](evidence/30.1/mobile-board.png),
[mobile encounter](evidence/30.1/mobile-encounter.png),
[mobile party](evidence/30.1/mobile-party.png),
[200% zoom](evidence/30.1/mobile-zoom.png),
[text only](evidence/30.1/text-only.png),
[forced colors](evidence/30.1/forced-colors.png).

## Scope and limits

The development/test gallery is synthetic and lazy-loaded. No Pack resource,
fingerprint, save, public DTO, generation profile, recruitment flag, legal-move
source or personal activity honeycomb was changed. The existing game controller
and text renderer remain in charge. The catalog does not fix MOVE-to-encounter
entry, wire party membership or enable generated asset selection; those remain
30.2–30.6 work.

Review here covers rendered Chromium screenshots and automated DOM/keyboard/
media-mode behavior. Native screen-reader/device testing and full PWA update/
old-save release certification remain 30.7. This is reusable artwork acceptance,
not a claim that the complete illustrated RPG has shipped.
