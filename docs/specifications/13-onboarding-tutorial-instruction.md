# 13 — First-Login Onboarding Tutorial (Inline, CapyBee-Narrated)

## Purpose

Implementation-ready instructions for GitHub Copilot to add a first-login onboarding
tutorial to `AuthenticatedHome.tsx`. The child never leaves the real home screen — the
existing CapyBee greeting bubble narrates a short guided tour, and a spotlight overlay
highlights whatever it is currently talking about. This is the "inline" onboarding
pattern (as opposed to a blocking full-screen modal tour): the check-in form, hive, and
nav stay live and tappable the entire time.

Applies to: `SCR-03` (Authenticated Home), first visit only. Covers all four bottom-nav
tabs (`home`/Start, `missions`/Misje, `friendships`/Relacje, `memories`/Wspomnienia) in
full, plus a short pointer-only step for the Profile button in the top bar.

Ground truth reviewed: `AuthenticatedHome.tsx` (`TabKey` ~L86, `activeTab` state ~L467,
`copy` object ~L250–460, header/profile button ~L1234–1273, home tab content
~L1276–1370, `bottomNavItems` ~L540–544, bottom-nav render ~L1867–1883), `capybee.tsx`
(`CapyBeeAvatar`, `CapyBeeBubble`), `styles.css` (`:root` tokens ~L15–20,
`.capybee-bubble` ~L92, `.bottom-nav` z-index ~L1366), `02-data-model.md` (child
profile schema), `03-api-contract.md` (`PATCH /api/child-profile`),
`ChildProfileService.java`, `FamilyProfile.java`, `UpdateChildProfileRequest.java`,
`ChildProfileResponse.java`, `V5__mission_child_state.sql` (latest migration).

---

## 1. Trigger and persistence

### 1.1 Why a backend flag, not `localStorage`

The tutorial must not re-appear if the child logs in from a different device, and must
not depend on the browser not clearing storage. Persist it the same way every other
profile preference already is — as a field on `FamilyProfile`, via the existing
`PATCH /api/child-profile` endpoint.

### 1.2 Data model addition

New migration `app/server/src/main/resources/db/migration/V6__onboarding_flag.sql`:

```sql
ALTER TABLE family_profiles
    ADD COLUMN has_seen_onboarding boolean NOT NULL DEFAULT false;
```

`FamilyProfile.java` — add field next to `active`:

```java
@Column(name = "has_seen_onboarding", nullable = false)
private boolean hasSeenOnboarding = false;

public boolean isHasSeenOnboarding() {
    return hasSeenOnboarding;
}

public void setHasSeenOnboarding(boolean hasSeenOnboarding) {
    this.hasSeenOnboarding = hasSeenOnboarding;
}
```

`ChildProfileResponse.java` — add field to the record:

```java
public record ChildProfileResponse(
        UUID id,
        String nickname,
        Integer birthYear,
        String preferredLocale,
        String avatarSeed,
        boolean active,
        boolean hasSeenOnboarding,
        Instant createdAt,
        Instant updatedAt) {
}
```

`UpdateChildProfileRequest.java` — add field to the record:

```java
public record UpdateChildProfileRequest(
        String nickname,
        Integer birthYear,
        String preferredLocale,
        String avatarSeed,
        Boolean active,
        Boolean hasSeenOnboarding) {
}
```

`ChildProfileService.java` — in `updateProfile(...)`, add alongside the existing
`if (request.active() != null)` block:

```java
if (request.hasSeenOnboarding() != null) {
    profile.setHasSeenOnboarding(request.hasSeenOnboarding());
}
```

And in `toResponse(...)`, add the field to the constructor call in the matching
position:

```java
private ChildProfileResponse toResponse(FamilyProfile profile) {
    return new ChildProfileResponse(
            profile.getId(),
            profile.getNickname(),
            profile.getBirthYear(),
            profile.getPreferredLocale(),
            profile.getAvatarSeed(),
            profile.isActive(),
            profile.isHasSeenOnboarding(),
            profile.getCreatedAt(),
            profile.getUpdatedAt());
}
```

No controller changes needed — `ApiController.java`'s existing
`PATCH /api/child-profile` mapping passes the whole request body through already.

