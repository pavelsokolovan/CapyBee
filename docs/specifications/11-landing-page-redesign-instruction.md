# 11 — Landing Page Redesign: Parent-Facing Trust & Brand Alignment

## Purpose

Implementation instructions for GitHub Copilot to redesign `SCR-01 Landing` in `App.tsx`
(the unauthenticated view returned before the `user?.authenticated` check).

Applies to: `SCR-01 - Landing` (`/`, unauthenticated state).
Related docs: `04-screen-descriptions.md`, `CapyBee_concept.md` (sections 4, 7, 10),
`06-capybee-phrases-instruction.md`.

Ground truth reviewed: `App.tsx`, `styles.css` (`.hero`, `.hero-copy`, `.hero-card`,
`.feature-card`, `.roadmap`), `capybee.tsx`, and the `copy.en` / `copy.pl` object pattern
established in `AuthenticatedHome.tsx`.

---

## 1. Problem Being Fixed

Current state (`App.tsx`, lines ~7–31 and ~88–173):

- The eyebrow badge reads **"CapyBee concept scaffold"** — internal dev language, shown
  to parents.
- The hero card's `stats` array shows **tech stack info** (`Spring Boot + React`,
  `Google OAuth`, `PostgreSQL`, `Fly.io`) — irrelevant and mildly alarming to a parent,
  who has no reason to care what the backend framework is.
- The `highlights` array under "What the scaffold already supports" describes
  **developer milestones** ("Google sign-in", "Child profile setup"), not the product
  value a parent needs to evaluate.
- Nothing on the page addresses **privacy/safety**, even though `CapyBee_concept.md`
  section 10 treats this as make-or-break for a parent deciding whether to trust an app
  with their child's emotional data. A parent googling "app for my kid who just moved"
  lands here and gets no signal that this is private, ad-free, and non-social.
- No **honeycomb visual motif** anywhere — the core brand metaphor is absent from the
  first thing anyone sees.
- No **tagline** ("Razem budujemy nowy ul" / "Together we build a new hive").
- The page is **hardcoded English only**, even though `AuthenticatedHome.tsx` already
  establishes a `copy.en` / `copy.pl` object pattern used throughout the rest of the app.

Target state:

- Hero leads with the real tagline and CapyBee's actual value proposition for a family
  going through a relocation, not a scaffold status report.
- A trust strip directly answers the parent's unspoken safety questions, replacing the
  tech-stack chips.
- Feature cards describe the four real product pillars (check-ins, two worlds, missions,
  friendship tracker), not implementation tasks.
- A subtle honeycomb pattern ties the landing page visually to the in-app hive.
- Full bilingual support via the existing `copy` object convention.

Note on register: unlike the child-facing "ty" tone used inside the app
(`06-capybee-phrases-instruction.md`), `SCR-01`'s primary user is the **parent**
(per `04-screen-descriptions.md`). Landing copy should stay warm but can use a slightly
more adult, reassuring register — it does not need to match the child-voice phrase pools.

---

## 2. Copy — Bilingual Content

Add a `landingCopy` object near the top of `App.tsx`, following the same shape as
`copy` in `AuthenticatedHome.tsx`:

