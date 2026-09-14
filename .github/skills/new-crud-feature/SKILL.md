---
name: new-crud-feature
description: End-to-end scaffold for a new domain feature spanning database migration, JPA entity, repository, service (with idempotent-create pattern), DTO records, REST controller, and SecurityConfig integration. Use this skill when adding a new child-owned feature to CapyBee (e.g., new activity type, new tracking domain). Triggers on "add new feature", "scaffold new domain entity", "create new feature", "implement new domain".
---

# New CRUD Feature Scaffold

## When to Use

- Adding a new child-owned domain entity
- Full-stack CRUD (DB → JPA → service → REST API)
- Features requiring offline-capable mutations

## Core Workflow (10 Steps)

### Step 1 — Define Data Model

Gather: fields, types, constraints, ownership (must be `ChildProfile` FK). Use soft-delete pattern (`deletedAt`) if user can undo. See references for data-type guidance.

### Step 2 — Create Flyway Migration

Follow `flyway-migration` skill. Pattern: UUID PK, `child_profile_id` FK `ON DELETE CASCADE`, `created_at`/`updated_at`/`deletedAt` timestamps, indexes on FK + soft-delete.

### Step 3 — Create JPA Entity

**Pattern:** See references/entity-template.md for full example.
- [ ] UUID id (no auto-increment)
- [ ] `@ManyToOne` FK to `ChildProfile` with `LAZY` fetch
- [ ] `createdAt` / `updatedAt` / `deletedAt` timestamps (nullable for soft-delete)
- [ ] Constructor + getters/setters only (no business logic)

### Step 4 — Create Repository Interface

See references for full example. Key methods:
```java
List<YourFeatureName> findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID childProfileId);
Optional<YourFeatureName> findByIdAndChildProfile_Id(UUID id, UUID childProfileId);
Optional<YourFeatureName> findById(UUID id); // for idempotent create
```

### Step 5 — Create DTO Records

See references for templates (CreateRequest, UpdateRequest, Response). Key patterns:
- [ ] Use `record` (not class)
- [ ] CreateRequest has optional `UUID id` for idempotent create
- [ ] Response has `fromEntity()` factory method
- [ ] Use `camelCase` (DTOs) vs `snake_case` (database)

### Step 6 — Create Service Class

See references for full example. Key patterns:
- [ ] Constructor injection only
- [ ] Idempotent-create: check `findById(id)` before inserting
- [ ] Ownership checks: `findByIdAndChildProfile_Id(id, childProfileId)`
- [ ] Soft-delete: filter queries with `deletedAt == null`, set `deletedAt` on delete
- [ ] Throw `ResponseStatusException` with correct status (404/403/409/400)
- [ ] Update `updatedAt` on mutations

Checklist:
- [ ] All methods pass `childProfileId` for ownership validation
- [ ] All error cases throw ResponseStatusException with correct HttpStatus
- [ ] No Spring/HTTP logic in service (pure business logic)

### Step 7 — Create REST Controller

See references for full example. Key endpoints:
```
GET    /api/your-feature-names
GET    /api/your-feature-names/{id}
POST   /api/your-feature-names          (201 Created)
PATCH  /api/your-feature-names/{id}    (200 OK)
DELETE /api/your-feature-names/{id}    (204 No Content)
```

Checklist:
- [ ] Constructor injection only
- [ ] Extract `childProfileId` from `Authentication.getName()`
- [ ] Correct HTTP status codes (201 create, 204 delete, 200 update/read)
- [ ] Never expose entities (use DTOs only)

### Step 8 — Update SecurityConfig

Add to `securityFilterChain()` in `app/server/src/main/java/com/capybee/server/config/SecurityConfig.java`:
```java
.requestMatchers("/api/your-feature-names/**").authenticated()
```

### Step 9 — Write Unit Tests

Create `app/server/src/test/java/com/capybee/server/service/YourFeatureNameServiceTest.java`.

Follow `service-unit-test-scaffold` skill. Key scenarios:
- [ ] Happy path (CRUD operations)
- [ ] Idempotent create: duplicate id returns existing record
- [ ] Ownership checks: different child cannot access
- [ ] Soft-delete: deleted records don't appear
- [ ] All error cases (404, 403, 400)

Run `mvn verify` to confirm 70% JaCoCo coverage gate passes.

### Step 10 — Frontend Integration (if offline-capable)

If offline mutations needed, wire through `offline-queue` following existing patterns for check-ins/missions/friendships.

## Full-Stack Checklist

- [ ] Flyway migration created + tested locally
- [ ] JPA entity with ownership + soft-delete support
- [ ] Repository with ownership-check + soft-delete queries
- [ ] DTOs (Create/Update/Response) with factories
- [ ] Service with idempotent-create, ownership checks, soft-delete
- [ ] REST controller with correct status codes
- [ ] SecurityConfig updated
- [ ] Unit tests with 70% coverage (`mvn verify` passes)
- [ ] All compile, zero errors
- [ ] Local smoke test (curl all endpoints)
- [ ] Frontend integration (if applicable)

## References

See `references/` for:
