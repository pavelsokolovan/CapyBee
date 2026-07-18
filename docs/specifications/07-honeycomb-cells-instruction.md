# Honeycomb Cells — Design & Implementation Guide

## Purpose

Define the visual design, SVG implementation, data model, and interaction behavior for the honeycomb progress map used on SCR-03 (Home) and SCR-09 (Memories). Replace the current uniform orange blob cells with a meaningful, emotionally differentiated cell system.

---

## Asset inventory

### Image files to place in `src/assets/`

```
src/assets/
  honeycomb/
    old-world-tab.png       (prepared externally — Old World tab header illustration)
    new-world-tab.png       (prepared externally — New World tab header illustration)
```

All other visual elements in this document are implemented as inline SVG. No additional image files are required for the honeycomb cells themselves.

---

## Color system

| Role | Fill | Stroke | Usage |
|---|---|---|---|
| Old World filled | `#EDD28A` | `#B8922A` | Memory or mission linked to old home |
| New World filled | `#A8D5A2` | `#5A9B52` | Memory or mission from new life |
| Check-in (no world) | `#F5C842` | `#C8952A` | Daily mood check-in, belongs to neither world |
| Empty | `#EDE8DF` | `#C4B99A` | Not yet earned — dashed stroke |
| Empty stroke style | — | `stroke-dasharray="5 3"` | Dashed outline only, no fill texture |

Icon text colors:
- On Old World gold: `#3d2b00`
- On New World green: `#1a4718`
- On Check-in honey: `#3d2b00`

---

## Cell type system

Each filled cell has a `type` that determines the emoji icon shown inside it.

| Type | Icon | Trigger |
|---|---|---|
| `mission` | ⭐ | `POST /api/missions/{id}/completions` succeeds |
| `memory` | 🫀 | `POST /api/memories` succeeds |
| `checkin` | 😊 | `POST /api/check-ins` succeeds |
| `friendship` | 🫂 | `POST /api/friendships` succeeds |
| `empty` | — | No icon, dashed outline only |

---

## SVG cell component

### Single cell SVG shape

Each hexagon cell is a `<polygon>` with flat-top orientation. The coordinate points below define one cell in a local coordinate system. Scale or translate using an SVG `<g transform="translate(x, y)">` wrapper.

```svg
<!-- Cell container: 64px wide × 72px tall local bounding box -->
<!-- Center point: cx=32, cy=36 -->
<polygon
  points="32,2 62,18 62,54 32,70 2,54 2,18"
  fill="FILL_COLOR"
  stroke="STROKE_COLOR"
  stroke-width="2"
  stroke-dasharray="DASH_OR_NONE"
  rx="2"
/>
```

For filled cells: `stroke-dasharray` is omitted (solid border).
For empty cells: `stroke-dasharray="5 3"`.

### Icon inside cell

Place the emoji as an SVG `<text>` element centered in the cell:

```svg
<text
  x="32"
  y="42"
  text-anchor="middle"
  font-size="22"
  fill="ICON_COLOR"
>EMOJI</text>
```

Font size `22` fits inside the 64×72 cell with comfortable margin. Do not use font sizes above `24`.

### Complete single cell example — New World mission

```svg
<g transform="translate(10, 10)">
  <polygon
    points="32,2 62,18 62,54 32,70 2,54 2,18"
    fill="#A8D5A2"
    stroke="#5A9B52"
    stroke-width="2"
  />
  <text
    x="32"
    y="42"
    text-anchor="middle"
    font-size="22"
    fill="#1a4718"
  >⭐</text>
</g>
```

### Complete single cell example — empty

```svg
<g transform="translate(10, 10)">
  <polygon
    points="32,2 62,18 62,54 32,70 2,54 2,18"
    fill="#EDE8DF"
    stroke="#C4B99A"
    stroke-width="1.5"
    stroke-dasharray="5 3"
  />
</g>
```

---

## Background honeycomb grid (SVG pattern)

The background of the honeycomb map section uses an SVG `<pattern>` to render a faint tiled hexagonal grid behind all cells. This gives the impression of an infinite hive — cells are being placed into a structure that already exists.

Implement this as an SVG `<defs>` pattern on the map container:

