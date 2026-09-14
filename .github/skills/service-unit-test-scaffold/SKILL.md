---
name: service-unit-test-scaffold
description: Scaffold Mockito-based unit tests for Spring Boot services in CapyBee, achieving 70% line-coverage gate on service.* package. Use this skill when adding tests for a new service class or expanding coverage of existing services. Covers the Mockito @Mock/@InjectMocks pattern, ownership checks, soft-delete logic, idempotent-create, and error branches. Triggers on "add service tests", "write unit tests", "test service", "increase test coverage", "jacoco coverage".
---

# Service Unit Test Scaffold

This skill guides creation of Mockito-based unit tests for CapyBee service classes, following the patterns established in `CheckInServiceTest`, `ChildProfileServiceTest`, `MissionServiceCompletionTest`, and enforcing the 70% line-coverage gate on `com.capybee.server.service.*`.

## When to Use

- Writing unit tests for a new service class
- Adding test coverage to an existing service
- Achieving the 70% line-coverage gate on service methods
- Testing idempotent-create, ownership checks, soft-delete, error paths

## Key Principles

1. **Mockito, not Spring** — Use `@ExtendWith(MockitoExtension.class)`, not `@SpringBootTest`. Faster, focuses on service logic only.
2. **Mocks for Collaborators** — Repository, other services are `@Mock`; service under test is `@InjectMocks`.
3. **Test All Error Branches** — 404 not found, 403 ownership denied, 409 conflict (duplicate), 400 validation errors.
4. **Coverage Gate** — `mvn verify` must pass with 70% line coverage on `service.*` package. Failing tests fail the gate.
5. **No Integration Tests Here** — If testing needs Spring context/database/security, that's an integration test (different tool).

## Test File Location

**Pattern:** `app/server/src/test/java/com/capybee/server/service/YourServiceTest.java`

Mirror the package structure of the service under test.

## Test Anatomy

**File location:** `app/server/src/test/java/com/capybee/server/service/YourServiceTest.java`

**Setup (Mockito pattern):**
```java
@ExtendWith(MockitoExtension.class)  // NOT @SpringBootTest
class YourServiceTest {
  @Mock private YourEntityRepository repository;
  @InjectMocks private YourService service;
  
  @BeforeEach
  void setup() {
    childProfileId = UUID.randomUUID();
    entityId = UUID.randomUUID();
    entity = new YourEntity(entityId, profile, "Test");
  }
}
```

**Key principle:** Mock repositories, test service logic only.

## Test Scenarios (9 Core Cases)

**Naming:** `methodName_scenario_expectedResult()`

### 1. Happy Path — Create
```java
@Test
void createYourEntity_success_returnsResponse() {
  var request = new CreateYourEntityRequest(null, "Test");
  when(repository.save(any(YourEntity.class))).thenAnswer(i -> i.getArgument(0));
  var response = service.createYourEntity(childProfileId, request);
  assertNotNull(response.id());
  verify(repository).save(any());
}
```

### 2. Idempotent Create — Duplicate ID
```java
@Test
void createYourEntity_clientSuppliedId_deduplicatesOnRetry() {
  var existing = new YourEntity(entityId, profile, "Existing");
  when(repository.findById(entityId)).thenReturn(Optional.of(existing));
  var response = service.createYourEntity(childProfileId, request);
  verify(repository, never()).save(any());
}
```

### 3. Get — Success
```java
@Test
void getYourEntity_found_returnsResponse() {
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.of(entity));
  var response = service.getYourEntity(entityId, childProfileId);
  assertEquals(entityId, response.id());
}
```

### 4. Get — Not Found (404)
```java
@Test
void getYourEntity_notFound_throws404() {
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.empty());
  assertThrows(ResponseStatusException.class, () ->
    service.getYourEntity(entityId, childProfileId)
  );
}
```

### 5. Soft-Delete — Deleted Records Excluded
```java
@Test
void getYourEntity_deleted_throws404() {
  entity.setDeletedAt(Instant.now());
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.of(entity));
  assertThrows(ResponseStatusException.class, () ->
    service.getYourEntity(entityId, childProfileId)
  );
}
```

### 6. Ownership Check — Different Child
```java
@Test
void getYourEntity_differentChild_throws404() {
  var otherChildId = UUID.randomUUID();
  when(repository.findByIdAndChildProfile_Id(entityId, otherChildId))
    .thenReturn(Optional.empty());
  assertThrows(ResponseStatusException.class, () ->
    service.getYourEntity(entityId, otherChildId)
  );
}
```

### 7. Update — Success
```java
@Test
void updateYourEntity_success_returnsUpdated() {
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.of(entity));
  when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
  var response = service.updateYourEntity(entityId, childProfileId, request);
  verify(repository).save(any());
}
```

### 8. Delete — Soft-Delete
```java
@Test
void deleteYourEntity_success_setsDeletedAt() {
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.of(entity));
  service.deleteYourEntity(entityId, childProfileId);
  verify(repository).save(argThat(e -> e.getDeletedAt() != null));
}
```

### 9. List All — Active Records Only
```java
@Test
void getYourEntities_returnsOnlyActive() {
  when(repository.findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(childProfileId))
    .thenReturn(List.of(entity));
  var responses = service.getYourEntities(childProfileId);
  assertEquals(1, responses.size());
}
```

## Coverage & Running Tests

**Run tests locally:**
```bash
cd app/server
mvn test
```

**Check JaCoCo coverage gate:**
```bash
mvn verify
```

Expected: 70% line coverage on `com.capybee.server.service.*` package.

If gate fails, view coverage report at `app/server/target/site/jacoco/index.html` to find uncovered lines.

## Checklist — Service Test Coverage

For a typical CRUD service:
- [ ] All public methods have at least one test
- [ ] Happy path (success cases)
- [ ] Idempotent create (duplicate id handling)
- [ ] Soft-delete (deleted records filtered)
- [ ] Ownership checks (different child access denied)
- [ ] All error branches (404, 403, 400 ResponseStatusException)
- [ ] List queries return only active records
- [ ] Timestamps updated correctly on mutations
- [ ] Service calls `save()` expected number of times (verify)
- [ ] `mvn verify` passes with ≥70% line coverage

## Common Pitfalls

### Pitfall 1: Forgetting to Mock Repository
```java
// ❌ WRONG: repository method not mocked
var response = service.get(id); // Fails or returns unexpected result

// ✅ CORRECT: explicitly mock
when(repository.findById(id)).thenReturn(Optional.of(entity));
var response = service.get(id);
```

### Pitfall 2: Testing Controller Logic in Service Tests
Test service logic only, not @RequestMapping or @PathVariable. Controllers are integration tests.

### Pitfall 3: Not Testing Error Paths
Test happy path AND all error cases (404, 403, 400 ResponseStatusException).

### Pitfall 4: No Centralized Test Fixtures
```java
// ✅ CORRECT: use @BeforeEach
@BeforeEach
void setup() {
  childProfileId = UUID.randomUUID();
  entityId = UUID.randomUUID();
}
```

## References

See `references/` for:
- `mockito-patterns.md` — detailed Mockito syntax
- `jacoco-troubleshooting.md` — fixing coverage gate failures
- `test-data-builders.md` — reusable entity builders
