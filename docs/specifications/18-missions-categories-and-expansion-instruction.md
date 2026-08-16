# 18 — Mission Categories, Filter Navigation, and 20 New Missions

## Purpose

Implementation instructions for GitHub Copilot to:
1. Fix a hard-coded query limit that will silently hide existing missions once the pool grows.
2. Expose `Mission.category` (already stored, never returned) to the frontend.
3. Add a category filter — chip row with one small icon per category — to `SCR-06 Missions
   List`, so a ~45-mission pool stays easy to browse on a phone.
4. Seed 20 new missions (bilingual EN/PL), bringing the active pool from ~25 to ~45.

Applies to: `SCR-06 - Missions List` (`/missions`).
Related docs: `04-screen-descriptions.md`, `08-missions-redesign-instruction.md`,
`06-capybee-phrases-instruction.md`.

---

## 1. Problem Being Fixed

Current state:
- `MissionRepository.findTop20ByActiveOrderByCreatedAtDesc` caps results at the **20 most
  recently created** active missions. Today the pool happens to be ~25 missions, so 5 are
  already silently invisible to children. Adding 20 more missions on top would push the
  original ~25 missions out of the result entirely — children would only ever see the
  newest 20, and the older ones would never resurface even though they're still `active`
  in the database. **This must be fixed before the new missions are seeded**, or the
  seed will make the problem worse, not better.
- `Mission.category` is stored on the entity and used internally for grouping, but
  `MissionResponse` never returns it, so the frontend has no way to filter by it.
- The Missions tab renders every active mission as one long vertical scroll
  (`visibleMissions.map(...)` in `AuthenticatedHome.tsx`). At ~25 missions this is
  already a long scroll; at ~45 it stops being easy for a 12-year-old to browse on a
  phone.

