---
applyTo: "app/ui/src/**/*.ts,app/ui/src/**/*.tsx"
---

# UI and frontend standards

Product-level safety/privacy rules live in `copilot-instructions.md` — this
file covers UI implementation and tone specifics.

## Frontend stack and architecture

- React 19 + TypeScript + Vite 6, Tailwind CSS 3, Framer Motion for animation, PWA via `vite-plugin-pwa`. No server-side rendering.

## UI design and implementation rules

- Function components with hooks only.
- Use mobile-first styling, target ~360px width; prefer Tailwind utilities, only add custom CSS in `styles.css` when no utility fits.
- Extract a section out of `AuthenticatedHome.tsx` into `src/components/` once it becomes self-contained; don't keep growing that file.

## Copy, tone, and localization

**CapyBee (the character) speaks warmly and casually, never clinical or clinical:**
- Short sentences, validates feelings first
- Never says "it'll get better" or frames homesickness as a problem to fix
- Old and new memories coexist as valid
- Bilingual (EN/PL) always — use the existing copy-object pattern in `AuthenticatedHome.tsx`
- Never hardcode a single-language string; provide both EN and PL for every user-facing text

Example: missions, check-in responses, empty states, notifications all follow this voice.

## Offline-first and data mutation rules

- New create/update actions from authenticated screens must go through the offline queue (`src/offline/queueStore.ts`) and sync engine (`src/offline/syncEngine.ts`), following the optimistic-update pattern already used for check-ins, missions, friendships, and memories.
- Prefer pure, side-effect-light helper functions for data transforms and queue logic.

## Testing expectations

- Any new or modified pure logic outside React components (queue logic, sync engine, session persistence helpers, etc.) must include Vitest unit tests: happy path plus at least one retry/failure/edge case, following the existing `queueStore.test.ts` / `syncEngine.test.ts` pattern (happy-dom, `idb-keyval` mocked/faked).
- Run `npm test` (or `npm run test:coverage`) in `app/ui` before considering frontend work done.

## Build and verification

- After UI changes, run the build workflow and copy `dist/*` into both Spring Boot static resource folders before manual verification — a stale copy is a common source of "missing feature" bugs.
- Verify changes in the integrated Spring Boot + Vite setup, not only the Vite dev server.

## Common mistakes to avoid

- Hardcoded single-language user strings.
- Mutating server state directly without going through the offline queue.
- One-off UI patterns that bypass existing conventions (component structure, copy pattern, offline queue).
