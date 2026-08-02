# 12 — Navigation Redesign: 4 Equal Bottom Tabs + Profile in Top Bar

## Purpose

Implementation-ready instructions for GitHub Copilot to fix the bottom navigation in
`AuthenticatedHome.tsx`. Replaces the current 5-item text-only bottom nav (which wraps
into an uneven two-row layout on phone widths) with 4 equal icon+label tabs, and moves
Profile into the top bar as an avatar button.

Applies to: `SCR-03` (Authenticated Home) navigation shell, referenced in
`04-screen-descriptions.md` under "Navigation Model (MVP)".

Ground truth reviewed: `AuthenticatedHome.tsx` (header block ~L1207–1236, bottom-nav
block ~L1830–1846, `TabKey` type ~L85), `styles.css` (`.auth-topbar`/`.auth-actions`/
`.locale-control` ~L508–556, `.bottom-nav`/`.nav-item` ~L1275–1302, mobile breakpoint
~L1441–1464), `capybee.tsx` (`CapyBeeAvatar` component).

---

## 1. Problem being fixed

Current bottom nav (`AuthenticatedHome.tsx` ~L1830):

```tsx
<nav className="bottom-nav">
  {([
    ['home', text.home],
    ['missions', text.missions],
    ['friendships', text.friendships],
    ['memories', text.memories],
    ['profile', text.profile]
  ] as Array<[TabKey, string]>).map(([key, label]) => (
    <button
      key={key}
      className={activeTab === key ? 'nav-item active' : 'nav-item'}
      onClick={() => setActiveTab(key)}
    >
      {label}
    </button>
  ))}
</nav>
```

Paired with `styles.css`:

```css
.bottom-nav {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

@media (max-width: 640px) {
  .bottom-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    row-gap: 6px;
  }
}
```

This is what breaks on phone: 5 items forced into a 3-column grid under 640px produces
an orphaned second row (3 items, then 2 items left-aligned) — exactly the crowded,
wrapping layout seen in the current build. There are also no icons, so "Start" (the
only visually distinct one, via `.nav-item.active`) reads like the odd one out instead
of an intentional highlight.

Fix has two parts:
1. Drop to 4 bottom tabs (Home, Missions, Friendships, Memories) — one consistent row
   at all widths, no breakpoint-driven wrapping.
2. Move Profile into the top bar as an avatar/icon button, since it's checked far less
   often than the daily loop (US-020 mobile-first, but profile is not part of the
   5-minute daily flow described in `CapyBee_concept.md` §8).

---

## 2. Bottom nav — 4 equal icon+label tabs

### 2.1 New icon set

No icon components exist yet in the codebase. Create one file with inline SVGs.
Per established visual language (`05-avatars-adding-instruction.md`,
`CapyBee_concept.md` §banner conventions): **no human figures, no hearts** — use
abstract shapes consistent with the hive metaphor.

```tsx
// src/components/NavIcons.tsx

type IconProps = { active?: boolean };

const strokeColor = (active?: boolean) => (active ? 'var(--accent-strong)' : 'var(--muted)');

export function HomeIcon({ active }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke={strokeColor(active)} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2l7 4v6c0 5-3 8-7 10-4-2-7-5-7-10V6z" />
    </svg>
  );
}

export function MissionsIcon({ active }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke={strokeColor(active)} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 11l3 3 8-8" />
      <path d="M20 12v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h9" />
    </svg>
  );
}

export function FriendshipsIcon({ active }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke={strokeColor(active)} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 17c2-2 5-2 6 0M4 17v2M10 17v2" />
      <path d="M14 12c2-2 5-2 6 0M14 12v2M20 12v2" />
    </svg>
  );
}

export function MemoriesIcon({ active }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke={strokeColor(active)} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2l2.6 6.3L21 9l-5 4.4L17.5 21 12 17.3 6.5 21 8 13.4 3 9l6.4-.7z" />
    </svg>
  );
}
```

`HomeIcon` reuses the same hexagon silhouette as the existing `hexPattern` background
in `App.tsx` (~L171) so the honeycomb motif stays consistent between the landing page
and the in-app nav.

### 2.2 Updated bottom-nav JSX

Replace the block at `AuthenticatedHome.tsx` ~L1830:

```tsx
const bottomNavItems: Array<[TabKey, string, (p: IconProps) => JSX.Element]> = [
  ['home', text.home, HomeIcon],
  ['missions', text.missions, MissionsIcon],
  ['friendships', text.friendships, FriendshipsIcon],
  ['memories', text.memories, MemoriesIcon]
];

<nav className="bottom-nav" aria-label={text.navLabel}>
  {bottomNavItems.map(([key, label, Icon]) => {
    const isActive = activeTab === key;
    return (
      <button
        key={key}
        type="button"
        className={isActive ? 'nav-item active' : 'nav-item'}
        onClick={() => setActiveTab(key)}
        aria-current={isActive ? 'page' : undefined}
      >
        <Icon active={isActive} />
        <span>{label}</span>
      </button>
    );
  })}
</nav>
```

Add `navLabel` to both locale copy objects (`text` in `AuthenticatedHome.tsx`):

```ts
// en
navLabel: 'Main navigation',
// pl
navLabel: 'Główna nawigacja',
```

Import at the top of `AuthenticatedHome.tsx`:

```tsx
import { HomeIcon, MissionsIcon, FriendshipsIcon, MemoriesIcon } from './components/NavIcons';
```

### 2.3 CSS updates