### 1.3 Frontend: `ChildProfile` type and `updateProfile`

`AuthenticatedHome.tsx` ~L34 — add the field:

```ts
interface ChildProfile {
  id: string;
  nickname: string;
  birthYear?: number;
  preferredLocale: 'en' | 'pl';
  avatarSeed?: string;
  active: boolean;
  hasSeenOnboarding: boolean;
  createdAt: string;
  updatedAt: string;
}
```

`updateProfile` ~L860 — add the field to the PATCH body:

```ts
const updated = await request<ChildProfile>('/api/child-profile', {
  method: 'PATCH',
  body: JSON.stringify({
    nickname: changes.nickname,
    birthYear: changes.birthYear,
    preferredLocale: changes.preferredLocale,
    avatarSeed: changes.avatarSeed,
    active: changes.active,
    hasSeenOnboarding: changes.hasSeenOnboarding
  })
});
```

### 1.4 When the tutorial mounts

In `AuthenticatedHome.tsx`, once `profile` has loaded:

```ts
const [tutorialActive, setTutorialActive] = useState(false);

useEffect(() => {
  if (profile && !profile.hasSeenOnboarding) {
    setTutorialActive(true);
  }
}, [profile]);

const finishTutorial = () => {
  setTutorialActive(false);
  if (profile) {
    updateProfile({ hasSeenOnboarding: true });
  }
};
```

`finishTutorial` fires both when the child completes every step and when they skip —
both are "I don't need to see this again," not just completion. There is no separate
skip-vs-complete state to persist.

---

## 2. Step definitions and full bilingual copy