```svg
<svg width="100%" height="100%" style="position:absolute; top:0; left:0; pointer-events:none; opacity:0.18; z-index:0;">
  <defs>
    <pattern id="hive-bg" x="0" y="0" width="68" height="78" patternUnits="userSpaceOnUse">
      <!-- Row 1 hex -->
      <polygon
        points="34,2 64,18 64,52 34,68 4,52 4,18"
        fill="none"
        stroke="#C8952A"
        stroke-width="1"
      />
      <!-- Row 2 hex, offset by half width and full height -->
      <polygon
        points="68,41 98,57 98,91 68,107 38,91 38,57"
        fill="none"
        stroke="#C8952A"
        stroke-width="1"
      />
    </pattern>
  </defs>
  <rect width="100%" height="100%" fill="url(#hive-bg)" />
</svg>
```

Place this SVG as the first child of the map container div. Set the container to `position: relative`. The pattern SVG is `position: absolute`, covers the full container, has `pointer-events: none` so it does not block taps, and `opacity: 0.18` so it is subtle. The foreground cell SVG sits on top with `position: relative; z-index: 1`.

---

## Grid layout — cell positioning

Cells are arranged in a brick-offset (staggered) grid, matching the natural honeycomb structure. Odd rows are offset to the right by half a cell width.

### Grid constants

```js
const CELL_W = 64        // cell bounding box width
const CELL_H = 72        // cell bounding box height
const COL_STEP = 68      // horizontal distance between cell centers (CELL_W + 4px gap)
const ROW_STEP = 54      // vertical distance between row centers (accounts for hex overlap)
const ODD_ROW_OFFSET = 34  // horizontal offset applied to even-indexed rows (0-based)
```

### Cell center calculation

```js
function getCellPosition(index, columnsPerRow = 6) {
  const col = index % columnsPerRow
  const row = Math.floor(index / columnsPerRow)
  const x = col * COL_STEP + (row % 2 === 1 ? ODD_ROW_OFFSET : 0)
  const y = row * ROW_STEP
  return { x, y }
}
```

The `<g transform>` for each cell uses `translate(x, y)` from this function.

### Recommended grid sizes

| Context | Columns | Rows | Total cells |
|---|---|---|---|
| Home screen (SCR-03) | 6 | 3 | 18 |
| Memories screen (SCR-09) per world | 5 | 4 | 20 |

On screens narrower than 360px, reduce columns by 1.

---

## React component structure

### `HoneycombMap` component

File: `src/components/HoneycombMap.jsx`

Props:
```js
{
  cells: Array<CellData>,   // ordered array, index = cell position in grid
  columns: number,          // default 6
  onCellTap: (cell) => void // called when a filled cell is tapped
}
```

CellData shape:
```js
{
  id: string,
  type: 'mission' | 'memory' | 'checkin' | 'friendship' | 'empty',
  world: 'old_world' | 'new_world' | null,  // null for checkin and empty
  title: string,      // short label shown in tooltip, e.g. "Said hi to Marta"
  date: string,       // display date string, e.g. "29.06"
  empty: boolean
}
```

Internal logic:
```jsx
function getCellColors(cell) {
  if (cell.empty) return { fill: '#EDE8DF', stroke: '#C4B99A', iconColor: null, dashed: true }
  if (cell.type === 'checkin') return { fill: '#F5C842', stroke: '#C8952A', iconColor: '#3d2b00', dashed: false }
  if (cell.world === 'old_world') return { fill: '#EDD28A', stroke: '#B8922A', iconColor: '#3d2b00', dashed: false }
  if (cell.world === 'new_world') return { fill: '#A8D5A2', stroke: '#5A9B52', iconColor: '#1a4718', dashed: false }
  return { fill: '#F5C842', stroke: '#C8952A', iconColor: '#3d2b00', dashed: false }
}

function getCellIcon(type) {
  const icons = { mission: '⭐', memory: '🫀', checkin: '😊', friendship: '🫂' }
  return icons[type] ?? ''
}
```

### Tooltip component

File: `src/components/HoneycombTooltip.jsx`

Show when a filled cell is tapped. Dismiss after 3000ms or on tap outside.

