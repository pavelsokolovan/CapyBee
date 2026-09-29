# GitHub Copilot Instructions — CapyBee

Lean global guidance. Scoped rules live in `.github/instructions/` (applied to matching file paths).

## What is CapyBee

Children's companion app for relocated kids: daily check-ins, missions, private friendship tracker, honeycomb map. See [`docs/CapyBee_concept.md`](../docs/CapyBee_concept.md) and [`docs/specifications`](../docs/specifications) before changing product behavior.

Stack: Spring Boot 4.1 (Java 25) + React 19/TypeScript + Vite 6 + Tailwind 3. Single-container deployment (Fly.io Postgres).

## Non-negotiable constraints

**Safety, privacy, and tone are first-class requirements:**
- **No inter-child data sharing or social features.** Friendship tracker is private to each child.
- **Minimal PII:** only nicknames, no real names/addresses/precise location.
- **No third-party analytics, ads, or tracking.**
- **Auth security:** Google OAuth2 + server sessions (never long-lived client tokens).
- **Warm tone:** CapyBee speaks casually and validates feelings; bilingual EN/PL copy always.
- **Offline-first writes:** mutations through offline queue + sync engine.

## Copilot's approach

- **Be concise.** Explain reasoning in simple English for non-trivial changes.
- **Read scoped instructions** (`.github/instructions/`) for layering rules, testing patterns, API design, schema rules, UI conventions.
- **Follow existing patterns** before proposing new ones: check-in service, mission service, offline queue, copy localization.
- **Quality gates before done:** `mvn verify` (backend), `npm test` (frontend), build-copy workflow sync.
