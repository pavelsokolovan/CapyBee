# GitHub Copilot Instructions — CapyBee

These instructions tell GitHub Copilot how to write code in this repository.
Copilot automatically reads this file and applies the guidance below to chat
answers, edits, and Agent-mode changes.

## Project context

CapyBee ("Razem budujemy nowy ul" / "Together we build a new hive") is a
companion app for ~12-year-olds who have relocated to a new country. It helps
them process homesickness ("My Old World") while gently building a new life
("My New World"), through daily check-ins, small real-world missions, a
private friendship tracker, and a honeycomb progress map. See
[docs/CapyBee_concept.md](../docs/CapyBee_concept.md) and
[docs/specifications](../docs/specifications) for product intent — read the
relevant spec before implementing a feature described there.

This is a children's app: privacy, safety, and a warm, non-clinical tone are
first-class requirements, not afterthoughts (see "Safety & privacy" below).

Monorepo layout:
- `app/server` — Spring Boot 4.1 (Java 25), Maven build, package root
  `com.capybee.server` (`config/`, `domain/`, `repository/`, `service/`,
  `web/`), Flyway migrations in `src/main/resources/db/migration`.
- `app/ui` — React 19 + TypeScript + Vite 6, Tailwind CSS 3, Framer Motion for
  animation, `vite-plugin-pwa` for the installable PWA/service worker, no
  server-side rendering. Main screens live in `AuthenticatedHome.tsx`;
  reusable pieces in `src/components/`; offline queue logic in `src/offline/`
  (`idb-keyval` for IndexedDB storage).
- Single-container deployment: the root `Dockerfile` builds the UI, then
  copies the build into Spring Boot's static resources; the whole thing runs
  as one Fly.io app with a private Postgres instance.

## Coding style

### Backend (Java / Spring Boot)
- Java 25, constructor injection only (no field `@Autowired`).
- Layer strictly: `web` (controllers/DTOs) → `service` (business logic) →
  `repository` (Spring Data JPA) → `domain` (entities). Controllers must not
  touch repositories directly.
- Use Java `record`s for request/response DTOs; never expose JPA entities
  directly from controllers.
- Every schema change goes through a new Flyway migration file in
  `db/migration` (never edit an already-applied migration).
- Return correct HTTP status codes (201 create, 204 delete, 404/409 as
  appropriate) and let a centralized exception handler translate service
  exceptions — don't scatter `ResponseEntity` status logic in services.
- Keep mutations idempotent where the client may retry offline actions
  (existing pattern: optional client-supplied `id` on create DTOs, checked
  before insert — see `CheckInService`, `MissionService`).

### Frontend (React / TypeScript)
- Function components with hooks only; no class components.
- Mobile-first styling (base ~360px width), Tailwind utility classes for new
  UI; keep custom CSS in `styles.css` consistent with existing patterns if a
  Tailwind utility doesn't fit.
- All user-facing strings must be bilingual (EN/PL) using the existing
  copy-object pattern in `AuthenticatedHome.tsx` — never hardcode a single
  language string in new UI.
- New create/update actions from authenticated screens should go through the
  offline queue (`src/offline/queueStore.ts` + `syncEngine.ts`) so they work
  offline, following the optimistic-update pattern already used for
  check-ins, missions, friendships, and memories.
- Prefer small, focused components in `src/components/` over growing
  `AuthenticatedHome.tsx` further; extract when a screen/section becomes
  self-contained.

## Safety & privacy (non-negotiable for this app)

- No feature may add social/chat functionality between different users or
  expose one child's data to another. The friendship tracker is private and
  local to the child's own account.
- Never require or store a child's real name, address, or precise location;
  nicknames only. Avoid adding new PII fields without an explicit reason.
- Do not add third-party analytics/ads SDKs.
- Auth flows rely on Google OAuth2 + server-side sessions plus a
  localStorage restore-token fallback (`sessionPersistence.ts`) for installed
  PWAs — do not replace this with client-only auth or long-lived tokens
  stored insecurely.
- All new endpoints must go through Spring Security config (`SecurityConfig`)
  explicitly — default to authenticated, only allow-list what truly must be
  public (e.g. `/api/health`).

## Tone & content

- CapyBee (the character) speaks warmly and casually, never like a therapist
  or teacher: short sentences, validates feelings first, never says "it'll
  get better." When writing copy (missions, check-in responses, empty
  states), match this voice and provide both EN and PL text.
- Never frame homesickness as a problem to fix; old and new memories coexist.

## Testing conventions

- Backend: JUnit 5 tests under `app/server/src/test/java`, mirroring the
  package structure of the class under test (e.g. service tests in
  `service/`). Use Mockito for collaborators; prefer testing service logic
  over controllers.
- Frontend: keep new logic (offline queue, sync engine, pure helpers)
  unit-testable and side-effect-free where possible.
- Cover the happy path and at least one edge/offline-retry case for new
  write operations.

## Build & workflow notes

- After any `app/ui` source change for local/manual testing, run
  `npm run build` in `app/ui` and copy `dist/*` into BOTH
  `app/server/src/main/resources/static` and
  `app/server/target/classes/static` — the running dev server reads from
  `target/classes/static`, and a stale copy is a common source of "missing
  feature" bugs. The `Workflow: Sync UI + Static` / `Workflow: Build + Copy +
  Start All` VS Code tasks (or `scripts/dev-workflow.ps1`) automate this.
- Use the `Dev: Start All` task for local development (Spring Boot on 8080 +
  Vite dev server).
- Fly.io deployment uses `SPRING_PROFILES_ACTIVE=fly` and a private Postgres
  network; don't hardcode fly-specific config outside `application-fly.yml`.

## Tone for Copilot's own responses

- Be concise. When making a non-trivial change, briefly explain your
  reasoning.
