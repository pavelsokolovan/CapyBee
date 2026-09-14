---
# Mockito Patterns — CapyBee Service Testing Reference

This reference covers Mockito syntax and patterns used in CapyBee service unit tests.

## Setup & Initialization

### Extension + Annotations
```java
@ExtendWith(MockitoExtension.class)
class YourServiceTest {

  @Mock
  private YourRepository repository;

  @InjectMocks
  private YourService service; // Gets repository injected via constructor
}
```

**Key points:**
- `@ExtendWith(MockitoExtension.class)` enables Mockito annotations (replaces `@RunWith(MockitoRunner.class)`)
- `@Mock` creates a mock instance
- `@InjectMocks` creates a real instance with mocks injected (constructor injection)

### Avoid Spring Context
```java
// ❌ DON'T do this
@SpringBootTest
class YourServiceTest { /* slow, pulls in whole Spring */ }

// ✅ DO this
@ExtendWith(MockitoExtension.class)
class YourServiceTest { /* fast, unit test only */ }
```

## Mocking Repository Methods

### when().thenReturn()
```java
// Mock a method to return a value
@Test
void test() {
  var entity = new YourEntity(id, profile, "value");
  
  when(repository.findById(id))
    .thenReturn(Optional.of(entity));

  var result = service.getById(id);
  
  assertEquals(id, result.id());
}
```

**Key:** `when(mock.method(arg)).thenReturn(value)` sets up the mock.

### when().thenThrow()
```java
// Mock a method to throw an exception
@Test
void test() {
  when(repository.findById(id))
    .thenThrow(new RuntimeException("Database error"));

  assertThrows(RuntimeException.class, () ->
    service.getById(id)
  );
}
```

**Use:** Simulate database failures, constraint violations, etc.

### when().thenAnswer()
```java
// Use a custom function to determine return value
@Test
void test() {
  when(repository.save(any(YourEntity.class)))
    .thenAnswer(invocation -> invocation.getArgument(0));
    // Returns whatever entity was passed in

  var entity = new YourEntity(id, profile, "test");
  var saved = service.create(entity);
  
  assertEquals(id, saved.id());
}
```

**Use:** When the mock should return the same object passed to it, or compute a return based on arguments.

### Chaining Multiple Returns
```java
// Return different values on consecutive calls
@Test
void test() {
  when(repository.findById(id))
    .thenReturn(Optional.of(entity1))
    .thenReturn(Optional.of(entity2))
    .thenReturn(Optional.empty());

  assertEquals(id1, service.getById(id).id()); // First call
  assertEquals(id2, service.getById(id).id()); // Second call
  assertThrows(Exception.class, () -> service.getById(id)); // Third call
}
```

**Use:** Simulate state changes across multiple operations.

## Argument Matchers

### Exact Arguments
```java
when(repository.findById(entityId))
  .thenReturn(Optional.of(entity));

// Only matches exactly entityId
```

### any()
```java
// Match any argument of a type
when(repository.save(any(YourEntity.class)))
  .thenAnswer(i -> i.getArgument(0));

// Matches any YourEntity passed to save()
```

### anyString(), anyInt(), anyUUID(), etc.
```java
when(repository.findByFieldName(anyString()))
  .thenReturn(List.of());

when(repository.findById(any(UUID.class)))
  .thenReturn(Optional.empty());
```

### argThat() — Custom Matching
```java
// Match arguments that satisfy a condition
when(repository.save(argThat(e ->
  e.getFieldName().equals("Expected Value")
)))
  .thenReturn(entity);

// Or with a lambda
when(repository.findByFieldName(argThat(s ->
  s.startsWith("Prefix")
)))
  .thenReturn(List.of());
```

**Use:** Match based on field values or predicates.

### isNull() / notNull()
```java
when(repository.delete(isNull()))
  .thenThrow(new IllegalArgumentException("ID cannot be null"));

when(repository.save(notNull()))
  .thenAnswer(i -> i.getArgument(0));
```

## Verification (Checking Mock Calls)

### verify() — Was the Mock Called?
```java
@Test
void test() {
  service.create(childProfileId, request);

  verify(repository).save(any(YourEntity.class));
  // Passes if save() was called at least once
}
```

### verify with times()
```java
@Test
void test() {
  service.create(childProfileId, request1);
  service.create(childProfileId, request2);

  verify(repository, times(2)).save(any(YourEntity.class));
  // Passes if save() was called exactly 2 times

  verify(repository, atLeastOnce()).save(any());
  // Passes if save() was called 1+ times

  verify(repository, never()).delete(any());
  // Passes if delete() was never called
}
```

### verify with argThat() — Verify Call Arguments
```java
@Test
void test() {
  service.updateEntity(entityId, childProfileId, updateRequest);

  verify(repository).save(argThat(e ->
    e.getFieldName().equals("Updated Value") &&
    e.getUpdatedAt() != null
  ));
  // Passes if save() was called with an entity matching these conditions
}
```

### verifyNoMoreInteractions()
```java
@Test
void test() {
  var result = service.getById(entityId, childProfileId);

  verify(repository).findByIdAndChildProfile_Id(entityId, childProfileId);
  verifyNoMoreInteractions(repository);
  // Passes if ONLY findByIdAndChildProfile_Id was called, nothing else
}
```