```jsx
const landingCopy = {
  en: {
    tagline: 'Together we build a new hive',
    heroTitle: 'Help your child feel at home again — one small step at a time.',
    heroLead:
      "CapyBee is a gentle companion app for children who've moved somewhere new. " +
      'Daily check-ins, tiny real-world missions, and a private space to hold both ' +
      "their old life and their new one — without ever rushing them to feel okay.",
    ctaPrimary: 'Continue with Google',
    ctaSecondary: 'See how CapyBee works',
    trustBadgeHeading: 'Built for family trust',
    trust: [
      { title: 'Private family space', body: 'Only your family can see your child\u2019s check-ins, missions, and memories.' },
      { title: 'No ads, no social feed', body: 'Nothing to scroll, nothing to sell, no strangers to talk to.' },
      { title: 'Parent-managed account', body: 'You sign in and set things up. Your child never needs their own credentials.' },
      { title: 'Real names optional', body: 'Your child can use a nickname everywhere in the app.' }
    ],
    featuresHeading: 'How CapyBee helps',
    featuresEyebrow: 'What your child actually does',
    features: [
      {
        title: 'A 2-minute daily check-in',
        body: "Not a questionnaire — just \u201cheavy, okay, or good?\u201d CapyBee responds with warmth, never a lecture."
      },
      {
        title: 'Two worlds, side by side',
        body: 'Old World memories and New World wins live together in one hive. Nothing gets deleted, nothing has to be replaced.'
      },
      {
        title: 'Small, real-world missions',
        body: 'Gentle nudges like saying hi to one person — always optional, never punished for skipping.'
      },
      {
        title: 'A private friendship tracker',
        body: 'A quiet place to notice small social steps. No public profiles, no strangers, ever.'
      }
    ],
    afterHeading: 'What happens after you sign in',
    afterEyebrow: 'First session',
    after: [
      {
        title: 'Profile setup',
        body: 'You create one child profile with a nickname and a language preference — that\u2019s it.'
      },
      {
        title: 'Daily flow',
        body: 'Your child checks in, tries a small mission, and quietly tracks social progress — five minutes, max.'
      },
      {
        title: 'Two worlds',
        body: 'Old World and New World memories are stored side by side, never replacing each other.'
      }
    ]
  },
  pl: {
    tagline: 'Razem budujemy nowy ul',
    heroTitle: 'Pomóż dziecku poczuć się znów jak w domu — krok po kroku.',
    heroLead:
      'CapyBee to delikatna aplikacja-towarzysz dla dzieci, które przeprowadziły się ' +
      'w nowe miejsce. Codzienne pytania o samopoczucie, małe misje w prawdziwym ' +
      'świecie i prywatna przestrzeń na wspomnienia ze starego i nowego domu — bez ' +
      'presji, by od razu poczuć się dobrze.',
    ctaPrimary: 'Kontynuuj z Google',
    ctaSecondary: 'Zobacz, jak działa CapyBee',
    trustBadgeHeading: 'Zbudowane z myślą o zaufaniu rodziny',
    trust: [
      { title: 'Prywatna przestrzeń rodzinna', body: 'Tylko wasza rodzina widzi nastroje, misje i wspomnienia dziecka.' },
      { title: 'Bez reklam, bez social mediów', body: 'Nic do przewijania, nic do sprzedania, żadnych obcych do rozmowy.' },
      { title: 'Konto zarządzane przez rodzica', body: 'To ty logujesz się i konfigurujesz konto. Dziecko nie potrzebuje własnych danych do logowania.' },
      { title: 'Prawdziwe imię jest opcjonalne', body: 'Dziecko może używać pseudonimu wszędzie w aplikacji.' }
    ],
    featuresHeading: 'Jak CapyBee pomaga',
    featuresEyebrow: 'Co robi twoje dziecko',
    features: [
      {
        title: '2-minutowe codzienne pytanie o samopoczucie',
        body: 'Nie ankieta — po prostu \u201cciężko, okej czy dobrze?\u201d. CapyBee reaguje ciepło, nigdy nie poucza.'
      },
      {
        title: 'Dwa światy, obok siebie',
        body: 'Wspomnienia ze Starego Świata i sukcesy z Nowego Świata żyją razem w jednym ulu. Nic nie znika, nic nie musi być zastąpione.'
      },
      {
        title: 'Małe misje w realnym świecie',
        body: 'Delikatne zachęty, jak powiedzenie „cześć” jednej osobie — zawsze opcjonalne, nigdy karane za pominięcie.'
      },
      {
        title: 'Prywatny tracker znajomości',
        body: 'Ciche miejsce na zauważanie małych kroków społecznych. Bez publicznych profili, bez obcych — nigdy.'
      }
    ],
    afterHeading: 'Co się dzieje po zalogowaniu',
    afterEyebrow: 'Pierwsza sesja',
    after: [
      {
        title: 'Konfiguracja profilu',
        body: 'Tworzysz jeden profil dziecka z pseudonimem i preferencją językową — to wszystko.'
      },
      {
        title: 'Codzienny rytm',
        body: 'Dziecko zgłasza samopoczucie, próbuje małej misji i po cichu śledzi postępy społeczne — maksymalnie pięć minut.'
      },
      {
        title: 'Dwa światy',
        body: 'Wspomnienia ze Starego i Nowego Świata są przechowywane obok siebie, nigdy się nie zastępują.'
      }
    ]
  }
};
```

