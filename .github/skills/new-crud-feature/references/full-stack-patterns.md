---
# Full-Stack Integration Patterns — New CRUD Feature Skill

This reference documents how layers integrate when implementing a new CRUD feature in CapyBee.

## Request Flow — Create Operation

When a client creates a record (e.g., new check-in):

```
1. Frontend (React)
   └─ submit form with data
   └─ HTTP POST /api/check-ins { mood, note, id (optional for offline) }

2. Controller (CheckInController)
   ├─ extract childProfileId from Authentication
   ├─ parse request body into CreateCheckInRequest DTO
   ├─ pass to service

3. Service (CheckInService)
   ├─ if request.id is present:
   │  └─ check repository.findById(id)
   │     ├─ if found: return existing (dedup)
   │     └─ if not found: create new with that id
   ├─ else: generate new UUID
   ├─ create entity with childProfile, fields, timestamps
   ├─ persist via repository.save()
   ├─ return DTO response

4. Repository (CheckInRepository)
   ├─ Hibernate handles INSERT INTO check_ins
   ├─ JDBC driver sends to Postgres

5. Database (Postgres)
   ├─ writes row to check_ins table
   └─ returns inserted row (with auto-set timestamps)

6. Frontend receives HTTP 201 Created with response DTO
   └─ display new item in UI
```

## Data Flow — Get Operation (with Ownership Check)

```
1. Frontend
   └─ HTTP GET /api/check-ins/12345

2. Controller
   ├─ extract childProfileId from Authentication
   ├─ pass id + childProfileId to service

3. Service (Ownership Check)
   ├─ call repository.findByIdAndChildProfile_Id(id, childProfileId)
   │  └─ this query ensures only matching records are returned
   ├─ filter out soft-deleted (deletedAt == null)
   │  ├─ if passes filter: convert to DTO
   │  └─ if filtered out or not found: throw 404

4. Repository
   ├─ Hibernate translates method name to SQL:
   │  └─ SELECT * FROM check_ins
   │     WHERE id = ? AND child_profile_id = ? AND deleted_at IS NULL
   ├─ Returns Optional<CheckIn>

5. Frontend receives 200 OK or 404 NOT FOUND
```

## Critical Patterns

### Pattern 1: Idempotent Create (Offline Safety)

**Why:** Client-generated IDs let offline mutations retry safely without creating duplicates.

**Implementation:**
```
Client submits: POST /api/check-ins {
  "id": "550e8400-e29b-41d4-a716-446655440001", ← Optional
  "mood": "okay"
}

Service logic:
1. If request.id is not null:
   └─ query.findById(request.id)
   ├─ if exists: return it (dedup, don't re-insert)
   └─ if not exists: create new with that id
2. Else (request.id is null):
   └─ generate new UUID
   └─ create and insert
```

**Test this:**
```java
@Test
void createCheckIn_clientSuppliedId_deduplicatesOnRetry() {
  var clientId = UUID.randomUUID();
  var existing = new CheckInTestBuilder()
    .withId(clientId)
    .withMood("heavy")
    .build();

  when(repository.findById(clientId))
    .thenReturn(Optional.of(existing));

  var request = new CreateCheckInRequest(clientId, "good"); // Different mood
  var response = service.createCheckIn(childProfileId, request);

  assertEquals(clientId, response.id());
  assertEquals("heavy", response.mood()); // Returns original, not request value
  verify(repository, never()).save(any()); // No insert
}
```

### Pattern 2: Soft-Delete (Undo-Safe)

**Why:** Set deletedAt instead of removing the row; allows undo, audit trail, recovery.

**Implementation in Service:**
```java
public void deleteCheckIn(UUID id, UUID childProfileId) {
  var entity = repository.findByIdAndChildProfile_Id(id, childProfileId)
    .filter(e -> e.getDeletedAt() == null)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ...));

  entity.setDeletedAt(Instant.now()); // ← Mark as deleted
  repository.save(entity);
}
```

