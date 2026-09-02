---
applyTo: "app/server/**/*.java,app/server/src/main/resources/**/*.sql,app/server/src/main/resources/**/*.yml,app/server/src/main/resources/**/*.yaml"
---

# Database and persistence standards

## Schema change policy

- Every schema change must be implemented as a new Flyway migration under `app/server/src/main/resources/db/migration`.
- Never edit an already-applied migration.
- Use descriptive migration names and keep them incremental.
- When the product requires a new domain concept, add the persistence model and migration together.

## Naming and modeling conventions

- Keep entity names clear and domain-based.
- Prefer explicit, business-meaningful field names.
- Use Java `record` DTOs for API payloads; do not return entities from controllers.
- Model relationships minimally and intentionally; avoid over-normalization when the app’s requirements are straightforward.
- Keep child-scoped data isolated and ownership-aware.

## Backend persistence layer rules

- Keep the layer order strict: `web` → `service` → `repository` → `domain`.
- Repositories should be Spring Data JPA repositories or narrow persistence abstractions, not service-layer logic containers.
- Service logic should own validation, ownership checks, and transactional rules.
- Domain entities should represent persistence concerns, not API contracts.

## Transaction and data integrity rules

- Use transactional boundaries at the service layer where mutation consistency matters.
- Validate existence and ownership before updates or deletes.
- Avoid multi-step writes that can partially succeed without clear rollback or validation rules.
- For idempotent client writes, support optional client-supplied IDs and reject duplicate creation when the same logical operation is replayed.

## Migration quality bar

- Include only the necessary schema changes for the feature.
- Make migrations safe for repeated deployment in environments that already contain the app schema.
- Use SQL that is compatible with the project’s Postgres setup.
- Consider indexes and constraints that match the intended queries and data access patterns.
- Follow the repo’s existing migration style and keep migration file ordering intact.

## Data privacy and safety requirements

- Store only the minimal data needed for the feature.
- Do not introduce profile fields that expose a child’s real identity or precise location.
- Keep personal data child-scoped and access-controlled.
- Do not add cross-user relations or shared social data without explicit product approval.

## Tests for persistence changes

- Add or update service tests for behavior affected by persistence changes.
- Cover validation branches, duplicate-create protection, and ownership errors.
- Prefer repository-agnostic service tests where the logic is the behavior under test.
- If the migration changes state semantics, include regression coverage.

## Common mistakes to avoid

- Editing historical SQL migration files.
- Mixing API DTO logic into entities.
- Using direct repository access from controllers.
- Writing update logic that ignores child ownership or idempotency.
- Adding broad schema changes without a matching feature requirement.
- Introducing data fields for analytics, social features, or location tracking without need.
