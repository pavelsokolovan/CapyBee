---
name: flyway-migration
description: Safe database migration authoring for CapyBee using Flyway. Use this skill when adding new tables, modifying schema, or setting up indexes. Covers naming conventions, never-edit rules, migration checklist, local verification, and troubleshooting. Triggers on "add database migration", "create migration", "update schema", "add database table".
---

# Flyway Migration Safety & Patterns

## When to Use

- Creating a new SQL migration file for a schema change
- Adding tables, indexes, or constraints
- Modifying column types or constraints

## Key Principles

1. **Never Edit Applied Migrations** — Once a migration has run against any environment, create a new migration to fix it.
2. **Explicit Naming** — Use clear, descriptive V-numbers: `V<N>__<description>.sql`
3. **Ordered in Git** — Migrations applied in version order; commit order determines test order.
4. **Safe Rollback** — CapyBee uses "undo" migrations only; design migrations to be downgrade-safe.

## File Naming

**Location:** `app/server/src/main/resources/db/migration/`

**Pattern:** `V<N>__<description>.sql`

Examples:
- `V1__base_schema.sql`
- `V2__add_friendships_and_memories.sql`
- `V4__add_avatar_url_length.sql`

**Checklist:**
- [ ] Version number is sequential (no gaps, no duplicates)
- [ ] Description uses underscores, lowercase, specific ("add_friendships" not "updates")
- [ ] File is in correct location

## SQL Patterns & Conventions

### Creating Tables

**Template:**
```sql
CREATE TABLE entity_names (
  id UUID PRIMARY KEY,
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
  field_name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);
```

### 2. Add Indexes
```sql
CREATE INDEX idx_entity_names_child_profile_id ON entity_names(child_profile_id);
CREATE INDEX idx_entity_names_deleted_at ON entity_names(deleted_at);
```

### 3. Modify Schema
```sql
ALTER TABLE entity_names ADD COLUMN new_field VARCHAR(255) DEFAULT '';
ALTER TABLE entity_names ALTER COLUMN field_name TYPE TEXT;
```

### 4. Seed Data (Optional)
```sql
INSERT INTO missions (id, title, created_at, updated_at)
VALUES (gen_random_uuid(), 'Title', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

## Verification

**Test locally:**
```bash
cd app/server
mvn clean flyway:clean flyway:migrate -DskipTests
mvn spring-boot:run
curl http://localhost:8080/api/health
```

## Pre-Commit Checklist

- [ ] File name: `V<N>__<description>.sql` (sequential, no gaps)
- [ ] Location: `app/server/src/main/resources/db/migration/`
- [ ] All FK use `ON DELETE CASCADE`
- [ ] Indexes on FK + soft-delete columns
- [ ] No unguarded UPDATE/DELETE
- [ ] Migration tested locally
- [ ] Spring Boot starts after migration
- [ ] All tests pass

## Common Mistakes & Quick Fixes

- **Editing applied migrations** → Checksum mismatch. Create a new migration instead.
- **Missing indexes on FK/soft-delete** → Slow queries. Always index.
- **Bulk UPDATE/DELETE without WHERE** → Accidental wipe. Always be explicit.
- **Referencing tables from future migrations** → Flyway fails. Create in order.

## References

See `references/` for detailed patterns:
- `schema-conventions.md` — CapyBee schema diagram, data type guidance
- `data-types.md` — Postgres type recommendations
