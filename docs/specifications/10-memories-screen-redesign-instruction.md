# 09 — Memories Screen Redesign: Illustration Consistency & Card Hierarchy

## Purpose

Implementation instructions for GitHub Copilot to fix `SCR-09 Memory Space` (`/memories`).
Covers: unifying the illustration style between the static world banner and the real
honeycomb hive, softening the favorite/delete controls on memory cards, and a
verification pass confirming the shared hive (`HoneycombMap`) already resolves the
scroll/cropping problem previously reported. Photo/thumbnail support is explicitly
**out of scope** for this pass — see Section 4.

Applies to: `SCR-09 - Memory Space (Old World / New World)` (`/memories`).
Related docs: `04-screen-descriptions.md`, `07-honeycomb-cells-instruction.md`,
`08-missions-redesign-instruction.md`.
Related files: `app/ui/src/AuthenticatedHome.tsx` (memories tab, ~L1093–L1188),
`app/ui/src/components/HoneycombMap.tsx`, `app/ui/src/hooks/useHoneycombCells.ts`,
`app/ui/src/styles.css`, `app/ui/src/assets/honeycomb/old-world-tab.png`,
`app/ui/src/assets/honeycomb/new-world-tab.png`.

---

## 0. Current State (confirmed in code)

- The Memories tab already renders the shared, data-driven hive via `HoneycombMap` +
  `useHoneycombCells`, fed by `allMemories` (unfiltered, all cell types: `checkin`,
  `mission`, `memory`, `friendship`). This is intentional and correct — the same hive
  is shown on Home, Missions, Friendships, and Memories so progress reads as one
  continuous hive, not four separate ones. **No change needed here.**
- `HoneycombMap` already uses a vertical brick-offset grid with `ResizeObserver`-driven
  scaling and `IntersectionObserver`-based "load more empty rows" — cells are never
  cropped, and full hexagons are always shown. This resolves the horizontal-scroll
  cropping issue seen in earlier screens. **Section 5 is a verification checklist only,
  not new implementation work.**
- What's still inconsistent: the static `world-tab-header-image` (old/new world banner,
  rendered above the Add Memory form) uses glossy gradient-shaded hexagons and a
  painterly lantern glow, while `HoneycombMap` cells are flat solid fills with a 2px
  stroke. These sit on the same screen and visibly clash.
- Memory cards (`.list-card` in the list below the hive) show a plain outline star for
  every entry regardless of favorite state, and a full-width `Delete` text button with
  equal visual weight to the rest of the card content.

---

## 1. Illustration Style — Unify Banner With Hive

**Problem:** `old-world-tab.png` / `new-world-tab.png` are glossy, gradient-shaded,
semi-painterly illustrations. `HoneycombMap` cells are flat, solid-fill, 2px-stroke
shapes. Two illustration languages on one screen read as unpolished.

**Fix:** Regenerate both banner images as flat illustrations, reusing the exact color
tokens already defined in `HoneycombMap.tsx` (`getCellColors`), so the banner reads as
part of the same visual system as the hive below it:

| Token | Old World | New World |
|---|---|---|
| Fill | `#EDD28A` | `#A8D5A2` |
| Stroke | `#B8922A` | `#5A9B52` |
| Icon color | `#3d2b00` | `#1a4718` |

Style rules for the new banner art:
- Flat fills only — no gradients, no drop shadows, no glow/blur effects (this removes
  the lantern's glossy glow specifically).
- Single stroke weight (2px, matching `.cell-polygon`) around any hexagon shapes used
  in the banner.
- Icon linework (lantern, house, bee, sprout) should match the flat 2px-stroke style
  already used for the hive's own icons (`📷`/`⭐`/`🫶` glyphs are emoji today — if the
  banner icons are custom SVG, match that same simple linework weight rather than the
  currently more detailed/shaded icon style).
- Keep the CapyBee mascot (photo-real rendered avatar) completely separate from this —
  it should never be flattened. It's the one deliberately "special" rendering style in
  the app; everything else (banners, hive cells, decorative icons) should be flat and
  consistent with each other.

No JSX changes required for this section — swap the two PNG assets at
`app/ui/src/assets/honeycomb/old-world-tab.png` and `new-world-tab.png` once
regenerated, keeping the same filenames and dimensions so `.world-tab-header-image`
CSS continues to apply without changes.

---

## 2. Memory Card — Favorite Star & Delete De-emphasis

**Problem:** `.ghost-button` renders `★`/`☆` with no visual difference beyond the glyph
itself (both are default text color), so favorited memories don't stand out in the
list. `.danger-button` renders `Delete` as a full-width, equally-weighted control,
competing with the memory content instead of sitting behind it.

**Target:**
- Favorite star fills solid gold when active.
- Delete becomes a small icon-only control, low visual weight, bottom-right of the card.

### 2.1 JSX changes (`AuthenticatedHome.tsx`, memories list render, ~L1172–L1183)

Replace:

```jsx
<div className="list-stack">
  {memories.map((entry) => (
    <article key={entry.id} className="list-card">
      <div className="line-between">
        <strong>{entry.title || 'Memory'}</strong>
        <button className="ghost-button" onClick={() => toggleFavorite(entry)}>
          {entry.isFavorite ? '★' : '☆'}
        </button>
      </div>
      {entry.textContent ? <p>{entry.textContent}</p> : null}
      <button className="danger-button" onClick={() => deleteMemory(entry.id)}>Delete</button>
    </article>
  ))}
</div>
```

