---
description: "Use when changing persistence models, Postgres schema, Flyway migrations, indexes, constraints, or database configuration."
applyTo: "app/server/**/*.java,app/server/src/main/resources/**/*.sql,app/server/src/main/resources/**/*.yml,app/server/src/main/resources/**/*.yaml"
---

# Database and persistence standards

Owns schema and migration policy. See [Java standards](java-server.instructions.md) for layering and tests; use the [Flyway skill](../skills/flyway-migration/SKILL.md) when authoring a migration.

## Schema change policy

- Every schema change must be a new Flyway migration under `app/server/src/main/resources/db/migration`.
- Never edit an already-applied migration; use descriptive, incrementally-ordered migration names.
- Add the persistence model and its migration together when introducing a new domain concept.

## Naming and modeling conventions

- Keep entity names clear and domain-based; prefer explicit, business-meaningful field names.
- Model relationships minimally and intentionally; avoid over-normalization when requirements are straightforward.
- Keep child-scoped data isolated and ownership-aware.

## Migration quality bar

- Include only the schema changes necessary for the feature.
- Make migrations safe for repeated deployment against environments that already have the app schema.
- Use SQL compatible with the project's Postgres setup.
- Add indexes/constraints that match the intended query and access patterns.
- Follow the repo's existing migration style; never reorder or renumber existing files.

## Data privacy

- Apply the [root privacy rules](../copilot-instructions.md) to schema fields; store only data needed by the feature.

## Tests for persistence changes

- Cover validation branches, duplicate-create protection, and ownership errors introduced by the change in the corresponding service test, following [Java testing rules](java-server.instructions.md).
