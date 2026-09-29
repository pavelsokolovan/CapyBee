---
applyTo: "**/*.{md,java,ts,tsx,sql,yml,yaml}"
---

# CapyBee project overview

Full orientation for any file in this repo. Scoped rules live in
`java-server.instructions.md` / `ui.instructions.md` / `api.instructions.md` /
`database.instructions.md` / `build-workflow.instructions.md` — check those
before assuming rules are missing.

## Product intent

CapyBee ("Razem budujemy nowy ul" / "Together we build a new hive") is a children's companion app for ~12-year-olds who have relocated to a new country. It helps them process homesickness ("My Old World") while gently building a new life ("My New World"), through daily check-ins, small real-world missions, a private friendship tracker, and a honeycomb progress map.

**Read [`docs/CapyBee_concept.md`](../../docs/CapyBee_concept.md) and [`docs/specifications`](../../docs/specifications) before implementing features that change product behavior, data model, onboarding, or emotional experience.**

## Monorepo structure

- **`app/server`** — Spring Boot 4.1 (Java 25), Maven build.
  - Package root: `com.capybee.server`
  - Layers: `config/`, `domain/`, `repository/`, `service/`, `web/`
  - Flyway migrations: `src/main/resources/db/migration/`
- **`app/ui`** — React 19 + TypeScript + Vite 6, Tailwind CSS 3, Framer Motion.
  - Main screens: `AuthenticatedHome.tsx`
  - Reusable UI: `src/components/`
  - Offline queue: `src/offline/` (queueStore, syncEngine, idb-keyval)
  - PWA service worker: `vite-plugin-pwa`
- **Deployment:** Single container. UI build (`npm run build` → `dist/`) copied into Spring Boot static resources; entire app runs as one Fly.io instance with private Postgres.

## Product and technical non-negotiables

Read [`copilot-instructions.md`](../../copilot-instructions.md) for complete constraints. Key points:

- **Safety & privacy first:** no inter-child data sharing, minimal PII (nicknames only), no analytics SDKs.
- **Auth:** Google OAuth2 + server sessions; `sessionPersistence.ts` for PWA restore-token fallback.
- **Tone:** warm, casual (never clinical), validates feelings, bilingual EN/PL always.
- **Offline-first:** all mutations through offline queue + sync engine.
- **Strict layering:** `web` → `service` → `repository` → `domain`. Controllers never touch repos directly.