Eight steps, in order. Steps 2–5 (Start, Misje, Relacje, Wspomnienia) get the full
explanation the child needs to understand what that tab is for. Step 6 (Profile) is
intentionally short — a pointer and one line, not a full walkthrough, per product
scope (profile is a parent-managed settings area, not part of the child's daily loop).

Add to `copy.en` and `copy.pl` in `AuthenticatedHome.tsx` (~L250 and ~L333):

```ts
// en
onboardingWelcomeTitle: "Hey, I'm CapyBee.",
onboardingWelcomeBody: "I'll show you around — quick, promise.",
onboardingStartTitle: 'This is where you check in.',
onboardingStartBody: "Pick heavy, okay, or good — whatever's true today. No wrong answer, and I'll always react to what you tell me.",
onboardingHiveTitle: 'This is your hive.',
onboardingHiveBody: 'Every check-in, mission, and memory adds one cell. It starts empty on purpose — we fill it together, over time.',
onboardingMissionsTitle: 'Missions live here.',
onboardingMissionsBody: "I'll suggest one small real-world thing to try. Do it, or tap Not today — skipped missions just wait at the bottom, they never disappear.",
onboardingFriendshipsTitle: 'Friendships are private, just for you.',
onboardingFriendshipsBody: 'Noticed someone? Said hi? Log it here. No one else ever sees this list — not even other kids using CapyBee.',
onboardingMemoriesTitle: 'Both your worlds live here.',
onboardingMemoriesBody: "Old World keeps what you miss — golden, safe, always yours. New World grows as you build your life here. Neither one replaces the other.",
onboardingProfileTitle: 'One more thing.',
onboardingProfileBody: 'This is your profile, up here. Language and a few settings live behind it — you can peek anytime.',
onboardingDoneTitle: "That's the hive.",
onboardingDoneBody: "Come back anytime — I'm here.",
onboardingNext: 'Next',
onboardingSkip: 'Skip',
onboardingStart: "Let's go",
```

```ts
// pl
onboardingWelcomeTitle: 'Hej, jestem CapyBee.',
onboardingWelcomeBody: 'Pokażę ci, co tu jest — szybko, obiecuję.',
onboardingStartTitle: 'Tu się meldujesz.',
onboardingStartBody: 'Wybierz ciężko, okej albo dobrze — co jest prawdą dziś. Nie ma złej odpowiedzi, a ja zawsze zareaguję na to, co mi powiesz.',
onboardingHiveTitle: 'To twój ul.',
onboardingHiveBody: 'Każde zameldowanie, misja i wspomnienie dodają jedną komórkę. Zaczyna się pusty specjalnie — zapełniamy go razem, po trochu.',
onboardingMissionsTitle: 'Tu mieszkają misje.',
onboardingMissionsBody: 'Zaproponuję ci jedną małą, prawdziwą rzecz do zrobienia. Zrób ją albo kliknij Nie dziś — pominięte misje po prostu czekają na dole, nigdy nie znikają.',
onboardingFriendshipsTitle: 'Relacje są prywatne, tylko dla ciebie.',
onboardingFriendshipsBody: 'Zauważyłeś kogoś? Powiedziałeś cześć? Zapisz to tutaj. Nikt inny tego nie widzi — nawet inne dzieci używające CapyBee.',
onboardingMemoriesTitle: 'Oba twoje światy są tutaj.',
onboardingMemoriesBody: 'Stary Świat trzyma to, za czym tęsknisz — złoty, bezpieczny, zawsze twój. Nowy Świat rośnie, gdy budujesz tu swoje życie. Żaden nie zastępuje drugiego.',
onboardingProfileTitle: 'I jeszcze jedno.',
onboardingProfileBody: 'To twój profil, tutaj na górze. Język i kilka ustawień są za nim — możesz tam zajrzeć, kiedy chcesz.',
onboardingDoneTitle: 'To twój ul.',
onboardingDoneBody: 'Wracaj tu, kiedy chcesz — jestem tu.',
onboardingNext: 'Dalej',
onboardingSkip: 'Pomiń',
onboardingStart: 'Zaczynamy',
```

### 2.1 Step-to-target map

| Step | Title key | Body key | Target | Spotlight type |
|---|---|---|---|---|
| 0 | `onboardingWelcomeTitle` | `onboardingWelcomeBody` | CapyBee bubble itself | none (bubble is the anchor) |
| 1 | `onboardingStartTitle` | `onboardingStartBody` | mood-selector panel | content hole |
| 2 | `onboardingHiveTitle` | `onboardingHiveBody` | honeycomb-card panel | content hole |
| 3 | `onboardingMissionsTitle` | `onboardingMissionsBody` | Misje nav item | nav glow |
| 4 | `onboardingFriendshipsTitle` | `onboardingFriendshipsBody` | Relacje nav item | nav glow |
| 5 | `onboardingMemoriesTitle` | `onboardingMemoriesBody` | Wspomnienia nav item | nav glow |
| 6 | `onboardingProfileTitle` | `onboardingProfileBody` | profile-nav-button (top bar) | nav glow (top bar) |
| 7 | `onboardingDoneTitle` | `onboardingDoneBody` | none | fades out, tutorial ends |

---

## 3. Component: `OnboardingTutorial.tsx`

New file: `app/ui/src/components/OnboardingTutorial.tsx`.

```tsx
import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CapyBeeAvatar, capyBeeAvatar } from '../capybee';

export interface OnboardingTargets {
  moodSection: React.RefObject<HTMLElement>;
  hiveSection: React.RefObject<HTMLElement>;
  missionsNav: React.RefObject<HTMLElement>;
  friendshipsNav: React.RefObject<HTMLElement>;
  memoriesNav: React.RefObject<HTMLElement>;
  profileButton: React.RefObject<HTMLElement>;
}

interface Step {
  titleKey: string;
  bodyKey: string;
  target: keyof OnboardingTargets | null;
  spotlight: 'content' | 'nav' | 'none';
}

const STEPS: Step[] = [
  { titleKey: 'onboardingWelcomeTitle', bodyKey: 'onboardingWelcomeBody', target: null, spotlight: 'none' },
  { titleKey: 'onboardingStartTitle', bodyKey: 'onboardingStartBody', target: 'moodSection', spotlight: 'content' },
  { titleKey: 'onboardingHiveTitle', bodyKey: 'onboardingHiveBody', target: 'hiveSection', spotlight: 'content' },
  { titleKey: 'onboardingMissionsTitle', bodyKey: 'onboardingMissionsBody', target: 'missionsNav', spotlight: 'nav' },
  { titleKey: 'onboardingFriendshipsTitle', bodyKey: 'onboardingFriendshipsBody', target: 'friendshipsNav', spotlight: 'nav' },
  { titleKey: 'onboardingMemoriesTitle', bodyKey: 'onboardingMemoriesBody', target: 'memoriesNav', spotlight: 'nav' },
  { titleKey: 'onboardingProfileTitle', bodyKey: 'onboardingProfileBody', target: 'profileButton', spotlight: 'nav' },
  { titleKey: 'onboardingDoneTitle', bodyKey: 'onboardingDoneBody', target: null, spotlight: 'none' }
];

export function OnboardingTutorial({
  copy,
  targets,
  onFinish
}: {
  copy: Record<string, string>;
  targets: OnboardingTargets;
  onFinish: () => void;
}) {
  const [step, setStep] = useState(0);
  const [hole, setHole] = useState<{ top: number; left: number; width: number; height: number } | null>(null);
  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;

  useEffect(() => {
    if (current.spotlight !== 'content' || !current.target) {
      setHole(null);
      return;
    }
    const el = targets[current.target].current;
    if (!el) {
      setHole(null);
      return;
    }
    el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    const measure = () => {
      const rect = el.getBoundingClientRect();
      setHole({ top: rect.top - 8, left: rect.left - 8, width: rect.width + 16, height: rect.height + 16 });
    };
    const timeout = setTimeout(measure, 350);
    window.addEventListener('resize', measure);
    return () => {
      clearTimeout(timeout);
      window.removeEventListener('resize', measure);
    };
  }, [step, current, targets]);

  useEffect(() => {
    const navKeys: Array<keyof OnboardingTargets> = [
      'missionsNav',
      'friendshipsNav',
      'memoriesNav',
      'profileButton'
    ];
    navKeys.forEach((key) => {
      const el = targets[key].current;
      if (!el) return;
      const isActiveTarget = current.spotlight === 'nav' && current.target === key;
      el.classList.toggle('onboarding-glow', isActiveTarget);
    });
    return () => {
      navKeys.forEach((key) => targets[key].current?.classList.remove('onboarding-glow'));
    };
  }, [step, current, targets]);

  const advance = () => {
    if (isLast) {
      onFinish();
      return;
    }
    setStep((s) => s + 1);
  };

  return (
    <>
      <AnimatePresence>
        {current.spotlight === 'content' && hole ? (
          <motion.div
            className="onboarding-mask"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <motion.div
              className="onboarding-hole"
              animate={{ top: hole.top, left: hole.left, width: hole.width, height: hole.height }}
              transition={{ type: 'spring', stiffness: 260, damping: 26 }}
            />
          </motion.div>
        ) : null}
        {current.spotlight === 'nav' ? (
          <motion.div
            className="onboarding-nav-mask"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          />
        ) : null}
      </AnimatePresence>

      <div className="onboarding-bubble-anchor" role="dialog" aria-live="polite" aria-label={copy[current.titleKey]}>
        <CapyBeeAvatar src={capyBeeAvatar.waving} size={64} />
        <div className="capybee-bubble onboarding-bubble">
          <strong>{copy[current.titleKey]}</strong>
          <p>{copy[current.bodyKey]}</p>
          <div className="onboarding-controls">
            <div className="onboarding-hexdots" aria-hidden="true">
              {STEPS.map((_, i) => (
                <span key={i} className={i <= step ? 'onboarding-hexdot on' : 'onboarding-hexdot'} />
              ))}
            </div>
            <div className="onboarding-buttons">
              {!isLast ? (
                <button type="button" className="onboarding-skip" onClick={onFinish}>
                  {copy.onboardingSkip}
                </button>
              ) : null}
              <button type="button" className="primary-button onboarding-next" onClick={advance}>
                {isLast ? copy.onboardingStart : copy.onboardingNext}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
```

Notes on this implementation:

- The bubble reuses the existing `.capybee-bubble` class so it visually matches the
  home screen's normal CapyBee speech bubble exactly — the tutorial should not look
  like a different UI system bolted on.
- The bubble itself does not move between steps; only the spotlight moves. This keeps
  it readable — the child always knows where to look for the next instruction.
- `onboarding-hexdot` progress markers deliberately reuse the hexagon shape (see
  §4 CSS) so the tutorial's own chrome echoes the hive metaphor it's explaining.
- Esc key handling and focus trapping are listed as a follow-up in §6, not blocking
  for first implementation, since this is a low-risk dismissible overlay rather than a
  destructive action.

---

## 4. Wiring into `AuthenticatedHome.tsx`

### 4.1 Refs

Near the other `useState`/`useRef` declarations (~L467):

```ts
const moodSectionRef = useRef<HTMLElement>(null);
const hiveSectionRef = useRef<HTMLElement>(null);
const missionsNavRef = useRef<HTMLElement>(null);
const friendshipsNavRef = useRef<HTMLElement>(null);
const memoriesNavRef = useRef<HTMLElement>(null);
const profileButtonRef = useRef<HTMLElement>(null);
```

### 4.2 Attaching refs to real elements

Mood/check-in panel, ~L1283:

```tsx
<section className="panel" ref={moodSectionRef}>
  <h2>{text.homeTitle}</h2>
  ...
```

Honeycomb panel, ~L1333:

```tsx
<section className="panel" ref={hiveSectionRef}>
  <h3>{text.honeycombProgress}</h3>
  ...
```

Profile button, ~L1243 — add the ref to the existing button:

```tsx
<button
  ref={profileButtonRef as React.RefObject<HTMLButtonElement>}
  type="button"
  className={activeTab === 'profile' ? 'profile-nav-button active' : 'profile-nav-button'}
  onClick={() => setActiveTab('profile')}
  aria-label={text.profile}
  aria-current={activeTab === 'profile' ? 'page' : undefined}
>
  <ProfileIcon active={activeTab === 'profile'} />
</button>
```

Bottom nav items, ~L1868 — the nav renders from a `bottomNavItems.map(...)`, so attach
a keyed ref callback rather than three separate hardcoded buttons:

```tsx
const navRefByKey: Partial<Record<TabKey, React.RefObject<HTMLElement>>> = {
  missions: missionsNavRef,
  friendships: friendshipsNavRef,
  memories: memoriesNavRef
};

<nav className="bottom-nav" aria-label={text.navLabel}>
  {bottomNavItems.map(({ key, label, Icon }) => {
    const isActive = activeTab === key;
    return (
      <button
        key={key}
        ref={navRefByKey[key] as React.RefObject<HTMLButtonElement> | undefined}
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

### 4.3 Rendering the tutorial

Right before the closing `</main>` (after the bottom nav, so it stacks above
everything in DOM order), only while `activeTab === 'home'` so it never fires mid-way
through another tab if the child somehow navigates before it mounts:

```tsx
{tutorialActive && activeTab === 'home' ? (
  <OnboardingTutorial
    copy={text}
    targets={{
      moodSection: moodSectionRef,
      hiveSection: hiveSectionRef,
      missionsNav: missionsNavRef,
      friendshipsNav: friendshipsNavRef,
      memoriesNav: memoriesNavRef,
      profileButton: profileButtonRef
    }}
    onFinish={finishTutorial}
  />
) : null}
```

Import at the top of `AuthenticatedHome.tsx`:

```tsx
import { OnboardingTutorial } from './components/OnboardingTutorial';
```

---

## 5. CSS additions

Add to `styles.css`, reusing the existing token set (`--ink`, `--muted`, `--accent`,
`--accent-strong`, `--panel-border`) rather than introducing new colors:

```css
.onboarding-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  pointer-events: none;
}

