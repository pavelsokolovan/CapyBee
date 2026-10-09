---
description: "Use when changing React components, frontend logic, CSS, mobile layout, EN/PL copy, or optimistic offline mutations."
applyTo: "app/ui/src/**/*.ts,app/ui/src/**/*.tsx,app/ui/src/**/*.css"
---

# UI and frontend standards

Owns UI implementation and copy details; universal constraints live in the [root guidance](../copilot-instructions.md).

## Frontend stack and architecture

- React 19 + TypeScript + Vite 6, Tailwind CSS 3, Framer Motion for animation, PWA via `vite-plugin-pwa`. No server-side rendering.

## UI design and implementation rules

- Function components with hooks only.
- Use mobile-first styling, target ~360px width; prefer Tailwind utilities, only add custom CSS in `styles.css` when no utility fits.
- Extract a section out of `AuthenticatedHome.tsx` into `src/components/` once it becomes self-contained; don't keep growing that file.

## Copy, tone, and localization

**CapyBee's voice is never clinical:**
- Short sentences, validates feelings first
- Never says "it'll get better" or frames homesickness as a problem to fix
- Old and new memories coexist as valid
- Implement EN/PL copy with the existing copy-object pattern in `AuthenticatedHome.tsx`; never hardcode single-language user-facing strings.

Example: missions, check-in responses, empty states, notifications all follow this voice.

## Offline-first and data mutation rules

- New create/update actions from authenticated screens must go through the offline queue (`src/offline/queueStore.ts`) and sync engine (`src/offline/syncEngine.ts`), following the optimistic-update pattern already used for check-ins, missions, friendships, and memories.
- Prefer pure, side-effect-light helper functions for data transforms and queue logic.

## Testing expectations

- Any new or modified pure logic outside React components (queue logic, sync engine, session persistence helpers, etc.) must include Vitest unit tests: happy path plus at least one retry/failure/edge case, following the existing `queueStore.test.ts` / `syncEngine.test.ts` pattern (happy-dom, `idb-keyval` mocked/faked).
- Run frontend tests and sync the built UI before integrated manual verification, following the [build guide](build-workflow.instructions.md). Check the Spring Boot-served UI, not only Vite, to catch stale static files.