Replace `.bottom-nav` / `.nav-item` block (~L1275–1302):

```css
.bottom-nav {
  position: fixed;
  left: 10px;
  right: 10px;
  bottom: 10px;
  z-index: 50;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  border: 1px solid var(--panel-border);
  background: rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  padding: 8px;
  backdrop-filter: blur(10px);
}

.nav-item {
  min-height: 52px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border-radius: 12px;
  border: 1px solid transparent;
  background: transparent;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--muted);
  padding: 6px 2px;
}

.nav-item span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.nav-item.active {
  background: rgba(242, 178, 51, 0.18);
  border-color: rgba(139, 107, 42, 0.24);
  color: var(--accent-strong);
}
```

Remove the mobile override that forced 3 columns (~L1456–1459 inside the
`@media (max-width: 640px)` block):

```css
/* DELETE this rule — 4-column grid now holds at every width down to 320px */
.bottom-nav {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  row-gap: 6px;
}
```

At 320px width, 4 columns with `gap: 6px` and short labels (max ~10 characters, e.g.
"Wspomnienia" at 0.72rem) fit on one line without truncation in either locale — verify
in browser devtools at 320px and 360px per `04-screen-descriptions.md` non-functional
requirements ("Mobile-first layout for 360px and above").

---

## 3. Profile moved to top bar

### 3.1 Updated header JSX

Replace the `<header className="auth-topbar panel">` block at ~L1209–1236:

```tsx
<header className="auth-topbar panel">
  <div className="auth-user">
    <CapyBeeAvatar src={capyBeeAvatar.faceHappy} size={48} alt={user.displayName} className="avatar" />
    <div>
      <h1>{profile ? profile.nickname : user.displayName}</h1>
      {profile ? null : <p>{user.email}</p>}
    </div>
  </div>
  <div className="auth-actions">
    <label className="locale-control">
      <select
        aria-label={text.language}
        value={locale}
        onChange={(event) => {
          const nextLocale = event.target.value as 'en' | 'pl';
          setLocale(nextLocale);
          if (profile) {
            updateProfile({ preferredLocale: nextLocale });
          }
        }}
      >
        <option value="en">EN</option>
        <option value="pl">PL</option>
      </select>
    </label>
    <button
      type="button"
      className={activeTab === 'profile' ? 'profile-nav-button active' : 'profile-nav-button'}
      onClick={() => setActiveTab('profile')}
      aria-label={text.profile}
      aria-current={activeTab === 'profile' ? 'page' : undefined}
    >
      <CapyBeeAvatar src={capyBeeAvatar.faceHappy} size={28} alt="" className="profile-nav-avatar" />
    </button>
    <a href="/logout" className="secondary-button">{text.logout}</a>
  </div>
</header>
```

The profile button reuses the same avatar the child already sees on the left of the
header, so it reads as "your account" without introducing a new icon system just for
this one entry point. `activeTab` and `TabKey` do not change — `'profile'` stays a
valid tab, it's just no longer reachable from the bottom row.

### 3.2 CSS additions

Add near `.locale-control` (~L538):

```css
.profile-nav-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: transparent;
  padding: 0;
  cursor: pointer;
}

.profile-nav-button.active {
  border-color: var(--accent-strong);
  background: rgba(242, 178, 51, 0.18);
}

.profile-nav-avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
```

`.auth-actions` already uses `display: flex; align-items: center; gap: 10px;`
(~L532–536) — the new button drops in between the locale select and the logout link
with no layout changes needed there.

### 3.3 Mobile behavior

`.auth-actions` already goes `width: 100%; justify-content: space-between;` under
640px (~L1447–1450). With three children now (locale select, profile button, logout),
confirm spacing still reads cleanly at 320px — if it feels cramped, wrap the locale
select and profile button in a shared `<div>` so logout stays pinned to the far right:

```tsx
<div className="auth-actions">
  <div className="auth-actions-primary">
    <label className="locale-control">...</label>
    <button className="profile-nav-button" ...>...</button>
  </div>
  <a href="/logout" className="secondary-button">{text.logout}</a>
</div>
```

```css
.auth-actions-primary {
  display: flex;
  align-items: center;
  gap: 10px;
}
```

Only add this wrapper if visual testing at 320–360px shows crowding — otherwise the
flat structure in §3.1 is simpler and sufficient.

---

## 4. Out of scope for this pass

- Changing what the Profile screen itself shows (`activeTab === 'profile'` content
  block, ~L1705 onward) — untouched.
- Adding a settings gear icon or second entry point to Profile — one entry point
  (top-bar avatar) is enough for MVP.
- Animating the nav transition between tabs (e.g. sliding indicator) — flat state
  change is enough; can be a later polish pass once the 4-tab layout is confirmed to
  read well on real devices.
- Redesigning the honeycomb-hexagon `HomeIcon` shape beyond reusing the existing
  `hexPattern` silhouette — no new icon exploration needed here.

---

## Traceability

- Fixes the wrapping/crowding bug visible in current phone build of `SCR-03`
  (Authenticated Home) navigation, referenced in `04-screen-descriptions.md` under
  "Navigation Model (MVP)".
- Supports US-020 (mobile-first, 360px+, thumb-reachable primary actions) by removing
  the breakpoint that forced a 2-row nav.
- Keeps `SCR-05` (Child Profile Settings) reachable in one tap, per its own success
  criteria, just relocates the entry point.
- No changes to API contracts, data model, or `TabKey` — purely a navigation shell
  and styling change.
