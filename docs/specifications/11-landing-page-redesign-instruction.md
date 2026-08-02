# 11 — Landing Page Redesign: Minimal, Kid-Facing, Above-the-Fold

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

Target state (superseded by the v2 revision below — kept here only as the original
problem framing):

- Hero leads with the real tagline instead of a scaffold status report.
- A short trust line answers the parent's unspoken safety question without turning the
  hero into a card full of text.
- Feature cards (below the fold) describe the four real product pillars, not
  implementation tasks.
- A subtle honeycomb pattern ties the landing page visually to the in-app hive.
- Landing hero is English-only; bilingual support stays scoped to in-app screens.

## 1.1 Revision — Audience, Length, and Scroll (v2)

First draft of this spec over-corrected: it added a full trust-strip card and a long
parent-facing lead paragraph, which turned the landing page into a wall of text. Revised
direction, per Pavlo:

- **The first screen speaks to the kid, not the parent.** `04-screen-descriptions.md`
  lists the parent as SCR-01's primary user because the parent is the one who taps
  "Continue with Google," but the *voice and tone* on screen should read like CapyBee
  talking to the child, not a product pitch to an adult. Keep it playful and short —
  match the register used inside the app, not a marketing register.
- **Minimal text.** No paragraphs. One short headline, one short line, done. Anything
  longer than that gets cut or pushed below the fold.
- **CapyBee waving stays** — it's the one thing that should be visually dominant.
- **English only on the first screen.** Do not add a Polish variant of the landing hero
  in this pass. Bilingual support stays scoped to the in-app screens
  (`AuthenticatedHome.tsx` and beyond); the landing page is English-only for now.
- **The login button must be visible without scrolling**, on common phone viewports
  (360–420px wide, ~700–800px tall) and on desktop. Nothing above the CTA should push it
  below the fold. If content doesn't fit above the fold at minimal text length, cut
  content — don't shrink the button or the CapyBee art to make room.
- Everything below the CTA (features, "what happens after sign-in") is fine to require
  scrolling — those sections are optional reading, not part of the required first
  impression.

---

## 2. Copy — Minimal, English-Only, Kid-Voiced

Add a `landingCopy` object near the top of `App.tsx`. English only for this screen —
do not add a `pl` key here.

```jsx
const landingCopy = {
  tagline: 'Together we build a new hive',
  heroTitle: 'Hey, you found the hive.',
  heroLine: "Let's settle in — one small step at a time.",
  ctaPrimary: 'Continue with Google',
  trustLine: 'Private. No ads. Just for your family.',

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
};
```

Remove the existing `highlights` and `stats` arrays entirely — `features` and `after`
replace them below the fold. There is no `trust` array anymore; trust is a single line
(`trustLine`) directly under the CTA, not a card.

Note on register: the hero (`heroTitle`, `heroLine`) is CapyBee talking directly to the
kid — short, playful, no marketing tone. `features` and `after`, further down the page,
are read by the parent scrolling for more detail, so those can stay a notch more
descriptive — but they're optional reading, not part of the required first impression.

---

## 3. Hero Section

Replace the `<section className="hero">` block. This is now a single centered column,
not the old two-column `hero-copy` / `hero-card` split — the second column is what was
pushing the CTA below the fold.

```jsx
const t = landingCopy;

<section className="hero hero-minimal">
  <CapyBeeAvatar src={capyBeeAvatar.waving} size={140} alt="CapyBee" className="hero-capybee" />
  <span className="eyebrow">{t.tagline}</span>
  <h1>{t.heroTitle}</h1>
  <p className="hero-subline">{t.heroLine}</p>
  <a className="primary-button" href="/oauth2/authorization/google">
    {t.ctaPrimary}
  </a>
  <span className="trust-line">{t.trustLine}</span>
</section>
```

`const t = landingCopy;` is declared once, in the component body above the returned
JSX — it's reused by the features and roadmap sections in Sections 4 and 5 below, not
redeclared per section.

Notes:
- No `hero-card`, no trust card grid, no secondary "see how it works" button — those are
  exactly the elements that pushed the login button down. Cut, not shrunk.
- `t.trustLine` replaces the whole trust card with one small line of reassurance under
  the button — still answers the parent's "is this safe" question, just without turning
  the hero into a wall of text.
