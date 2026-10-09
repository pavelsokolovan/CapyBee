---
description: "Use when designing or changing REST endpoints, DTO payloads, HTTP errors, or client-server contracts, including offline retries and idempotency."
applyTo: "app/server/**/*.java,app/ui/src/**/*.ts,app/ui/src/**/*.tsx"
---

# API and integration contract standards

Owns HTTP contracts. See [Java standards](java-server.instructions.md) for server implementation and [UI standards](ui.instructions.md) for client integration.

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
- Validate ownership before returning an existing record for a duplicate identifier.
- Server and client duplicate-detection logic must agree.

## Client-server alignment

- When a contract changes, update both the affected UI code and server code in the same change — never let them drift.
- Communicate meaningful error messages to the client without leaking sensitive information.