Remove the existing `highlights` and `stats` arrays entirely — they are fully replaced
by `landingCopy.features` and `landingCopy.trust`.

---

## 3. Hero Section

Replace the `<section className="hero">` block:

```jsx
const t = landingCopy[locale];

<section className="hero">
  <div className="hero-copy">
    <CapyBeeAvatar src={capyBeeAvatar.waving} size={180} alt="CapyBee" className="hero-capybee" />
    <span className="eyebrow">{t.tagline}</span>
    <h1>{t.heroTitle}</h1>
    <p className="lead">{t.heroLead}</p>
    <div className="hero-actions">
      <a className="primary-button" href="/oauth2/authorization/google">
        {t.ctaPrimary}
      </a>
      <a className="secondary-button" href="#overview">
        {t.ctaSecondary}
      </a>
    </div>
  </div>

  <motion.aside
    className="hero-card"
    initial={{ opacity: 0, y: 20 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.6, ease: 'easeOut' }}
  >
    <div className="hero-card-badge">{t.trustBadgeHeading}</div>
    <div className="hero-card-grid trust-grid">
      {t.trust.map((item) => (
        <div key={item.title} className="stat-chip trust-chip">
          <strong>{item.title}</strong>
          <span>{item.body}</span>
        </div>
      ))}
    </div>
  </motion.aside>
</section>
```

Notes:
- `hero-card` now carries the **trust strip** instead of tech stats — this is the single
  highest-value change, since it's the first thing a scrolling parent sees beside the
  hero copy.
- `.stat-chip` already exists in `styles.css`; add a `.trust-chip` modifier (Section 5)
  since trust copy is longer than the old `label`/`value` stat pairs and needs two lines,
  not a small-caps label + bold value.
- Keep `hero-card-grid` at `repeat(2, minmax(0, 1fr))` — 4 trust items in a 2×2 grid reads
  cleanly at the existing card width.

---

## 4. Features Section (replaces "What the scaffold already supports")

```jsx
<section className="overview" id="overview">
  <div className="section-heading">
    <span>{t.featuresEyebrow}</span>
    <h2>{t.featuresHeading}</h2>
  </div>
  <div className="feature-grid">
    {t.features.map((item, index) => (
      <motion.article
        key={item.title}
        className="feature-card"
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.45, delay: index * 0.08 }}
      >
        <span className="feature-index">0{index + 1}</span>
        <h3>{item.title}</h3>
        <p>{item.body}</p>
      </motion.article>
    ))}
  </div>
</section>
```

Structurally identical to the current implementation — only the data source and field
names change (`highlights` → `t.features`, `description` → `body`).

---

## 5. "What Happens After Sign-In" Section

```jsx
<section className="roadmap">
  <div className="section-heading compact">
    <span>{t.afterEyebrow}</span>
    <h2>{t.afterHeading}</h2>
  </div>
  <div className="roadmap-list">
    {t.after.map((item) => (
      <div key={item.title}>
        <strong>{item.title}</strong>
        <p>{item.body}</p>
      </div>
    ))}
  </div>
</section>
```

Same structure as current — data-driven instead of hardcoded JSX so it's bilingual.

---

## 6. Honeycomb Background Accent

