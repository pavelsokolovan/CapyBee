---
name: service-unit-test-scaffold
description: "Use when adding Java service tests, expanding Spring service unit coverage, or troubleshooting JaCoCo coverage in CapyBee. Covers Mockito, ownership checks, idempotent retries, soft-delete, and error branches; not frontend tests."
---

# Service Unit Test Scaffold

Use for Java service tests and coverage work, not Spring-context integration or frontend tests. Test obligations live in [Java standards](../../instructions/java-server.instructions.md).

## Procedure
1. Read the service's actual branches and its nearest tests. Prefer extending an existing test file; mirror the service package under `app/server/src/test/java` if a new file is necessary.
2. Follow `CheckInServiceTest`, `ChildProfileServiceTest`, or `MissionServiceCompletionTest`: `@ExtendWith(MockitoExtension.class)`, `@Mock` collaborators, `@InjectMocks` service. No Spring context for these unit tests.
3. Reuse existing fixtures or builders and actual DTO/entity constructors. Stub only methods exercised by the scenario; use `thenAnswer` when a saved entity should be returned unchanged.
4. Test public behavior: assert returned fields, persisted changes, and exception status codes, not only mock invocation counts. Use `methodName_scenario_expectedResult` names.
5. Cover success, every validation/error branch, empty and boundary inputs, ownership denial, and duplicate-ID retries. Include a foreign-owned duplicate ID case; it must not expose or update another child's record.
6. Where supported, cover active-only lists, deleted-record exclusion, deletion timestamps, and update timestamps. Do not invent soft-delete behavior absent from the service.
7. Run `mvn -Dtest=YourServiceTest test` in `app/server`; fix the touched tests before expanding scope. Then run the required backend coverage gate in the [build guide](../../instructions/build-workflow.instructions.md).

## Coverage Review
- Inspect `app/server/target/site/jacoco/index.html` for uncovered service lines; add meaningful missing scenarios, never lower the gate.
- Keep tests deterministic and independent; mock collaborators, not the service under test.
- Confirm tests prove ownership, idempotency, error responses, and state changes introduced by the implementation.

## References
Open only what the current task needs:
- [Mockito patterns](./references/mockito-patterns.md): setup, stubbing, argument matchers, and assertions.
- [Test data builders](./references/test-data-builders.md): fixture patterns; prefer actual existing helpers over copying placeholders.
- [JaCoCo troubleshooting](./references/jacoco-troubleshooting.md): use only when coverage or instrumentation fails; check current `pom.xml` before changing plugin settings.