**Filter in queries:**
```java
// Repository: List active records only
List<CheckIn> findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID childProfileId);

// Service: Filter in-memory as fallback
entity.getDeletedAt() == null
```

**Test this:**
```java
@Test
void deleteCheckIn_success_setsDeletedAt() {
  when(repository.findByIdAndChildProfile_Id(id, childProfileId))
    .thenReturn(Optional.of(entity));
  when(repository.save(any(CheckIn.class)))
    .thenAnswer(i -> i.getArgument(0));

  service.deleteCheckIn(id, childProfileId);

  verify(repository).save(argThat(e -> e.getDeletedAt() != null));
}

@Test
void getCheckIn_deleted_throws404() {
  var deleted = new CheckInTestBuilder().deleted().build();
  when(repository.findByIdAndChildProfile_Id(id, childProfileId))
    .thenReturn(Optional.of(deleted));

  assertThrows(ResponseStatusException.class, () ->
    service.getCheckIn(id, childProfileId)
  );
}
```

### Pattern 3: Ownership Check (Privacy)

**Why:** A child cannot access another child's records. Every query must filter by childProfileId.

**Implementation:**
```java
// ❌ WRONG: No ownership check
public CheckInResponse getCheckIn(UUID id) {
  var entity = repository.findById(id)
    .orElseThrow(...); // Any child can fetch any id
  return CheckInResponse.fromEntity(entity);
}

// ✅ CORRECT: Ownership enforced
public CheckInResponse getCheckIn(UUID id, UUID childProfileId) {
  var entity = repository.findByIdAndChildProfile_Id(id, childProfileId)
    .filter(e -> e.getDeletedAt() == null)
    .orElseThrow(...); // Only returns if child owns it
  return CheckInResponse.fromEntity(entity);
}
```

**Repository method:** Must include `childProfileId` in WHERE clause.
```java
Optional<CheckIn> findByIdAndChildProfile_Id(UUID id, UUID childProfileId);
```

**SQL generated:**
```sql
SELECT * FROM check_ins WHERE id = ? AND child_profile_id = ?
```

**Test this:**
```java
@Test
void getCheckIn_differentChild_throws404() {
  var otherChildId = UUID.randomUUID();

  when(repository.findByIdAndChildProfile_Id(id, otherChildId))
    .thenReturn(Optional.empty()); // Simulate different child

  assertThrows(ResponseStatusException.class, () ->
    service.getCheckIn(id, otherChildId)
  );
}
```

### Pattern 4: Audit Timestamps (createdAt, updatedAt)

**Why:** Maintain audit trail; required for sorting (newest first), debugging.

**Implementation:**
```java
// In entity constructor
public CheckIn(UUID id, ChildProfile profile, String mood) {
  this.id = id;
  this.childProfile = profile;
  this.mood = mood;
  this.createdAt = Instant.now(); // Set once
  this.updatedAt = Instant.now();
}

// On update
entity.setMood("updated mood");
entity.setUpdatedAt(Instant.now()); // Always update
repository.save(entity);
```

**In migration:**
```sql
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
```

**In queries:**
```java
// Default sort: newest first
List<CheckIn> findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID childProfileId);
```

### Pattern 5: DTO Contracts (No Entity Exposure)

**Why:** Controllers and clients never see JPA entities. DTOs provide a stable contract independent of schema.

**Entity → DTO conversion:**
```java
public record CheckInResponse(
  UUID id,
  String mood,
  String note,
  Instant createdAt,
  Instant updatedAt
) {
  public static CheckInResponse fromEntity(CheckIn entity) {
    return new CheckInResponse(
      entity.getId(),
      entity.getMood(),
      entity.getNote(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }
}
```

**In service:**
```java
public List<CheckInResponse> getCheckIns(UUID childProfileId) {
  return repository.findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(childProfileId)
    .stream()
    .map(CheckInResponse::fromEntity) // Convert all to DTO
    .toList();
}
```