.onboarding-hole {
  position: fixed;
  border-radius: 18px;
  box-shadow: 0 0 0 2000px rgba(46, 36, 19, 0.58);
  border: 2px solid var(--accent);
  pointer-events: none;
}

.onboarding-nav-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 78px; /* stays above bottom-nav, which is z-index: 50 and 10px inset */
  background: rgba(46, 36, 19, 0.5);
  z-index: 55;
  pointer-events: none;
}

.onboarding-glow {
  position: relative;
  z-index: 56;
  transform: translateY(-3px);
  transition: transform 0.3s ease;
}

.onboarding-glow::before {
  content: '';
  position: absolute;
  inset: -6px -2px;
  border-radius: 14px;
  box-shadow: 0 0 0 3px var(--accent), 0 6px 18px rgba(242, 178, 51, 0.5);
  animation: onboarding-pulse 1.1s ease-in-out infinite;
  pointer-events: none;
}

@keyframes onboarding-pulse {
  0%, 100% { opacity: 0.55; }
  50% { opacity: 1; }
}

.onboarding-bubble-anchor {
  position: fixed;
  left: 50%;
  bottom: 96px;
  transform: translateX(-50%);
  z-index: 130;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: min(92vw, 340px);
}

.onboarding-bubble {
  width: 100%;
}

