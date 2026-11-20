# Spec 36 — CapyBee: Capability-based scene-first play

**Depends on:** 30.2, 30.5, 31, 32 and 33

**Baseline:** `4c4f71b`

Make the scene-first layout from specs 32 and 33 follow what the accepted view
*contains*, not which pack id the run belongs to. Any run whose accepted view
carries artwork presentation gets the same calm, scene-first screen. This keeps
the UI consistent with the universal-engine goal: a new pack with artwork must
not need a UI code change to look like **Nowy ul, dwa światła**.

No visual redesign belongs here. Layout changes (one-screen scene, map sheet,
scroll behavior) are separate specs that build on this one.

## Findings in the baseline

- `GameRun.tsx` sets `expedition = state.run.packId === hiveExpeditionPackId`.
  `AdventurePreparation.tsx` repeats the same pack-id check in `sceneFirstReady`.
  A run that fails the check falls into the legacy path of `GameRun`: visible
  "Story language" and "saved" paragraphs, full-size board with per-hex
  Inspect/Go buttons, the "Current moment" heading, a non-collapsible
  "Who is here" card and a visible "Adventure" fieldset legend.
- `adventureTitle()` returns a title only for the Anonymous Note episode, a
  story title in the view, or the hive pack id. Otherwise the run heading falls
  back to the generic "Przygoda" / "Adventure", which repeats the overlay header.
- Dynamic generator profiles declare `placesEnabled: false`, so today only
  authored packs can carry `places` plus `presentation`. This spec therefore
  changes nothing for generated (DYNAMIC) runs.
- **Check before coding.** The legacy layout seen on a device may simply be an
  older renderer. Confirm the deployed build includes specs 32 and 33, and that
  the installed PWA has taken the update notice from `docs/pwa-update-recovery.md`.
  If the run on the device was the hive pack and the build was current, report it
  as a bug in the existing predicate and fix it as part of this spec.

## Behavior

1. **One predicate.** Add `app/ui/src/game/sceneFirst.ts` exporting
   `sceneFirstKind(run: GameRunSummaryDto): 'episode' | 'expedition' | null`.
   - `'episode'` when `run.packId === anonymousNotePackId` (its distinct layout
     is unchanged).
   - `'expedition'` when `run.packId === hiveExpeditionPackId` (keeps retained
     saves that predate artwork) **or** the accepted view has a non-null
     `presentation` (view schema 2).
   - `null` otherwise, including a run with no accepted view.
   The predicate reads only the accepted summary. It never inspects pack bytes,
   story text or unaccepted state, and an unknown or hostile `packId` does not
   change the result when `presentation` is absent.
2. **Use it in both places.** `GameRun.tsx` derives `episode` and `expedition`
   from the predicate (the local names may stay). `AdventurePreparation.tsx`
   derives `sceneFirstReady` from the same call. Remove the duplicated pack-id
   comparisons; keep `hiveExpeditionPackId` for the title fallback and for the
   retained-save case above.
3. **Same experience for every expedition-kind run.** Specs 32 and 33 apply
   unchanged: current location and legal actions first, board in the collapsed
   **Map** control with one labeled travel button per advertised destination,
   character details behind a disclosure, empty party hidden, no repeated save or
   language paragraph, quiet save announcement, text mode parity, and focus on the
   location heading after entry.
4. **No generic heading in the way.** For an expedition-kind run with no title
   from `adventureTitle()`, keep the run heading as the focus target but make it
   visually hidden (existing `game-visually-hidden` class) instead of showing the
   generic "Przygoda" / "Adventure". When there is also no current location, the
   fallback moment heading is likewise visually hidden. Headings keep their
   accessible names and `lang`.
5. **Legacy and text-only runs are untouched.** A run with no `presentation`
   and not the hive pack keeps today's text-first layout, text controls and
   focus behavior. Runs with missing or unknown artwork ids keep the neutral
   fixed-catalog art required by 30.5.
6. **Contracts unchanged.** No engine, server, pack, save-format, command,
   queue, generation or reward change. Existing saves resume exactly as before.
   The CSS class names (`game-scene-first`, `game-expedition`) stay as they are;
   renaming them is out of scope.

## Out of scope

- Any change to spacing, artwork size, speech bubbles, sticky action bars or the
  map presentation (planned follow-up specs).
- Text length of packs or generated stories.
- Giving dynamic generator profiles places or artwork.

## Verification

1. **Unit** (`app/ui/src/game/sceneFirst.test.ts`): hive pack, with and without a
   view; episode pack; unknown `packId` with `presentation`; `packId: null` with
   `presentation`; view schema 1 without `presentation`; `null` view; hostile
   `packId` strings without `presentation` return `null`.
2. **Component** (`GameRun.test.tsx`, `AdventurePreparation.test.tsx`): a
   fixture run with `packId: 'fixture-art'`, a HEX `places` view and a
   `presentation` renders `.game-scene-first`, has no story-language or saved
   paragraph, shows the board only inside the collapsed Map control, shows no
   per-cell Inspect button, collapses current characters, and applies the
   scene-panel class in the preparation overlay. Add a case for a title-less run
   asserting the hidden run heading. Add a text-only legacy run asserting the
   existing layout is unchanged. Existing hive and episode tests keep passing.
3. **Focus and text mode:** in the new fixture run, assert focus moves to the
   location heading after a move, Text mode shows the same text and choices, and
   no command is sent by toggling it.
4. **Gates** (from `.github/instructions/build-workflow.instructions.md`):
   `npm test` in `app/ui`, then `./scripts/dev-workflow.ps1 -Action sync`, then
   the Spring-served browser suite. Extend `app/ui/scripts/game-expeditions.mjs`
   only to confirm the hive run still takes the scene-first path on a
   360 × 640 viewport with the first action visible. The `fixture-hex` test pack
   declares no artwork catalog, so the non-hive path is covered at component
   level, not in the browser suite.
5. Manually verify in the integrated app, in Polish and English controls, that
   starting and resuming **Nowy ul, dwa światła** shows no visible generic
   heading and no repeated paragraphs.
