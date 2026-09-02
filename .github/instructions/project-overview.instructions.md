---
applyTo: "**/*.{md,java,ts,tsx,sql,yml,yaml}"
---

# CapyBee project overview

## Product intent

CapyBee is a children's companion app for kids who have moved to a new country. It supports emotional processing of homesickness while helping them build a new life in a warm, age-appropriate, privacy-first way.

The app centers on:
- daily check-ins
- small real-world missions
- a private friendship tracker
- a honeycomb progress map
- memory-based reflection of both old and new worlds

Read the product specs in `docs/CapyBee_concept.md` and `docs/specifications/` before implementing features that change user-facing behavior, data model, onboarding, or emotional experience.

## Core architecture

- Backend: Spring Boot 4.1 / Java 25 / Maven
- Frontend: React 19 + TypeScript + Vite 6 + Tailwind CSS 3
- Deployment model: single-container app with UI built into Spring Boot static resources and served from a Fly.io app with a private Postgres instance
- Package layout:
  - backend root package: `com.capybee.server`
  - backend layers: `config`, `domain`, `repository`, `service`, `web`
  - frontend app: `app/ui/src`
  - database migrations: `app/server/src/main/resources/db/migration`

## Product principles

1. Safety and privacy are non-negotiable.
2. Emotional tone must be warm, validating, and never clinical or instructional.
3. The app is for one child and their own private account; no social/chat features between users.
4. No child PII beyond safe, minimal profile information.
5. Offline-first writes and resilient UX matter because the app may be used in unstable connectivity or installable PWA mode.
6. Keep implementations small, explicit, and aligned with the existing app pattern rather than introducing new frameworks or architectural drift.

## Required way of working

- Favor explicit, readable code over clever abstractions.
- Prefer small components, narrow service methods, and clear layering.
- Match the existing patterns in the repo before inventing new ones.
- If a change affects the database schema, create a new Flyway migration, never edit an already-applied migration.
- If a change affects the API contract, update both backend and frontend in the same change set.
- If a change affects persisted state or user flows, account for offline retries and idempotent writes.
- Always keep EN and PL copy aligned for any user-visible copy.

## Safety & privacy mandates

- No feature may add chat or social interaction between different users.
- Never expose one child’s private data to another child.
- Do not store or require a real name, address, or precise location.
- Use nicknames only.
- Never add third-party analytics or ad SDKs.
- Preserve the server-session + Google OAuth pattern. Do not replace authenticated app flows with client-only auth.
- All new HTTP endpoints must be explicitly allowed or restricted in Spring Security config.
- Default to authenticated access; only allow truly public endpoints like health endpoints.

## Writing style for user-facing content

- Tone should feel warm, casual, and supportive.
- Validate feelings before suggesting action.
- Never frame homesickness as a problem to be fixed.
- Treat old and new memories as coexisting parts of identity.
- Use short sentences and gentle phrasing.
- Provide bilingual EN/PL content for every visible string.

## Build and validation expectations

- Run relevant backend validation before considering Java or Spring changes complete.
- Run relevant frontend validation before considering UI logic complete.
- For any frontend logic outside pure React components, include unit tests when required.
- For any backend service change, include unit tests that cover the main success path and validation branches.
- Prefer focused validation commands over broad unrelated test suites.

## Do not do

- Do not add social features or multi-user chat.
- Do not bypass Spring Security or introduce insecure client-side auth.
- Do not use field injection.
- Do not expose JPA entities directly in controllers.
- Do not edit existing Flyway migrations in place.
- Do not add hardcoded single-language user text.
- Do not add new analytics or advertising integrations.
- Do not create architecture patterns that bypass the repo’s layering or offline queue conventions.
