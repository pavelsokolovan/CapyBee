---
applyTo: "app/server/**/*.java"
---

# Java server standards

## Java and Spring conventions

- Use Java 25 features only when they align with the repo standard and project compatibility.
- Use constructor injection everywhere; do not use field injection.
- Prefer explicit configuration and narrow dependencies over “god services”.
- Keep classes focused and package responsibilities clear.

## Layering rules

The backend package structure is intentionally layered:

1. `web` — controllers and DTOs
2. `service` — business logic and validation
3. `repository` — persistence access
4. `domain` — entities and core domain model

Rules:
- Controllers do not access repositories directly.
- Service methods contain validation, ownership checks, and business decisions.
- Repositories do not contain business rule logic.
- Domain entities are not API DTOs.
- Return correct HTTP status codes (201 Create, 204 No Content, 400/404/409 for validation).

## Service design

- Keep service methods narrow and cohesive.
- Validate all state transitions before persisting changes.
- Use exceptions to signal business-level failures and let handlers map them to HTTP responses.
- Make behavior explicit and easy to test.
- Handle duplicate create attempts and idempotent retries intentionally (pattern: optional client-supplied `id` on create DTOs, checked before insert — see `CheckInService`, `MissionService`).

## DTO and API patterns

- Use Java records for request and response DTOs.
- Keep DTOs minimal and tied to specific endpoint contracts.
- Do not pass entities directly across component boundaries.
- Ensure controller return types reflect the API contract rather than persistence internals.

## Security and auth

- Treat security as explicit configuration, not a default practice.
- Use Spring Security configuration to protect new endpoints.
- Default all new endpoints to authenticated access.
- Only allow public endpoints when truly required.
- Protect child-specific resources with ownership validation.

## Testing rules — MANDATORY

**Mandatory: any new or modified method in `.../service/` must ship with unit tests in the same PR.**

- Put tests under `app/server/src/test/java`, mirroring package structure.
- Use JUnit 5 and Mockito pattern: `@ExtendWith(MockitoExtension.class)`, `@Mock` on repositories/collaborators, `@InjectMocks` on the service under test.
- Cover success paths AND every failure branch:
  - Validation errors (`ResponseStatusException`s with 400/404/409/403)
  - Idempotent-create-by-id branches (including when the id already exists)
  - Ownership checks (deny access to other child's resources)
  - Edge cases (empty input, boundary conditions)
- See reference style: `CheckInServiceTest`, `ChildProfileServiceTest`, `MissionServiceCompletionTest`.
- **JaCoCo gate:** `pom.xml` enforces 70% line coverage on `com.capybee.server.service.*` via `mvn verify` (CI runs this too). Do not lower this threshold — add the missing tests instead.
- Run `mvn verify` locally before considering backend work done.

## Build and verification

- Run the relevant Maven verification commands for changed backend code.
- For service-level changes, prefer focused verification rather than unrelated full-suite runs when the goal is a precise fix.
- When modifying backend behavior, ensure the project still passes the repository’s required verification gate.

## Common mistakes to avoid

- Field injection via `@Autowired`.
- Business logic leaking into controllers.
- Direct repository usage from web layer code.
- Returning entities to the client (use DTOs).
- Broad catch-all exceptions that hide real validation errors.
- Adding schema changes without a Flyway migration.
- Creating endpoints without explicit authorization configuration.
- Service tests that don't cover failure branches and ownership validation.