Target state:
- All active missions are queried (no artificial 20-row ceiling).
- `category` is part of the mission API response.
- The Missions tab shows a horizontal row of category chips, each with a small abstract
  icon (in the app's existing line-icon style — see `NavIcons.tsx`). Tapping a chip
  filters the list client-side. No category is visually ranked above another — same
  chip size, same style, only the active one is highlighted, exactly like the existing
  Old World / New World `segment-control` pattern on `SCR-09 Memory Space`.
- The existing per-child suggestion ordering (unseen/not-recently-actioned missions
  first — see `getLastActionedAt` in `MissionService`) is preserved *within* whatever
  category is selected. Filtering never overrides that ordering.
- 20 new missions exist in the database, split across the 5 existing categories, in the
  same tone as the existing set (short, concrete, skippable, never phrased as
  hard/easy).

---

## 2. Backend: Remove the 20-Row Ceiling

**File:** `app/server/src/main/java/com/capybee/server/repository/MissionRepository.java`

Replace the two `findTop20...` methods. There is no product reason to cap the active
mission pool — the frontend already handles empty/long lists, and per-child ordering
(Section 4 below) is what actually decides what a child sees first.

```java
public interface MissionRepository extends JpaRepository<Mission, UUID> {

    List<Mission> findByActiveOrderByCreatedAtDesc(boolean active);

    List<Mission> findAllByOrderByCreatedAtDesc();

    @Query("select m from Mission m where m.active = :active")
    List<Mission> findActiveMissionsForProfileOrderByLastActionedAt(@Param("profileId") UUID profileId,
            @Param("active") boolean active);
}
```

**File:** `app/server/src/main/java/com/capybee/server/service/MissionService.java`

Update the two call sites in `getMissions`:

```java
List<Mission> missions = Boolean.TRUE.equals(active)
        ? missionRepository.findByActiveOrderByCreatedAtDesc(true)
        : missionRepository.findAllByOrderByCreatedAtDesc();
```

Nothing else in `getMissions` needs to change — the existing `nullsFirst` sort by
`getLastActionedAt` still runs after this and still determines display order.

---

## 3. Backend: Expose `category` on the API

**File:** `app/server/src/main/java/com/capybee/server/web/dto/MissionResponse.java`

```java
package com.capybee.server.web.dto;

import java.util.UUID;

public record MissionResponse(
        UUID id,
        String code,
        String title,
        String timeHint,
        String description,
        String category,
        boolean active) {
}
```

**File:** `app/server/src/main/java/com/capybee/server/service/MissionService.java`

Update `toMissionResponse`:

```java
return new MissionResponse(
        mission.getId(),
        mission.getCode(),
        localizedTitle,
        localizedTimeHint,
        mission.getDescription(),
        mission.getCategory(),
        mission.isActive());
```

Existing missions already have a `category` value from `V4__missions_redesign.sql`, so
no backfill is needed. Missions with a `null` category (there shouldn't be any active
ones, but defensively) should be treated by the frontend as belonging to the `all` chip
only — see Section 5.

---

## 4. Frontend: Category Model

**File:** `app/ui/src/AuthenticatedHome.tsx`

Extend the `Mission` interface:

```ts
interface Mission {
  id: string;
  code: string;
  title: string;
  timeHint: string;
  description: string;
  category?: string;
  active: boolean;
}
```

Define the five known categories plus the "all" default. Keep this list in one place so
new categories are a one-line change:

```ts
type MissionCategoryKey = 'all' | 'social' | 'new_world' | 'old_world' | 'reflection' | 'exploration';

const missionCategoryOrder: MissionCategoryKey[] = [
  'all',
  'social',
  'new_world',
  'old_world',
  'reflection',
  'exploration'
];
```

Add a `missionCategory` state, defaulting to `'all'`:

```ts
const [missionCategory, setMissionCategory] = useState<MissionCategoryKey>('all');
```

Replace the existing `visibleMissions` memo so it filters on top of the existing order
(do not re-sort — `missions` already arrives pre-ordered by suggestion priority from the
API):

```ts
const visibleMissions = useMemo(() => {
  if (missionCategory === 'all') return missions;
  return missions.filter((mission) => mission.category === missionCategory);
}, [missions, missionCategory]);
```

Reset the filter when missions are refetched only if the currently selected category has
zero matches (avoids the child landing on a dead filter after an admin deactivates a
mission) — optional but recommended:

```ts
useEffect(() => {
  if (missionCategory === 'all') return;
  const hasAny = missions.some((mission) => mission.category === missionCategory);
  if (!hasAny) setMissionCategory('all');
}, [missions, missionCategory]);
```

---

## 5. Frontend: Category Icons

Create a new file `app/ui/src/components/MissionCategoryIcons.tsx`, matching the
existing stroke-based, abstract, non-human icon style from `NavIcons.tsx` (24×24
viewBox, `strokeWidth 1.8`, round caps/joins). Reuses the same `strokeColor(active)`
convention — pass `active` for the currently selected chip.

Visual language (consistent with `frontend-design` principles already used elsewhere in
CapyBee — no human figures, no hearts, no padlocks):

- **All** — a small six-point sparkle (the "surprise me" / shuffle feel; distinct from
  the honeycomb hexagon so it doesn't read as "old world").
- **Social** (`social`) — two overlapping circles with a short connecting arc — echoes
  the existing `FriendshipsIcon` language without duplicating it exactly.
- **New World** (`new_world`) — a simple two-stroke sprout (stem + curved leaf) — ties
  to the "growing, piece by piece" language already used for New World in the concept
  doc.
- **Old World** (`old_world`) — a hexagon outline with a small filled dot at its center
  — echoes the "sealed honeycomb cell, precious, kept" language from the concept doc.
- **Feelings** (`reflection`) — three short, softly staggered horizontal wave lines —
  a calm/breathing motif, distinct from a literal weather cloud.
- **Explore** (`exploration`) — a circle with one small diamond mark near its edge — an
  abstract compass, avoiding a magnifying-glass (surveillance-coded) or a pin (map-pin
  shapes read fine, but a compass keeps it consistent with the "let's find your people"
  wayfinding language already in the concept doc).

```tsx
export type MissionCategoryIconProps = { active?: boolean };

const strokeColor = (active?: boolean) => (active ? 'var(--accent-strong)' : 'var(--muted)');

export function CategoryAllIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M18 6l-2.5 2.5M8.5 15.5L6 18" />
    </svg>
  );
}

export function CategorySocialIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="9" cy="10" r="4" />
      <circle cx="16" cy="13" r="4" />
    </svg>
  );
}

export function CategoryNewWorldIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 21V11" />
      <path d="M12 12c0-4 3-6 6-6 0 4-2 6-6 6z" />
      <path d="M12 15c0-3-2.5-5-5.5-5 0 3.5 2.5 5 5.5 5z" />
    </svg>
  );
}

export function CategoryOldWorldIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polygon points="12,3 20,7.5 20,16.5 12,21 4,16.5 4,7.5" />
      <circle cx="12" cy="12" r="1.6" fill={strokeColor(active)} stroke="none" />
    </svg>
  );
}

export function CategoryReflectionIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 9c2-1.5 4-1.5 6 0s4 1.5 6 0" />
      <path d="M4 14c2-1.5 4-1.5 6 0s4 1.5 6 0" />
    </svg>
  );
}

export function CategoryExploreIcon({ active }: MissionCategoryIconProps) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke={strokeColor(active)}
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="8" />
      <path d="M15 9l-2 5-3 1 2-5z" />
    </svg>
  );
}
```

Do **not** color-code the icons by category (e.g. tinting Old World gold, New World
green) inside the chip itself — that would start visually ranking/grouping missions in a
way that reads as competitive or hierarchical among categories. Keep every chip icon the
same neutral `strokeColor`, exactly like `NavIcons.tsx` does for bottom-nav items. Only
the chip's *active state* background changes (same treatment as `.segment.active`).

---

## 6. Frontend: Chip Row Markup

Add localized chip labels to both locale text objects (`text` for `en`, and the Polish
equivalent block), near the existing `missions`/`missionSuggestion` keys:

```ts
// English block
missionCategoryAll: 'All',
missionCategorySocial: 'People',
missionCategoryNewWorld: 'New World',
missionCategoryOldWorld: 'Old World',
missionCategoryReflection: 'Feelings',
missionCategoryExploration: 'Explore',

// Polish block
missionCategoryAll: 'Wszystkie',
missionCategorySocial: 'Ludzie',
missionCategoryNewWorld: 'Nowy świat',
missionCategoryOldWorld: 'Stary świat',
missionCategoryReflection: 'Uczucia',
missionCategoryExploration: 'Odkrywaj',
```

Add a lookup so the JSX loop can pull the right icon/label per key without a big
`if/else`:

```ts
const missionCategoryIcon: Record<MissionCategoryKey, (props: { active?: boolean }) => JSX.Element> = {
  all: CategoryAllIcon,
  social: CategorySocialIcon,
  new_world: CategoryNewWorldIcon,
  old_world: CategoryOldWorldIcon,
  reflection: CategoryReflectionIcon,
  exploration: CategoryExploreIcon
};

const missionCategoryLabelKey: Record<MissionCategoryKey, keyof typeof text> = {
  all: 'missionCategoryAll',
  social: 'missionCategorySocial',
  new_world: 'missionCategoryNewWorld',
  old_world: 'missionCategoryOldWorld',
  reflection: 'missionCategoryReflection',
  exploration: 'missionCategoryExploration'
};
```

Insert the chip row in the Missions tab, right after the `<h2>{text.missions}</h2>`
title block and before `missionsTabImage`, in
`app/ui/src/AuthenticatedHome.tsx` (around line 1671, inside the existing
`activeTab === 'missions'` block):

```tsx
<div className="mission-category-row" role="tablist" aria-label={text.missions}>
  {missionCategoryOrder.map((key) => {
    const Icon = missionCategoryIcon[key];
    const isActive = missionCategory === key;
    return (
      <button
        key={key}
        type="button"
        role="tab"
        aria-selected={isActive}
        className={isActive ? 'mission-chip active' : 'mission-chip'}
        onClick={() => setMissionCategory(key)}
      >
        <Icon active={isActive} />
        <span>{text[missionCategoryLabelKey[key]]}</span>
      </button>
    );
  })}
</div>
```

Update the empty state so it's category-aware — a child filtering "Feelings" with zero
matches should not see the generic "no missions right now" copy that implies the *whole*
app is out of missions:

```tsx
{visibleMissions.length === 0 ? (
  <div className="capybee-center-block">
    <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
    <CapyBeeBubble
      text={missionCategory === 'all' ? text.missionEmpty : text.missionEmptyCategory}
    />
  </div>
) : ( /* existing list-stack rendering, unchanged */ )}
```

Add the new phrase to both locale blocks:

```ts
// English
missionEmptyCategory: "Nothing here right now - try another group, or tap All.",
// Polish
missionEmptyCategory: 'Nic tu teraz nie ma - spróbuj innej grupy albo kliknij Wszystkie.',
```

---

## 7. CSS

**File:** `app/ui/src/styles.css`

Add near the existing `.segment-control` / `.segment` rules (around line 1248), reusing
the same visual weight as the Old World / New World segment control so category
switching reads as the same *kind* of control the child already knows from the Memories
tab:

```css
.mission-category-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px 2px 10px;
  margin-bottom: 4px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.mission-category-row::-webkit-scrollbar {
  display: none;
}

.mission-chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(139, 107, 42, 0.2);
  background: rgba(255, 255, 255, 0.75);
  color: var(--muted);
  font-size: 0.85rem;
  white-space: nowrap;
}

.mission-chip.active {
  background: rgba(242, 178, 51, 0.2);
  border-color: var(--accent-strong);
  color: var(--ink);
}
```

`min-height: 40px` plus the horizontal padding keeps chips comfortably above the 44×44px
tap-target guidance in combination with their touch padding; if design QA measures a chip
below 44px tall on a real device, bump `min-height` to `44px`.

---

## 8. Twenty New Missions — Seed Data

**New file:** `app/server/src/main/resources/db/migration/V8__missions_expansion.sql`

Balanced 4 per category. Same shape as `V4__missions_redesign.sql`: `code` is a stable
slug (never shown to the child), `title`/`title_en` duplicate for the non-null legacy
`title` column, `title_pl` carries proper Polish diacritics directly this time (the
client-side override map in Section 9 is the real source of truth either way, so this is
belt-and-suspenders).

```sql
insert into missions (
    id, code, title, title_en, title_pl, description,
    time_hint_en, time_hint_pl, category, active, created_at, updated_at
) values
    ('b28b133c-1553-4bc8-97c4-0198d3febc98', 'invite_lunch_sit_together', 'Invite someone to sit with you at lunch', 'Invite someone to sit with you at lunch', 'Zaproś kogoś, żeby usiadł z tobą na lunchu', 'Invite someone to sit with you at lunch.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true, now(), now()),
    ('7c58c539-6f11-416a-9138-5099442b491d', 'learn_classmate_name', 'Learn one classmate''s name you didn''t know', 'Learn one classmate''s name you did not know', 'Naucz się imienia kogoś z klasy, kogo jeszcze nie znałeś/aś', 'Learn one classmate''s name you did not know yet.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true, now(), now()),
    ('fdcda8c8-a8fb-44f3-a91d-d51455ab31fa', 'share_something_funny', 'Share something funny with someone', 'Share something funny with someone', 'Podziel się z kimś czymś zabawnym', 'Share something funny with someone today.', 'this takes 1 minute', 'to zajmie 1 minutę', 'social', true, now(), now()),
    ('ac4e32a9-b92f-477c-ac1a-87415d7f9dca', 'say_thank_you_helper', 'Say thank you to someone who helped you this week', 'Say thank you to someone who helped you this week', 'Podziękuj komuś, kto ci ostatnio pomógł/pomogła', 'Say thank you to someone who helped you this week.', 'this takes 1 minute', 'to zajmie 1 minutę', 'social', true, now(), now()),
    ('12a1d821-76d8-4815-8c11-a025a77220d6', 'try_good_morning_local_language', 'Try saying good morning in the new language', 'Try saying good morning in the new language', 'Spróbuj powiedzieć dzień dobry w nowym języku', 'Try saying good morning in the new language.', 'this takes 1 minute', 'to zajmie 1 minutę', 'new_world', true, now(), now()),
    ('c5d18c96-db94-4222-83de-ce8756362ce2', 'find_nearest_park', 'Find the nearest park or green space near your home', 'Find the nearest park or green space near your home', 'Znajdź najbliższy park lub zielone miejsce blisko domu', 'Find the nearest park or green space near your home.', 'this takes 5 minutes', 'to zajmie 5 minut', 'new_world', true, now(), now()),
    ('70e56044-3ccb-4db8-89f5-d37cb6896bab', 'notice_new_neighborhood_sound', 'Notice one sound your new neighborhood makes that your old one didn''t', 'Notice one sound your new neighborhood makes that your old one did not', 'Zauważ jeden dźwięk w nowej okolicy, którego nie było w starej', 'Notice one sound your new neighborhood makes that your old one did not.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'new_world', true, now(), now()),
    ('c8478a71-4466-4306-8e18-4d44b0390db3', 'find_busiest_market_day', 'Find out what day the local market or store is busiest', 'Find out what day the local market or store is busiest', 'Dowiedz się, w który dzień lokalny sklep lub targ jest najbardziej ruchliwy', 'Find out what day the local market or store is busiest.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'new_world', true, now(), now()),
    ('3535221a-dd1c-41fd-9e1d-1b4df523bca3', 'listen_song_reminds_home', 'Listen to a song that reminds you of home', 'Listen to a song that reminds you of home', 'Posłuchaj piosenki, która przypomina ci dom', 'Listen to a song that reminds you of home.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'old_world', true, now(), now()),
    ('87001a1a-5cb2-4c21-8b1c-f77109f4f324', 'write_favorite_holiday_tradition', 'Write down your favorite holiday tradition from home', 'Write down your favorite holiday tradition from home', 'Zapisz swoją ulubioną tradycję świąteczną z domu', 'Write down your favorite holiday tradition from home.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'old_world', true, now(), now()),
    ('017a2da5-9a2c-4031-b84d-dc8aafa8710d', 'describe_old_bedroom_memory', 'Describe your old bedroom from memory', 'Describe your old bedroom from memory', 'Opisz z pamięci swój stary pokój', 'Describe your old bedroom from memory.', 'this takes 5 minutes', 'to zajmie 5 minut', 'old_world', true, now(), now()),
    ('daf2ee20-b45d-4ae0-bc51-1b47fb0eee91', 'recall_joke_from_home', 'Think of a joke someone back home used to tell you', 'Think of a joke someone back home used to tell you', 'Przypomnij sobie żart, który ktoś z domu ci opowiadał', 'Think of a joke someone back home used to tell you.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'old_world', true, now(), now()),
    ('4a636be8-8e69-4dd9-b52d-cd4454fbdcc3', 'name_one_thing_proud_week', 'Name one thing you''re proud of from this week', 'Name one thing you are proud of from this week', 'Wskaż jedną rzecz, z której jesteś dumny/a w tym tygodniu', 'Name one thing you are proud of from this week.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'reflection', true, now(), now()),
    ('782b5e6b-4e6e-43c5-8aa1-843ddfb69d66', 'write_something_surprised_today', 'Write down one thing that surprised you today', 'Write down one thing that surprised you today', 'Zapisz jedną rzecz, która cię dziś zaskoczyła', 'Write down one thing that surprised you today.', 'this takes 1 minute', 'to zajmie 1 minutę', 'reflection', true, now(), now()),
    ('a2278653-fbb4-424d-9213-dc9fa2009750', 'one_word_today_felt', 'Think of one word that describes how today felt', 'Think of one word that describes how today felt', 'Wymyśl jedno słowo, które opisuje dzisiejszy dzień', 'Think of one word that describes how today felt.', 'this takes 1 minute', 'to zajmie 1 minutę', 'reflection', true, now(), now()),
    ('98004773-9587-437f-9bb1-bf3707e9c60d', 'notice_small_win_today', 'Notice one small thing that went better than you expected', 'Notice one small thing that went better than you expected', 'Zauważ jedną małą rzecz, która poszła lepiej, niż się spodziewałeś/aś', 'Notice one small thing that went better than you expected.', 'this takes 2 minutes', 'to zajmie 2 minuty', 'reflection', true, now(), now()),
    ('d969912d-8d93-4170-83d2-01467d2b0346', 'find_nearest_library', 'Find out where the library is near your home', 'Find out where the library is near your home', 'Dowiedz się, gdzie jest biblioteka blisko domu', 'Find out where the library is near your home.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'exploration', true, now(), now()),
    ('08536acb-39d6-4ebb-adf5-2b72c82a3948', 'look_up_local_holiday', 'Look up one holiday celebrated here that''s new to you', 'Look up one holiday celebrated here that is new to you', 'Sprawdź jedno święto obchodzone tutaj, którego wcześniej nie znałeś/aś', 'Look up one holiday celebrated here that is new to you.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'exploration', true, now(), now()),
    ('251e5cb7-47c7-498f-af31-a7ee18249de4', 'find_new_walk_route', 'Find one new route to walk or bike this week', 'Find one new route to walk or bike this week', 'Znajdź nową trasę na spacer lub rower w tym tygodniu', 'Find one new route to walk or bike this week.', 'this takes 5 minutes', 'to zajmie 5 minut', 'exploration', true, now(), now()),
    ('b8e30c68-a3ef-434a-a09d-65a555f0b2e7', 'discover_recess_game', 'Discover what game kids play at recess here', 'Discover what game kids play at recess here', 'Dowiedz się, w co bawią się dzieci na przerwie', 'Discover what game kids play at recess here.', 'this takes 3 minutes', 'to zajmie 3 minuty', 'exploration', true, now(), now())
on conflict (code) do nothing;
```

Category balance: 4× `social`, 4× `new_world`, 4× `old_world`, 4× `reflection`,
4× `exploration` — 20 total, none framed as harder/easier than another, all completable
in 1–5 minutes, matching the existing pool's tone and length.

---

## 9. Client-Side Title Overrides

**File:** `app/ui/src/AuthenticatedHome.tsx`

Following the existing pattern (`missionTitleOverridesPl` / `missionTitleOverridesEn`
around line 104), append the 20 new codes so the exact display copy — including Polish
diacritics and the gender-neutral `/a`/`/aś` slash forms already used elsewhere in the
app (see `was_nice: 'Był/a dla mnie miły/a'`) — is guaranteed regardless of what ends up
in the database:

```ts
// add to missionTitleOverridesPl
invite_lunch_sit_together: 'Zaproś kogoś, żeby usiadł z tobą na lunchu',
learn_classmate_name: 'Naucz się imienia kogoś z klasy, kogo jeszcze nie znałeś/aś',
share_something_funny: 'Podziel się z kimś czymś zabawnym',
say_thank_you_helper: 'Podziękuj komuś, kto ci ostatnio pomógł/pomogła',
try_good_morning_local_language: 'Spróbuj powiedzieć dzień dobry w nowym języku',
find_nearest_park: 'Znajdź najbliższy park lub zielone miejsce blisko domu',
notice_new_neighborhood_sound: 'Zauważ jeden dźwięk w nowej okolicy, którego nie było w starej',
find_busiest_market_day: 'Dowiedz się, w który dzień lokalny sklep lub targ jest najbardziej ruchliwy',
listen_song_reminds_home: 'Posłuchaj piosenki, która przypomina ci dom',
write_favorite_holiday_tradition: 'Zapisz swoją ulubioną tradycję świąteczną z domu',
describe_old_bedroom_memory: 'Opisz z pamięci swój stary pokój',
recall_joke_from_home: 'Przypomnij sobie żart, który ktoś z domu ci opowiadał',
name_one_thing_proud_week: 'Wskaż jedną rzecz, z której jesteś dumny/a w tym tygodniu',
write_something_surprised_today: 'Zapisz jedną rzecz, która cię dziś zaskoczyła',
one_word_today_felt: 'Wymyśl jedno słowo, które opisuje dzisiejszy dzień',
notice_small_win_today: 'Zauważ jedną małą rzecz, która poszła lepiej, niż się spodziewałeś/aś',
find_nearest_library: 'Dowiedz się, gdzie jest biblioteka blisko domu',
look_up_local_holiday: 'Sprawdź jedno święto obchodzone tutaj, którego wcześniej nie znałeś/aś',
find_new_walk_route: 'Znajdź nową trasę na spacer lub rower w tym tygodniu',
discover_recess_game: 'Dowiedz się, w co bawią się dzieci na przerwie'

// add to missionTitleOverridesEn
invite_lunch_sit_together: 'Invite someone to sit with you at lunch',
learn_classmate_name: "Learn one classmate's name you didn't know",
share_something_funny: 'Share something funny with someone',
say_thank_you_helper: 'Say thank you to someone who helped you this week',
try_good_morning_local_language: 'Try saying good morning in the new language',
find_nearest_park: 'Find the nearest park or green space near your home',
notice_new_neighborhood_sound: "Notice one sound your new neighborhood makes that your old one didn't",
find_busiest_market_day: 'Find out what day the local market or store is busiest',
listen_song_reminds_home: 'Listen to a song that reminds you of home',
write_favorite_holiday_tradition: 'Write down your favorite holiday tradition from home',
describe_old_bedroom_memory: 'Describe your old bedroom from memory',
recall_joke_from_home: 'Think of a joke someone back home used to tell you',
name_one_thing_proud_week: "Name one thing you're proud of from this week",
write_something_surprised_today: 'Write down one thing that surprised you today',
one_word_today_felt: 'Think of one word that describes how today felt',
notice_small_win_today: 'Notice one small thing that went better than you expected',
find_nearest_library: 'Find out where the library is near your home',
look_up_local_holiday: "Look up one holiday celebrated here that's new to you",
find_new_walk_route: 'Find one new route to walk or bike this week',
discover_recess_game: 'Discover what game kids play at recess here'
```

---

## 10. Acceptance Checklist

- [ ] `MissionRepository` no longer caps active missions at 20; all ~45 active missions
      are queryable.
- [ ] `MissionResponse` includes `category`; existing missions (from `V4`) return their
      already-stored category values with no data migration needed.
- [ ] Missions tab shows a horizontal, scrollable chip row: All, People, New World, Old
      World, Feelings, Explore — each with a small neutral-colored icon, no chip visually
      ranked above another.
- [ ] Tapping a chip filters the list instantly, client-side, without re-fetching or
      re-ordering; the per-child "unseen missions first" ordering is preserved within the
      filtered set.
- [ ] Selecting a category with zero current matches shows `missionEmptyCategory` copy,
      not the generic "no missions at all" copy.
- [ ] If the currently selected category's missions all get deactivated server-side, the
      filter falls back to `all` on next fetch instead of showing a permanently empty tab.
- [ ] Every chip meets the 44×44px comfortable-tap-target guidance in practice (padding +
      min-height combined), verified on a real phone width (360px).
- [ ] `V8__missions_expansion.sql` adds exactly 20 new missions, 4 per category, each
      completable in 1–5 minutes, none phrased as harder/easier than another.
- [ ] All 20 new missions have entries in both `missionTitleOverridesEn` and
      `missionTitleOverridesPl`, matching the existing gender-neutral slash convention
      (e.g. `dumny/a`, `znałeś/aś`) already used elsewhere in the app.
- [ ] No new mission copy uses "easy"/"hard" language, hearts, padlocks, or human-figure
      iconography, per the established CapyBee visual/tone rules.
- [ ] `Not today` / skip flow (from `08-missions-redesign-instruction.md`) continues to
      work unchanged for all missions, old and new, within any selected category.