.onboarding-bubble p {
  margin: 4px 0 0;
  font-weight: 500;
}

.onboarding-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.onboarding-hexdots {
  display: flex;
  gap: 5px;
}

.onboarding-hexdot {
  width: 11px;
  height: 13px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  background: var(--panel-border);
}

.onboarding-hexdot.on {
  background: linear-gradient(160deg, var(--accent), var(--accent-strong));
}

.onboarding-buttons {
  display: flex;
  gap: 8px;
}

.onboarding-skip {
  border: none;
  background: transparent;
  color: var(--muted);
  font-weight: 700;
  font-size: 0.8rem;
  cursor: pointer;
  padding: 6px 8px;
}

.onboarding-next {
  padding: 8px 16px;
  font-size: 0.85rem;
}

@media (max-width: 420px) {
  .onboarding-bubble-anchor {
    bottom: 88px;
    width: 94vw;
  }
}
```

`.onboarding-nav-mask` bottom offset (`78px`) and z-index values are set to sit below
`.bottom-nav` (`z-index: 50` per `12-navigation-redesign-instruction.md` §2.3) plus the
`10px` bottom inset that nav already has — confirm against the shipped nav CSS at
implementation time in case those values drift.

---

## 6. Accessibility and edge cases

- `role="dialog"` and `aria-live="polite"` on the bubble container so screen readers
  announce each step as it changes, consistent with the toast/undo patterns already
  used elsewhere (`skip-undo-toast` `aria-live="polite"`).
- The tutorial does not trap focus or block interaction with the real UI underneath —
  this is intentional per Option B's design (the child can, in principle, tap the real
  mood picker mid-tour). If user testing shows this causes confusion, a follow-up pass
  can add `pointer-events: none` to `#content` while `tutorialActive` is true, except
  for the currently-spotlighted element.