```jsx
<div style={{
  background: '#3d2b00',
  color: '#FDF6E3',
  fontSize: '13px',
  padding: '6px 12px',
  borderRadius: '10px',
  position: 'absolute',
  whiteSpace: 'nowrap',
  pointerEvents: 'none',
  zIndex: 10,
  // position above the tapped cell
  bottom: 'calc(100% + 8px)',
  left: '50%',
  transform: 'translateX(-50%)'
}}>
  {icon} {cell.title} · {cell.date}
  {/* small triangle pointing down */}
  <div style={{
    position: 'absolute',
    bottom: '-5px',
    left: '50%',
    transform: 'translateX(-50%)',
    borderLeft: '5px solid transparent',
    borderRight: '5px solid transparent',
    borderTop: '5px solid #3d2b00'
  }} />
</div>
```

Wrap each cell `<g>` in a `<foreignObject>` or use a React `ref` + absolute positioned div overlay. The tooltip is positioned relative to the tapped cell's screen coordinates, not SVG coordinates — convert using `getBoundingClientRect()` on the cell element.

### Interaction behavior

```
onCellTap(cell):
  if cell.empty → do nothing
  else → show tooltip for this cell, start 3000ms timer
         if another cell is tapped → dismiss current tooltip, show new one
         if tap outside map → dismiss tooltip
```

On desktop, tooltip also shows on mouse hover (no timer — dismiss on mouse leave).

---

## Data mapping — where cells come from

The `HoneycombMap` receives a merged array of user activity. Cells are ordered chronologically (oldest first = top-left). Empty cells pad the end of the array to fill the grid.

```js
async function buildHoneycombCells(columnsPerRow = 6, rows = 3) {
  const totalCells = columnsPerRow * rows

  // fetch all activity in parallel
  const [checkIns, missions, memories, friendships] = await Promise.all([
    fetch('/api/check-ins').then(r => r.json()),
    fetch('/api/missions/completions').then(r => r.json()),
    fetch('/api/memories').then(r => r.json()),
    fetch('/api/friendships').then(r => r.json())
  ])

  // normalize to common shape, sort by date ascending
  const allActivity = [
    ...checkIns.map(c => ({
      id: c.id, type: 'checkin', world: null,
      title: c.mood, date: formatDate(c.createdAt), empty: false
    })),
    ...missions.map(m => ({
      id: m.id, type: 'mission', world: m.worldType ?? 'new_world',
      title: m.missionTitle, date: formatDate(m.completedAt), empty: false
    })),
    ...memories.map(m => ({
      id: m.id, type: 'memory', world: m.worldType,
      title: m.title, date: formatDate(m.createdAt), empty: false
    })),
    ...friendships.map(f => ({
      id: f.id, type: 'friendship', world: 'new_world',
      title: f.personLabel, date: formatDate(f.createdAt), empty: false
    }))
  ].sort((a, b) => new Date(a.date) - new Date(b.date))

  // cap to totalCells, pad remainder with empty cells
  const filled = allActivity.slice(0, totalCells)
  const emptyCount = totalCells - filled.length
  const empties = Array.from({ length: emptyCount }, (_, i) => ({
    id: `empty-${i}`, type: 'empty', world: null,
    title: '', date: '', empty: true
  }))

  return [...filled, ...empties]
}
```

Call `buildHoneycombCells()` on mount and after any successful POST that creates new activity.

---

## SCR-03 — Home screen integration

### Placement

The honeycomb map sits in its own white card section below the check-in form. Card has:
- `border-radius: 16px`
- `padding: 16px`
- `background: #FFFFFF`
- `box-shadow: none`
- `border: 1px solid #F0E8D8`

Section heading above the map: `"Twój ul"` / `"Your hive"` — 15px, font-weight 500, color `#5C3D00`.

### Grid spec for SCR-03

- 6 columns, 3 rows = 18 cells total
- Cell size: 64×72px local, rendered at ~52px wide on mobile (SVG scales to container)
- Container max-width: 420px, centered

---

## SCR-09 — Memories screen integration

### World tab headers

The Memories screen has two tabs: Stary świat (Old World) and Nowy świat (New World).

Each tab has a header illustration image placed above the cell grid for that world.

**Old World tab header:**

