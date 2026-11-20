# Game visual art guide — catalog v1

Spec: [30.1](specifications/30.1-visual-design-and-svg-catalog-instruction.md).
The catalog is artwork and presentation primitives; the live RPG still uses
its existing map, text renderer, public commands and recruitment flags.

## Identity and palette

The inspected identity reference was
`app/ui/src/assets/avatars/capybee_main_resize.png`: warm tan fur, small round
ears, a broad capybara muzzle and honey accents. The RPG reduces the detailed
raster mascot to a flat vector capybara with an elongated rounded muzzle,
short legs and a honey scarf, as directed by 30.1. The shell assets are unchanged.
All vector geometry was authored for this implementation; no third-party
artwork, tracing library, embedded bitmap or external image service is used.
The existing shell reference is an identity reference, not a new licensed import.
No new attribution obligation is introduced by these original vectors.

Use cream/paper surfaces, dark warm outlines, sage foliage, pale water and muted
lilac distance. `src/assets/game/catalog.ts` exports the canonical palette.
`gameArt.css` scopes equivalent CSS variables to `.game-art`, reusing the app's
ink/muted/accent tokens. HTML text and controls sit on opaque cream, outside
the illustration. Old World lanterns use solid pale halos. Both worlds are
welcoming; no human figures, hearts, padlocks or affection/competition cues.

## Canonical catalog

`app/ui/src/assets/game/catalog.ts` is the serializable manifest. It exports
`catalogVersion = 1`, IDs, kinds, dimensions, safe areas, provenance, source
paths and fallbacks without importing React or browser code. TypeScript ID
unions derive from those same keys. IDs are additive; never reuse an ID for a
different meaning. It is deliberately not a public API or save-file schema.

| Kind | IDs | Source SVG geometry | Variants |
|---|---|---|---|
| Character | `character.capy`, `character.mela-bee`, `character.borys-beaver`, `character.luna-moth`, `character.abstract-guide` | `CharacterArtwork.tsx` | Portrait and figure, each neutral/attentive/pleased; Capy also has a simplified neutral marker |
| Terrain | `terrain.meadow`, `.water`, `.willow`, `.workshop`, `.lantern`, `.crossing`, `.neutral` | `EnvironmentArtwork.tsx` | Default |
| Backdrop | `backdrop.hive-clearing`, `.lantern-grove`, `.quiet-meadow`, `.reed-bank`, `.willow-glen`, `.hive-workshop`, `.small-crossing`, `.neutral` | `EnvironmentArtwork.tsx` | Default |
| Motif | `motif.lantern`, `.reed`, `.rest-leaf` | `MotifArtwork.tsx` | Default |

Names abbreviated with a leading dot in this table retain their family's
prefix. The manifest contains every full ID. Geometry lives in React SVG
elements, which the specification explicitly permits, rather than SVG strings.
Shared face/body/environment primitives avoid duplicated full SVG documents.
Every catalog ID resolves directly to fixed local geometry; no import path is
constructed from a received string. A neutral backdrop contains only clearing,
sky and ground; it does not claim that a story task has been completed.

## Composition

Pointy hexes share the exact `120×140` viewBox and `hexPoints` from the manifest.
Use `GameTileArt` for the terrain plus current/visited/selected boundaries and
Capy's marker. Available/unavailable/pending semantics and all interactions
belong to the host; supply readable HTML labels/reasons next to the artwork.
Only public legal moves may authorize a future gameplay action. Selection and
pending art do not advance Capy or recruit anyone.

The central 48% of the tile is clear. Terrain examples are shown at 64px wide;
the gallery includes a 32px Capy marker and 40/64px portraits with names.
Full figures use a `160×180` viewBox; portraits crop consistently to a 120px
square. The public expression vocabulary is neutral, attentive, and pleased.
CapyBee also has a local playful drawing. Illustrated Hive expeditions select
his expression using accepted art bindings, objectives, and outcomes.
[Spec 44](specifications/44-capybee-expressions-instruction.md)
Only character geometry is mirrored for inward orientation, never HTML labels.

Backdrops use an opaque `640×360` viewBox with background, middle-distance and
ground groups. Preserve aspect ratio. Landmarks stay in the upper central
region; characters occupy the sparse foreground. Show dialogue and choices
below the image on an opaque panel. Artwork is static, with no idle animation,
sound, filter, parallax or timed dismissal.

## Renderer boundary

Renderers live in `src/components/game/art/GameArt.tsx`. Give them a sized
container within `.game-art`; layout and sizing belong to trusted UI code.

```tsx
<div className="game-art">
  <GameCharacter assetId="character.mela-bee" variant="portrait"
    expression="attentive" decorative />
  <GameBackdrop assetId="backdrop.reed-bank" decorative />
</div>
```

Use `decorative` when adjacent HTML already supplies the meaning. For unique
meaning omit `decorative` and provide a localized `label`. The discriminated
props require that label; SVG titles and clip IDs are unique for each instance.
Decorative SVGs are unfocusable and absent from the accessibility tree. Raw
catalog IDs in the developer gallery are also hidden from assistive technology.

Missing, hostile and wrong-family IDs resolve to the receiving family's
neutral art. Invalid variant/expression uses a supported neutral default;
non-Capy marker requests become neutral figures. Unknown motifs render nothing.
No fallback requests a provider, changes narrative text or changes state.
Public names must remain the names next to fallback portraits.

Do not add markup/path/CSS/color/URL inputs to these renderers. No external
images, scripts, event attributes, `foreignObject`, filters, runtime SVG parsing
or `dangerouslySetInnerHTML`. The only local reference is the per-instance
terrain clip. In forced colors, decorative art can disappear while textual
states, control borders and keyboard focus remain visible.

## Preview and checks

Development: run the normal Vite server and open `/?preview=art` or `/__art`.
The gallery is lazy-loaded and offers EN/PL, text-only mode, all 31 character
combinations, all settings/terrain/motifs and eleven state examples. The board,
encounter and party use synthetic fixtures with no controller, storage or API.
The ordinary production build excludes the gallery and its art chunks until
later specifications integrate the renderers into gameplay.

For a built preview, use `npm run build -- --mode art-preview` in `app/ui`,
then the repository static-sync workflow with `-SkipBuild` (the build must be
current). Open `/?preview=art` on Spring's root route; no new server route or
authentication bypass is needed. This is a test artifact, not the production
build. After review run the normal build-and-sync workflow to restore production
assets. No generation feature or provider approval is needed for either build.

Run `npm test` / `npm run test:coverage`. To capture browser evidence from a
local development or Spring preview host, run from `app/ui`:

```sh
node scripts/check-art-gallery.mjs http://127.0.0.1:5173
CAPYBEE_ART_OFFLINE=true node scripts/check-art-gallery.mjs http://127.0.0.1:8080
```

The offline option requires the built preview's service worker. Screenshots
and a machine-readable report go to `docs/evidence/30.1/`; override
`CAPYBEE_ART_EVIDENCE` to write elsewhere. Tests validate the manifest, local
source references, budgets, prohibited SVG constructs, fallback handling,
multiple instances and semantic HTML/text-mode parity. Browser checks cover
both locales, narrow layout/zoom, keyboard, forced colors, reduced motion,
contrast, zero API/external requests and optional cached offline reload.
