---
name: new-crud-feature
description: "Use when adding a new child-owned CRUD feature or scaffolding a domain entity in CapyBee, from migration through service, REST API, tests, and offline frontend integration. Not for isolated UI or copy changes."
---

# New CRUD Feature

Use for a new child-owned domain, not isolated changes. Follow the [overview](../../instructions/project-overview.instructions.md) and relevant specification before designing behavior.

## Procedure
1. Define the minimum fields, constraints, and `ChildProfile` ownership. Choose soft-delete only where the feature requires it; inspect a nearby domain implementation instead of assuming reference APIs exist.
2. Create the schema change with the [Flyway skill](../flyway-migration/SKILL.md). Add the matching UUID entity, relationship, timestamps, and necessary indexes.
3. Add repository methods scoped to the owning profile and active records where applicable. Use actual entity property names for derived queries.
4. Add request/response records and mappings, following the [API contract](../../instructions/api.instructions.md); support optional client IDs for offline retry-safe creates.
5. Implement a constructor-injected service using [Java standards](../../instructions/java-server.instructions.md). Validate ownership and transitions; deduplicate creates only after confirming ownership, preserve timestamps, and use existing error-handling conventions.
6. Add a DTO-based controller and protect routes in `SecurityConfig`. Resolve the authenticated user's child profile using a neighboring controller's actual flow; do not assume the authentication name is a profile UUID.
7. Add service coverage with the [service-test skill](../service-unit-test-scaffold/SKILL.md): success, retries, foreign ownership, validation/errors, and soft-delete when applicable.
8. Integrate authenticated frontend mutations through `queueStore.ts` / `syncEngine.ts`, with optimistic updates and EN/PL copy following [UI standards](../../instructions/ui.instructions.md). Update both sides of the contract together.

## Verification
- Run focused tests while implementing; run applicable final backend/frontend gates and static sync from the [build guide](../../instructions/build-workflow.instructions.md).
- On a disposable database, verify migration/startup and affected authenticated CRUD flows, including retries and denial of access to another child's records. Report unavailable runtime checks explicitly.
- Confirm migration/entity alignment, DTO-only responses, endpoint protection, ownership-safe idempotency, appropriate delete behavior, localized copy, offline sync, and passing tests before completion.

## Reference
Open [full-stack integration patterns](./references/full-stack-patterns.md) when tracing cross-layer data flow. Examples are illustrative; current implementations and scoped rules determine actual APIs and ownership validation.
