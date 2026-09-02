---
applyTo: "app/ui/src/**/*.ts,app/ui/src/**/*.tsx"
---

# UI and frontend standards

## Frontend stack and architecture

- React 19 + TypeScript + Vite 6
- Tailwind CSS 3 for styling
- Framer Motion for animation
- PWA support via Vite PWA tooling
- No server-side rendering

## UI design and implementation rules

- Prefer function components with hooks.
- Keep components focused and reusable.
- Do not grow a single large screen component without splitting meaningful sections into separate components when they become self-contained.
- Use mobile-first styling with a target around 360px width.
- Prefer Tailwind utilities for new styling; only use custom CSS when a Tailwind utility does not fit the design.
- Keep styles consistent with the existing app patterns rather than introducing a different visual language.

## Copy and localization rules

- Every user-facing string must be bilingual (EN/PL).
- Follow the existing copy-object pattern used in the app instead of hardcoded language text.
- Copy should sound warm, casual, and validating; avoid clinical phrasing.
- Do not add new copy that frames homesickness as a problem to fix.

## Offline-first and data mutation rules

- New create/update actions from authenticated screens should go through the offline queue and sync engine.
- Follow the optimistic-update pattern already used for check-ins, missions, friendships, and memories.
- Keep offline-safety, idempotent retries, and queue semantics in mind for all new write flows.
- Treat sync logic as a first-class architecture concern, not an optional UI detail.

## State and logic organization

- Keep logic testable and side-effect-light when possible.
- Prefer pure helper functions for data transforms and queue logic.
- Keep offline queue and sync logic in the existing `src/offline/` area.
- Avoid coupling ephemeral UI state directly to server mutations without a clear sync path.

## Testing expectations

- Any new or modified pure logic outside React components must include unit tests.
- This includes queue logic, sync engine logic, session persistence helpers, and similar side-effect-light modules.
- Add happy-path tests and at least one retry / failure / edge-case test.
- Use the project’s existing Vitest and happy-dom patterns.

## Build and verification

- After local UI changes, run the project build workflow and copy assets into the backend static resources before manual verification.
- Ensure the app still works in the integrated Spring Boot + Vite setup.
- Validate that UI changes are actually visible in the app rather than only in the Vite dev build.

## Security and privacy in the UI

- Do not add ad or analytics integrations.
- Keep child-facing content safe, minimal, and private.
- Avoid any UI pattern that creates cross-user social features or shared data exposure.
- Preserve auth flows consistent with the app’s server-side session model and PWA token restore fallback.

## Common mistakes to avoid

- Hardcoded single-language user strings.
- Directly mutating server state without considering offline queue behavior.
- Creating one-off UI patterns that bypass existing app conventions.
- Adding unsupported social or data-sharing flows.
- Neglecting test coverage for offline logic and session persistence helpers.