The landing page currently has zero visual reference to the hive metaphor beyond the
CapyBee avatar. Add a low-opacity hex pattern behind the hero using the **same polygon
geometry already defined in `HoneycombMap.tsx`** (`POLYGON_POINTS = '32,2 62,18 62,54
32,70 2,54 2,18'`), so it's visually consistent with the in-app honeycomb rather than a
new, unrelated pattern.

Add to `page-shell` (or a new wrapping `<div className="landing-hex-bg">` around the
`<section className="hero">`):

```jsx
<div className="landing-hex-bg" aria-hidden="true">
  <svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <pattern id="hexPattern" width="68" height="108" patternUnits="userSpaceOnUse" patternTransform="scale(1.4)">
        <polygon points="32,2 62,18 62,54 32,70 2,54 2,18" fill="none" stroke="#f2b233" strokeWidth="1" strokeOpacity="0.14" />
        <polygon points="32,72 62,88 62,124 32,140 2,124 2,88" fill="none" stroke="#f2b233" strokeWidth="1" strokeOpacity="0.14" />
      </pattern>
    </defs>
    <rect width="100%" height="100%" fill="url(#hexPattern)" />
  </svg>
</div>
```

CSS:

```css
.landing-hex-bg {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}

.page-shell {
  position: relative;
}
```

Keep opacity/stroke subtle (`strokeOpacity: 0.14` as above) — this is a background
texture, not a foreground element. Do not let it reduce text contrast in `.hero-copy` or
`.hero-card`; both already sit on `var(--panel)` (`rgba(255,255,255,0.8)`) with
`backdrop-filter: blur(18px)`, which is sufficient to keep the pattern from interfering
with legibility.

---

## 7. CSS Additions

Add to `styles.css`, near the existing `.stat-chip` rules:

```css
.trust-chip {
  gap: 4px;
}

.trust-chip strong {
  font-size: 0.85rem;
  color: var(--ink);
}

.trust-chip span {
  font-size: 0.78rem;
  color: var(--muted);
  line-height: 1.35;
}
```

No other structural CSS changes are required — `.hero`, `.hero-copy`, `.hero-card`,
`.feature-card`, `.roadmap`, and their responsive breakpoints (lines ~370–401 in
`styles.css`) stay as-is.

---

## 8. What NOT to Change

- Do not alter the `loading` state, the `sessionExpired` state, or the
  `AuthenticatedHome` branch in `App.tsx` — this spec only touches the unauthenticated
  landing return block.
- Do not add a manual language toggle in this pass — `locale` continues to be derived
  from `navigator.language` as it already is elsewhere in the app. If a manual toggle is
  wanted later, it should be its own spec so it can be applied consistently across the
  whole app, not just the landing page.
- Do not reintroduce any tech-stack, framework, or infrastructure detail into
  user-facing copy anywhere on this screen.

---

## 9. Acceptance Checklist

- [ ] Eyebrow badge shows the bilingual tagline, not "CapyBee concept scaffold."
- [ ] Hero card shows the 4-item trust strip (private space, no ads/social, parent-managed
      account, optional real name) — the old tech-stack `stats` array is fully removed.
- [ ] Feature section shows the 4 real product pillars (check-ins, two worlds, missions,
      friendship tracker) — the old dev-milestone `highlights` array is fully removed.
- [ ] "What happens after sign-in" section is data-driven from `landingCopy` and renders
      correctly in both `en` and `pl`.
- [ ] A subtle honeycomb hex pattern is visible behind the hero, using the same polygon
      geometry as `HoneycombMap.tsx`, at low opacity, with no legibility impact on hero
      text or the trust card.
- [ ] Switching `navigator.language` to a `pl-*` locale renders all landing copy in
      Polish, including the tagline, CTAs, trust strip, features, and after-sign-in
      section.
- [ ] No tech stack, framework, or infrastructure name (Spring Boot, PostgreSQL, Fly.io,
      etc.) appears anywhere in the rendered landing page copy.
- [ ] Layout, responsive breakpoints, and animation timing remain visually consistent
      with the rest of the page (no new CSS beyond `.trust-chip` and `.landing-hex-bg`).