```jsx
<img
  src="/assets/honeycomb/old-world-tab.png"
  alt=""
  draggable="false"
  style={{
    width: '100%',
    maxWidth: '420px',
    height: 'auto',
    display: 'block',
    margin: '0 auto 16px',
    borderRadius: '12px'
  }}
/>
```

**New World tab header:**

```jsx
<img
  src="/assets/honeycomb/new-world-tab.png"
  alt=""
  draggable="false"
  style={{
    width: '100%',
    maxWidth: '420px',
    height: 'auto',
    display: 'block',
    margin: '0 auto 16px',
    borderRadius: '12px'
  }}
/>
```

Place the image between the tab selector and the honeycomb grid. Do not show the image if the tab is loading.

### Grid spec for SCR-09

The memory screen honeycomb shows only cells matching the active world tab:

- Old World tab: show only cells where `world === 'old_world'` or `type === 'checkin'`
- New World tab: show only cells where `world === 'new_world'` or `type === 'checkin'`
- 5 columns, 4 rows = 20 cells per world view
- Same cell sizing as SCR-03

Check-in cells (`type: 'checkin'`) appear in both world tabs — they are shared context, not world-specific.

### Empty state per world

If a world has zero filled cells, do not show the honeycomb grid. Show the CapyBee empty state instead (see `05-avatars-adding-instruction.md` SCR-09 rules). Show the tab header image even in the empty state.

---

## Cell fill animation — new cell earned

When a new activity is created (check-in, mission, friendship, memory) and the honeycomb map is visible on screen, animate the newly added cell:

1. The cell starts as an empty dashed outline (its natural empty state).
2. On success of the POST, identify the cell's position in the grid (it will be the last filled cell).
3. Animate:
   - Fill color transitions from `#EDE8DF` → target fill color over 400ms (CSS transition on fill).
   - Icon fades in at 300ms delay, opacity 0 → 1 over 200ms.
   - Stroke changes from dashed to solid simultaneously with the fill change.

```css
.cell-polygon {
  transition: fill 400ms ease-out;
}
.cell-icon {
  transition: opacity 200ms ease-in;
  transition-delay: 300ms;
}
```

SVG `fill` and `opacity` are animatable via CSS transitions on SVG elements. Apply the transition class to the `<polygon>` and `<text>` elements respectively.

If the map is not currently visible (e.g. the child is on the Missions screen), skip the animation — just update the data. The next time the home screen renders, the cell will already be filled.

---

## Accessibility

- The outer SVG map has `role="img"` and `aria-label="Twój ul — postęp" / "Your hive — progress"`.
- Individual cells do not have individual ARIA roles — the map is treated as a single decorative image for screen readers.
- Tooltip text is read by screen readers when it appears via `role="status"` on the tooltip container.
- Empty cells have no interactive role — `pointer-events: none` in SVG.

---

## Do not do

- Do not use `<img>` tags for individual cells — all cells are inline SVG only.
- Do not render cell emoji as separate image files.
- Do not show world-color cells on SCR-03 home map — on the home map, all filled cells use the type-based color (mission = old/new world color based on data, checkin = honey). The world tab images appear only on SCR-09.
- Do not animate cells that are off-screen.
- Do not add cell borders thicker than `stroke-width="2"`.
- Do not change the dashed empty cell to a spinner or loading state — empty cells are always static.

---

## Traceability

| Element | Screen | File |
|---|---|---|
| `HoneycombMap` component | SCR-03, SCR-09 | `src/components/HoneycombMap.jsx` |
| `HoneycombTooltip` component | SCR-03, SCR-09 | `src/components/HoneycombTooltip.jsx` |
| Background SVG pattern | SCR-03, SCR-09 | Inline in `HoneycombMap.jsx` |
| `old-world-tab.png` | SCR-09 Old World tab | `src/assets/honeycomb/old-world-tab.png` |
| `new-world-tab.png` | SCR-09 New World tab | `src/assets/honeycomb/new-world-tab.png` |
| Cell data builder | SCR-03, SCR-09 | `src/hooks/useHoneycombCells.js` |
| Color + icon helpers | SCR-03, SCR-09 | Inside `HoneycombMap.jsx` |
| US-007 | Honeycomb map on home screen | — |
| US-008 | Old World / New World visual distinction | — |
