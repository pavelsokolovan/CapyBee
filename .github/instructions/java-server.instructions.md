---
description: "Use when implementing Java backend code, Spring services, controllers, authorization, or service unit tests."
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
- Follow the [API contract rules](api.instructions.md) for DTO contracts, HTTP responses, and idempotency.

## Service design

- Keep service methods narrow and cohesive.
- Validate all state transitions before persisting changes.
- Use exceptions to signal business-level failures and let handlers map them to HTTP responses.
- Make behavior explicit and easy to test.
- Implement the API's idempotent-create contract using nearby `CheckInService` / `MissionService` patterns; preserve ownership checks on retries.

## DTO and API patterns

- Use Java records for request and response DTOs.
- Do not expose persistence entities as API payloads.
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
- Follow `CheckInServiceTest`, `ChildProfileServiceTest`, and `MissionServiceCompletionTest`; use the [service-test skill](../skills/service-unit-test-scaffold/SKILL.md) for scaffolding or coverage troubleshooting.
- Do not lower the service coverage threshold; add missing tests. Run a focused test during iteration and the required final backend gate in the [build guide](build-workflow.instructions.md).
- For persistence changes, also follow [database rules](database.instructions.md).