- `heroTitle` and `heroLine` together should read as CapyBee greeting the kid, not a
  product description — keep both under roughly 8 words each.
- CapyBee avatar size drops from 180px to 140px specifically to save vertical space; it's
  still clearly the dominant visual element since nothing else in the hero competes with
  it.

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
texture, not a foreground element. Since `.hero-minimal` (Section 7) sits directly on the
page background rather than inside a `var(--panel)` card, double-check contrast on the
`h1` and CTA button against the hex pattern specifically — bump `strokeOpacity` down
further (e.g. `0.08`) if the text looks busy against it.

---

## 7. CSS Additions

Add to `styles.css`. `.hero-minimal` overrides the old two-column `.hero` grid with a
single centered column sized to fit above the fold on a typical phone viewport
(360–420px × 700–800px):

```css
.hero-minimal {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 10px;
  min-height: 100dvh;
  padding: 24px 20px;
  box-sizing: border-box;
}

.hero-minimal .hero-capybee {
  margin-bottom: 4px;
}

.hero-minimal h1 {
  margin: 0;
  font-size: clamp(1.8rem, 7vw, 2.6rem);
  line-height: 1.05;
  letter-spacing: -0.03em;
  max-width: 14ch;
}

.hero-subline {
  margin: 0 0 6px;
  font-size: 1rem;
  color: var(--muted);
  max-width: 32ch;
}

.hero-minimal .primary-button {
  margin-top: 4px;
  padding: 0 28px;
}

.trust-line {
  margin-top: 10px;
  font-size: 0.78rem;
  color: var(--muted);
}
```

Notes:
- `min-height: 100dvh` (dynamic viewport height, not `vh`) is deliberate — it accounts
  for mobile browser chrome (address bar) so the CTA doesn't end up hidden behind it on
  phones, which plain `100vh` would risk.
- This hero no longer needs the two-column `@media` breakpoint logic that stacked
  `.hero-copy`/`.hero-card` on narrow screens — `.hero-minimal` is already a single
  column at every width, so remove or skip that breakpoint override for this section
  specifically (the `.feature-grid` and `.roadmap` breakpoints below the fold stay as-is).
- `.hero`, `.feature-card`, `.roadmap`, and their existing responsive rules elsewhere in
  `styles.css` stay unchanged — only the hero's internal layout changes.

---

## 8. What NOT to Change

- Do not alter the `loading` state, the `sessionExpired` state, or the
  `AuthenticatedHome` branch in `App.tsx` — this spec only touches the unauthenticated
  landing return block.
- Do not add a Polish variant, a language toggle, or any `locale` branching to the hero
  in this pass — it's English-only. `locale`-based branching stays scoped to the in-app
  screens that already use it.
- Do not reintroduce any tech-stack, framework, or infrastructure detail into
  user-facing copy anywhere on this screen.
- Do not add a secondary CTA, trust card, or any other element back into the hero that
  would push the primary button below the fold — if new content is requested later for
  the hero, something else has to leave to keep the button above the fold.

---

## 9. Acceptance Checklist

- [ ] Eyebrow badge shows the tagline, not "CapyBee concept scaffold."
- [ ] Hero is a single centered column: CapyBee waving, one short headline, one short
      line, the CTA button, and a one-line trust note — nothing else.
- [ ] The old two-column hero (`.hero-copy` / `.hero-card`) and the tech-stack `stats`
      array are fully removed.
- [ ] On a 375×667 viewport (iPhone SE) and a 390×844 viewport (iPhone 12/13), the
      "Continue with Google" button is visible without scrolling.
- [ ] On desktop widths, the hero is vertically centered and the CTA is visible without
      scrolling on a standard 900px-tall browser window.
- [ ] Feature section (below the fold) shows the 4 real product pillars (check-ins, two
      worlds, missions, friendship tracker) — the old dev-milestone `highlights` array is
      fully removed.
- [ ] "What happens after sign-in" section (below the fold) is data-driven from
      `landingCopy.after`.
- [ ] A subtle honeycomb hex pattern is visible behind the hero, using the same polygon
      geometry as `HoneycombMap.tsx`, at low opacity, with no legibility impact on the
      headline or CTA.
- [ ] No tech stack, framework, or infrastructure name (Spring Boot, PostgreSQL, Fly.io,
      etc.) appears anywhere in the rendered landing page copy.
- [ ] No Polish text appears on the landing hero.