- If `profile` fails to load or the child has no profile yet (first-time setup flow,
  ~L1190), the tutorial must not mount — `tutorialActive` only ever becomes `true`
  once `profile` exists, so this is already handled by the `useEffect` condition in
  §1.4.
- If a child dismisses the tutorial via `Skip` at any step, `finishTutorial` still
  fires — there is intentionally no partial-progress state to resume later. This
  matches CapyBee's "never pushes" tone; a half-finished tutorial reappearing later
  would feel like nagging.

---

## 7. Out of scope for this pass

- Embedded miniature animated demos of app mechanics (e.g. a fake mission card
  animating its own checkmark inside the tutorial bubble) — the tutorial spotlights
  and narrates the real, live elements instead of simulating them.
- Onboarding for `SCR-04`/`SCR-05` (profile setup/settings) — out of scope per the
  original request; Profile gets a one-line pointer only (step 6), not a full
  explanation.
- Re-triggering the tutorial on demand (e.g. a "Show me around again" button in
  Profile settings) — worth considering later, not required for first release.
- Localizing beyond `en`/`pl` — matches existing app scope.

---

## Traceability

- Supports US-003 (child lands in a safe, understandable home screen), US-017 (gentle,
  never-lecturing tone — copy in §2 follows the same voice as existing `reactionHeavy`/
  `missionSkipped` phrases), US-016 (bilingual, both locales fully written before
  implementation, not left as a TODO).
- Explains the honeycomb metaphor referenced in US-007/US-008 at the moment the child
  first sees it, rather than assuming it's self-explanatory.
- Complements `12-navigation-redesign-instruction.md`'s 4-tab + top-bar-profile layout
  by teaching that exact layout, in that exact order, the first time a child sees it.
- No changes to `04-screen-descriptions.md` screen inventory — this spec only adds a
  first-visit overlay behavior to the existing `SCR-03` shell.