**In controller:**
```java
@GetMapping
public ResponseEntity<List<CheckInResponse>> getAll(Authentication auth) {
  UUID childProfileId = UUID.fromString(auth.getName());
  return ResponseEntity.ok(service.getCheckIns(childProfileId));
  // ↑ Returns DTOs, never entities
}
```

## Common Mistakes to Avoid

### Mistake 1: Missing Ownership Check
```java
// ❌ WRONG
public CheckInResponse getCheckIn(UUID id) {
  return service.getCheckIn(id); // No childProfileId parameter
}

// ✅ CORRECT
public CheckInResponse getCheckIn(UUID id, UUID childProfileId) {
  return service.getCheckIn(id, childProfileId);
}
```

### Mistake 2: Exposing Entities from Controller
```java
// ❌ WRONG
@GetMapping("/{id}")
public ResponseEntity<CheckIn> getCheckIn(@PathVariable UUID id) {
  return ResponseEntity.ok(repository.findById(id).orElse(null)); // Entity leak!
}

// ✅ CORRECT
@GetMapping("/{id}")
public ResponseEntity<CheckInResponse> getCheckIn(@PathVariable UUID id) {
  return ResponseEntity.ok(service.getCheckIn(id, childProfileId)); // DTO
}
```

### Mistake 3: Forgetting Soft-Delete in Queries
```java
// ❌ WRONG
public List<CheckInResponse> getCheckIns(UUID childProfileId) {
  return repository.findByChildProfile_Id(childProfileId) // Includes deleted!
    .stream()
    .map(CheckInResponse::fromEntity)
    .toList();
}

// ✅ CORRECT
public List<CheckInResponse> getCheckIns(UUID childProfileId) {
  return repository.findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(childProfileId)
    .stream()
    .map(CheckInResponse::fromEntity)
    .toList();
}
```

### Mistake 4: Not Testing Error Paths
```java
// ❌ WRONG: Only happy path
@Test
void getCheckIn_success() { /* ... */ }

// ✅ CORRECT: All paths
@Test
void getCheckIn_success() { /* ... */ }

@Test
void getCheckIn_notFound_throws404() { /* ... */ }

@Test
void getCheckIn_deleted_throws404() { /* ... */ }

@Test
void getCheckIn_ownershipViolation_throws404() { /* ... */ }
```

## Integration Checklist

Before marking a feature complete:

- [ ] Database migration applied and verified locally
- [ ] JPA entity created with audit timestamps and soft-delete support
- [ ] Repository interface with ownership-check methods (findByIdAndChildProfile_Id)
- [ ] Service class with idempotent-create, ownership checks, 404/403 handling
- [ ] DTO records with fromEntity() factories
- [ ] Controller with proper HTTP status codes (201/204/200)
- [ ] SecurityConfig updated with new route in authenticated requestMatchers
- [ ] Unit tests cover happy path + all error branches
- [ ] JaCoCo coverage gate passes (mvn verify)
- [ ] All Java files compile with zero errors
- [ ] No ResponseStatusException without childProfileId check in service layer
- [ ] No entity objects exposed from controller (only DTOs)
- [ ] All queries filter soft-deleted records (deletedAt IS NULL)
- [ ] If offline-capable: frontend integrated with offline queue + syncEngine

## Example: Real CapyBee Feature (CheckIn)

See `app/server/src/main/java/com/capybee/server/` for the reference implementation:

- Entity: `domain/CheckIn.java`
- Repository: `repository/CheckInRepository.java`
- Service: `service/CheckInService.java`
- Controller: `web/CheckInController.java`
- DTOs: `web/CreateCheckInRequest.java`, `web/CheckInResponse.java`

Tests: `app/server/src/test/java/com/capybee/server/service/CheckInServiceTest.java`
