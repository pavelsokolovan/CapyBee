---
name: flyway-migration
description: "Use when creating a Flyway migration, adding a database table, changing schema, or adding indexes in CapyBee. Covers safe authoring and non-destructive verification."
---

# Flyway Migration

Use for new tables, indexes, constraints, and column changes. Policy lives in the [database instructions](../../instructions/database.instructions.md); do not edit applied migrations.

## Procedure
1. Read the relevant product specification, current entity, and nearby migrations. Confirm ownership, existing names, and the required change.
2. Inspect existing migration versions; choose the next unused `V<N>__<lowercase_description>.sql` under `app/server/src/main/resources/db/migration/`. Flyway orders by version, not commit order.
3. Author only the necessary Postgres changes. Match existing UUID, timestamp, relationship, and soft-delete conventions where applicable. Choose cascade behavior and indexes for actual access patterns, not mechanically for every column.
4. Check data compatibility: backfills, defaults, constraints on existing rows, and references only to objects available at this version. Correct deployed schema with a new forward migration, not an assumed undo migration.
5. Add the matching persistence changes and service tests for affected validation, ownership, and duplicate-create behavior.
6. Verify against an explicitly disposable database using the project's configured Spring Boot/Flyway startup. Test upgrade from the previous schema and startup from an empty database; inspect migration history and affected constraints.

## Verification And Checklist
- Never run `flyway:clean` or reset a shared/production database as a routine check.
- Confirm the verification datasource before startup; do not alter the user's database configuration silently.
- If a disposable database is unavailable, report migration runtime verification as blocked rather than substituting a destructive command.
- Run the backend gate from the [build guide](../../instructions/build-workflow.instructions.md).
- Confirm a unique version, no edits to applied migrations, ownership-aware relationships, appropriate indexes, explicit data-change predicates, and successful migration/startup checks.

## References
Open only the reference needed, and verify examples against the current schema:
- [Schema conventions](./references/schema-conventions.md): relationships, timestamps, indexes, and example SQL.
- [Postgres data types](./references/data-types.md): field types and constraints.
