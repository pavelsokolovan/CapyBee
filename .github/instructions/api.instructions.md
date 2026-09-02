---
applyTo: "app/server/**/*.java,app/ui/src/**/*.ts,app/ui/src/**/*.tsx"
---

# API and integration contract standards

## REST design principles

- Keep endpoints explicit and domain-oriented.
- Use consistent resource naming and conventional HTTP semantics.
- Use correct status codes: `201 Created` for successful creates, `204 No Content` for successful deletes, and appropriate `404`, `409`, or `400` responses for domain and validation errors.
- Treat controllers as API boundaries, not business logic containers.

## Controller rules

- Controllers should orchestrate request validation and response mapping only.
- Never directly touch repositories in controller code.
- Use Java records for request/response DTOs.
- Never expose JPA entities directly from controllers.
- Keep service exceptions domain-specific and let a centralized exception handler translate them into API responses.

## Request and response design

- Prefer small DTOs tailored to a specific interaction.
- Validate all request inputs before or during service invocation.
- Use snake-case or clear property names consistently across frontend and backend.
- Return only the minimum data needed for the client to render and act.

## Offline-safe and idempotent operations

- New write operations that originate from authenticated screens should align with the offline queue pattern used by the app.
- For client-retry scenarios, support idempotent creation via an optional client-supplied identifier on create DTOs when the pattern already exists.
- For retries after offline sync, avoid duplicate server-side writes from repeated request submission.
- Ensure server logic and client logic agree on duplicate detection behavior.

## Security and authorization

- All new endpoints must be registered in explicit Spring Security rules.
- Default to authenticated access.
- Only publicly expose endpoints that are intentionally public such as health checks.
- Validate ownership before mutating or reading records that belong to a specific child user.
- Treat server-side sessions and OAuth flows as the contract; do not replace them with client-only trust.

## Client-server alignment

- When the frontend calls an API, match the backend contract exactly.
- Keep API payload shapes stable and predictable.
- Avoid overloading a single endpoint with unrelated behavior.
- When changing a contract, update both
affected UI code and server code in the same change.

## Error handling expectations

- Service-layer exceptions should be meaningful and specific.
- Controller-level status mapping should be centralized and consistent.
- Do not scatter `ResponseEntity` status code logic across services.
- Communicate meaningful error messages without leaking sensitive information.

## Testing expectations

- Cover API-related validation and ownership behavior in service tests.
- For new write operations, include offline retry or duplicate suppression cases.
- Prefer tests that verify the contract behavior rather than mocked HTTP plumbing alone.

## Common mistakes to avoid

- Returning entities from controllers.
- Bypassing the service layer.
- Mixing persistence logic into HTTP endpoints.
- Hardcoding client assumptions that do not match server validation.
- Allowing public access to child-scoped data.
- Creating duplicate mutation effects during retry or offline sync.