With:

```jsx
<div className="list-stack">
  {memories.map((entry) => (
    <article key={entry.id} className="list-card memory-card">
      <div className="line-between">
        <strong>{entry.title || 'Memory'}</strong>
        <button
          className={entry.isFavorite ? 'favorite-star active' : 'favorite-star'}
          onClick={() => toggleFavorite(entry)}
          aria-label={text.favoriteMemory}
          aria-pressed={entry.isFavorite}
        >
          {entry.isFavorite ? '★' : '☆'}
        </button>
      </div>
      {entry.textContent ? (
        <p className="memory-story-preview">{entry.textContent}</p>
      ) : null}
      <div className="memory-card-footer">
        <button
          className="icon-delete-button"
          onClick={() => deleteMemory(entry.id)}
          aria-label={text.deleteMemory}
        >
          🗑
        </button>
      </div>
    </article>
  ))}
</div>
```

Add one new copy key (both locales, `copy.en` / `copy.pl`):

```ts
// en
deleteMemory: 'Delete memory',
// pl
deleteMemory: 'Usuń wspomnienie',
```

### 2.2 CSS additions (`styles.css`)

Add near the existing `.list-card` / `.danger-button` rules — do not remove
`.danger-button`/`.ghost-button`, they're still used elsewhere (e.g. friendship entries):

```css
.favorite-star {
  min-height: 44px;
  min-width: 44px;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: #c8952a; /* outline star, same as before by default */
  cursor: pointer;
}

.favorite-star.active {
  color: #f2b233; /* solid gold fill when favorited */
}

.memory-story-preview {
  margin-top: 8px;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.memory-card-footer {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.icon-delete-button {
  min-height: 36px;
  min-width: 36px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(137, 43, 34, 0.5);
  font-size: 16px;
  cursor: pointer;
}

.icon-delete-button:hover,
.icon-delete-button:focus-visible {
  background: rgba(157, 54, 44, 0.08);
  color: #892b22;
}
```

Note: `★` is already a filled-star glyph, `☆` an outline glyph — the color change on
`.active` is what makes the *favorited* state visually distinct at a glance (gold vs.
muted brown), not the glyph shape, since the glyph already swaps in the existing JSX.

---

## 3. Line-Clamping Long Stories

`memory-story-preview` above clamps card preview text to 2 lines via
`-webkit-line-clamp`. This prevents long stories from making cards vary wildly in
height in the list. No "read more" expansion is required for this pass — if a fuller
view is wanted later, that's a separate follow-up (e.g. tap card to open a detail
sheet), not part of this spec.

---

## 4. Photo/Thumbnail Support — Explicitly Deferred

The backend already has a `media_url` column (`MemoryEntry.mediaUrl`,
`CreateMemoryRequest.mediaUrl`, `UpdateMemoryRequest.mediaUrl`) wired end-to-end as a
plain string URL field, but the Memories UI does not yet expose any upload control or
render `mediaUrl` on cards. **Do not implement file upload or storage in this pass** —
this is intentionally deferred to a dedicated follow-up spec once the storage approach
(e.g. a Fly volume + a photo-serving endpoint) is decided. No UI or schema changes
related to photos should be made as part of this instruction file.

---

## 5. Verification Checklist — Hive Scroll/Crop (No New Work Expected)

These items should already pass against the current `HoneycombMap.tsx`. Copilot should
verify, not rebuild:

- [ ] No hexagon cell is ever rendered partially cropped at the edge of the visible
      scroll window, at any container width.
- [ ] Scrolling the hive (vertical) always settles with complete rows visible; no cell
      is left half-visible after a scroll gesture.
- [ ] Hovering a cell (desktop, `hover: hover` + `pointer: fine`) shows the tooltip with
      title + date; tapping a cell on mobile shows the same tooltip with auto-hide.
- [ ] Scrolling to the bottom sentinel appends more empty cells without layout shift or
      cropping the last filled row.

If any of the above fails on a specific viewport width, file it as a targeted bug fix
against `HoneycombMap.tsx` rather than re-implementing the component.

---

## 6. Acceptance Checklist

- [ ] `old-world-tab.png` and `new-world-tab.png` are replaced with flat-illustration
      versions using the exact color tokens from `getCellColors` (Section 1); no
      gradients, glow, or shading remain.
- [ ] Memory cards show a solid gold star (`.favorite-star.active`) for favorited
      entries and a muted outline star otherwise — distinguishable at a glance in the
      list.
- [ ] Delete is a small icon-only button (`.icon-delete-button`), bottom-right of the
      card, not a full-width labeled button.
- [ ] Long memory stories are clamped to 2 lines in the card preview.
- [ ] `deleteMemory` copy key exists in both `en` and `pl` locales and is used as the
      delete button's `aria-label`.
- [ ] No changes made to `mediaUrl` handling, upload UI, or storage — deferred per
      Section 4.
- [ ] Shared hive (`allMemories`, all cell types, no per-tab filtering) continues to
      render identically across Home/Missions/Friendships/Memories tabs — unchanged.
- [ ] Hive scroll/crop verification checklist (Section 5) passes with no regressions.