**Use:** Ensure no unexpected additional calls to the mock.

## Common Test Patterns

### Happy Path with Arrangement
```java
@Test
void createEntity_success_returnsResponse() {
  // Arrange
  var request = new CreateYourEntityRequest(null, "Test Field");
  when(repository.save(any(YourEntity.class)))
    .thenAnswer(i -> i.getArgument(0)); // Return the entity being saved

  // Act
  var response = service.createYourEntity(childProfileId, request);

  // Assert
  assertNotNull(response.id());
  assertEquals("Test Field", response.fieldName());
  verify(repository, times(1)).save(any(YourEntity.class));
}
```

### Error Path — Not Found
```java
@Test
void getEntity_notFound_throws404() {
  // Arrange
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.empty());

  // Act & Assert
  var exception = assertThrows(ResponseStatusException.class, () ->
    service.getEntity(entityId, childProfileId)
  );

  assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
}
```

### Soft-Delete Logic
```java
@Test
void getEntity_deleted_throws404() {
  // Arrange
  var deletedEntity = new YourEntity(entityId, profile, "test");
  deletedEntity.setDeletedAt(Instant.now());

  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.of(deletedEntity));

  // Act & Assert
  var exception = assertThrows(ResponseStatusException.class, () ->
    service.getEntity(entityId, childProfileId)
  );

  assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
}
```

### Ownership Check
```java
@Test
void getEntity_differentChild_throws404() {
  // Arrange
  var otherChildId = UUID.randomUUID(); // Not childProfileId

  when(repository.findByIdAndChildProfile_Id(entityId, otherChildId))
    .thenReturn(Optional.empty()); // Different child can't access

  // Act & Assert
  var exception = assertThrows(ResponseStatusException.class, () ->
    service.getEntity(entityId, otherChildId)
  );

  assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
}
```

### Idempotent Create
```java
@Test
void createEntity_duplicateId_deduplicates() {
  // Arrange
  var clientId = UUID.randomUUID();
  var existing = new YourEntity(clientId, profile, "Original");
  
  when(repository.findById(clientId))
    .thenReturn(Optional.of(existing));

  var request = new CreateYourEntityRequest(clientId, "Different");

  // Act
  var response = service.createYourEntity(childProfileId, request);

  // Assert
  assertEquals(clientId, response.id());
  assertEquals("Original", response.fieldName()); // Returns existing, not request value
  verify(repository, never()).save(any()); // No save on dedup
}
```

## Static Imports (For Readability)

```java
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```

These allow:
```java
// Instead of
org.mockito.Mockito.when(repository.findById(id))...
org.mockito.Mockito.verify(repository).save(...);

// You write
when(repository.findById(id))...
verify(repository).save(...);
```

## Common Pitfalls

### Pitfall 1: Forgetting to Set Up Mock Behavior
```java
// ❌ WRONG
@Test
void test() {
  // No when() setup!
  var result = service.getById(entityId, childProfileId);
  // repository.findByIdAndChildProfile_Id() returns mock.empty() by default
  // Service throws 404, test fails unexpectedly
}

// ✅ CORRECT
@Test
void test() {
  when(repository.findByIdAndChildProfile_Id(entityId, childProfileId))
    .thenReturn(Optional.of(entity));
  var result = service.getById(entityId, childProfileId);
  assertEquals(entityId, result.id());
}
```

### Pitfall 2: Using Real Objects as Arguments
```java
// ❌ WRONG
when(repository.save(entity)).thenReturn(entity);
// If service creates a NEW entity object, mock won't match

// ✅ CORRECT
when(repository.save(any(YourEntity.class))).thenReturn(entity);
// Matches any YourEntity passed to save()
```

### Pitfall 3: Verifying Without Mocking
```java
// ❌ WRONG
@Test
void test() {
  service.create(childProfileId, request);
  verify(repository).save(any()); // Fails: repository is not a mock if not @Mock
}

// ✅ CORRECT
@Test
void test() {
  service.create(childProfileId, request);
  verify(repository).save(any()); // Passes: repository is @Mock
}
```

### Pitfall 4: Mixing Stubbing & Verification Incorrectly
```java
// ❌ WRONG - stubbing after action
@Test
void test() {
  service.create(childProfileId, request);
  when(repository.save(any())).thenReturn(entity); // Too late!
}

// ✅ CORRECT - stub before action
@Test
void test() {
  when(repository.save(any())).thenReturn(entity);
  service.create(childProfileId, request);
  verify(repository).save(any());
}
```

## Advanced: Spy vs. Mock

### @Mock — Complete Fake
```java
@Mock
private YourRepository repository;

// All methods return mock defaults (null, empty, false, etc.)
// Useful for isolation testing
```

### @Spy — Partial Fake (Rarely Used)
```java
@Spy
private YourRepository repository = new YourRepository();

// Calls real methods unless stubbed with when()
// Useful when testing one method that calls another
```

**CapyBee uses:** Always `@Mock` for repositories. Never `@Spy`.

## Reference

- [Mockito Docs](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [JUnit 5 Docs](https://junit.org/junit5/)
