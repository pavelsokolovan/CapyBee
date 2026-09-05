---
applyTo: "**/*.{md,java,ts,tsx,sql,yml,yaml}"
---

# CapyBee project overview

Quick orientation for any file in this repo. Detailed rules live in the
always-loaded root `copilot-instructions.md` and in the scoped
`java-server.instructions.md` / `ui.instructions.md` / `api.instructions.md` /
`database.instructions.md` files — this file intentionally does not repeat
them, so check those before assuming a rule is missing here.

## Product intent

CapyBee is a children's companion app for kids who have moved to a new
country: daily check-ins, small real-world missions, a private friendship
tracker, a honeycomb progress map, and memory-based reflection on both old
and new worlds.

Read [docs/CapyBee_concept.md](../../docs/CapyBee_concept.md) and
[docs/specifications](../../docs/specifications) before implementing features
that change user-facing behavior, data model, onboarding, or emotional
experience.

## Core architecture

- Backend: Spring Boot 4.1 / Java 25 / Maven — package root `com.capybee.server`, layers `config`, `domain`, `repository`, `service`, `web`.
- Frontend: React 19 + TypeScript + Vite 6 + Tailwind CSS 3 — app source in `app/ui/src`.
- Database migrations: `app/server/src/main/resources/db/migration` (Flyway).
- Deployment: single container — UI build copied into Spring Boot static resources, served as one Fly.io app with a private Postgres instance.

## Non-negotiables

Safety/privacy, bilingual EN/PL copy, offline-first writes, and strict
backend layering are hard requirements for every change in this repo, not
just recommendations — see `copilot-instructions.md` for the full list.
