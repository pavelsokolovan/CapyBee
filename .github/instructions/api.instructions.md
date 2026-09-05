---
applyTo: "app/server/**/*.java,app/ui/src/**/*.ts,app/ui/src/**/*.tsx"
---

# API and integration contract standards

This file covers the HTTP contract between frontend and backend only.
Backend implementation details (layering, DI, security config, testing
pattern) live in `java-server.instructions.md`; UI implementation details
live in `ui.instructions.md` — avoid restating those here.

## REST design principles

- Keep endpoints explicit and domain-oriented; avoid overloading a single endpoint with unrelated behavior.
- Use correct status codes: `201 Created` for creates, `204 No Content` for deletes, `400`/`404`/`409` for validation and domain errors.

## Request and response design

- Prefer small DTOs tailored to a specific interaction; return only the minimum data the client needs.
- Use consistent, clear property names across frontend and backend.
- Validate all request inputs before or during service invocation.

## Offline-safe and idempotent operations

- New write operations from authenticated screens must align with the app's offline queue pattern.
- Support idempotent creation via an optional client-supplied identifier on create DTOs where the pattern already exists, so retried or offline-synced requests don't create duplicate records.
- Server and client duplicate-detection logic must agree.

## Client-server alignment

- When a contract changes, update both the affected UI code and server code in the same change — never let them drift.
- Communicate meaningful error messages to the client without leaking sensitive information.

## Common mistakes to avoid

- Payload shapes that don't match between client and server.
- Creating duplicate mutation effects during retry or offline sync.
- Overloading a single endpoint with unrelated behavior.
