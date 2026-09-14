---
# Test Data Builders — CapyBee Service Testing Reference

This reference provides reusable test data builders and factory patterns for CapyBee entity tests, reducing boilerplate in test files.

## Builder Pattern

Instead of constructing entities inline in every test, use a builder class.

### Entity Builder Template

**File:** `app/server/src/test/java/com/capybee/server/domain/YourEntityTestBuilder.java`

```java
package com.capybee.server.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Test builder for YourEntity. Use instead of constructor in tests.
 * Example: new YourEntityTestBuilder().withFieldName("test").build();
 */
public class YourEntityTestBuilder {

  private UUID id = UUID.randomUUID();
  private ChildProfile childProfile;
  private String fieldName = "default field name";
  private Instant createdAt = Instant.now();
  private Instant updatedAt = Instant.now();
  private Instant deletedAt = null;

  public YourEntityTestBuilder() {
    // Default child profile
    this.childProfile = new ChildProfile();
    this.childProfile.setId(UUID.randomUUID());
  }

  public YourEntityTestBuilder withId(UUID id) {
    this.id = id;
    return this;
  }

  public YourEntityTestBuilder withChildProfile(ChildProfile profile) {
    this.childProfile = profile;
    return this;
  }

  public YourEntityTestBuilder withFieldName(String name) {
    this.fieldName = name;
    return this;
  }

  public YourEntityTestBuilder withCreatedAt(Instant timestamp) {
    this.createdAt = timestamp;
    return this;
  }

  public YourEntityTestBuilder deleted() {
    this.deletedAt = Instant.now();
    return this;
  }

  public YourEntityTestBuilder withDeletedAt(Instant timestamp) {
    this.deletedAt = timestamp;
    return this;
  }

  public YourEntity build() {
    var entity = new YourEntity(id, childProfile, fieldName);
    entity.setCreatedAt(createdAt);
    entity.setUpdatedAt(updatedAt);
    entity.setDeletedAt(deletedAt);
    return entity;
  }
}
```

### Using the Builder

**Before (verbose):**
```java
@Test
void test() {
  var profile = new ChildProfile();
  profile.setId(childProfileId);

  var entity1 = new YourEntity(UUID.randomUUID(), profile, "value 1");
  entity1.setCreatedAt(Instant.now());
  entity1.setUpdatedAt(Instant.now());

  var entity2 = new YourEntity(UUID.randomUUID(), profile, "value 2");
  entity2.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
  entity2.setUpdatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
  entity2.setDeletedAt(Instant.now());
}
```

**After (concise):**
```java
@Test
void test() {
  var profile = new ChildProfile();
  profile.setId(childProfileId);

  var entity1 = new YourEntityTestBuilder()
    .withChildProfile(profile)
    .withFieldName("value 1")
    .build();

  var entity2 = new YourEntityTestBuilder()
    .withChildProfile(profile)
    .withFieldName("value 2")
    .deleted() // convenience method
    .build();
}
```

## Common Builders (CapyBee Entities)

### ChildProfile Builder
```java
public class ChildProfileTestBuilder {
  private UUID id = UUID.randomUUID();
  private UUID userId = UUID.randomUUID();
  private String nickname = "Test Child";
  private Integer birthYear = 2015;
  private String locale = "en";

  public ChildProfileTestBuilder withId(UUID id) {
    this.id = id;
    return this;
  }

  public ChildProfileTestBuilder withNickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  public ChildProfile build() {
    var profile = new ChildProfile();
    profile.setId(id);
    profile.setUserId(userId);
    profile.setNickname(nickname);
    profile.setBirthYear(birthYear);
    profile.setLocale(locale);
    profile.setCreatedAt(Instant.now());
    profile.setUpdatedAt(Instant.now());
    return profile;
  }
}
```

### CheckIn Builder
```java
public class CheckInTestBuilder {
  private UUID id = UUID.randomUUID();
  private ChildProfile childProfile = new ChildProfileTestBuilder().build();
  private String mood = "okay";
  private String note = null;
  private Instant createdAt = Instant.now();
  private Instant deletedAt = null;

  public CheckInTestBuilder withMood(String mood) {
    this.mood = mood;
    return this;
  }

  public CheckInTestBuilder withNote(String note) {
    this.note = note;
    return this;
  }

  public CheckInTestBuilder deleted() {
    this.deletedAt = Instant.now();
    return this;
  }

  public CheckIn build() {
    var checkIn = new CheckIn(id, childProfile, mood);
    checkIn.setNote(note);
    checkIn.setCreatedAt(createdAt);
    checkIn.setUpdatedAt(createdAt);
    checkIn.setDeletedAt(deletedAt);
    return checkIn;
  }
}
```

### Memory Entry Builder
```java
public class MemoryEntryTestBuilder {
  private UUID id = UUID.randomUUID();
  private ChildProfile childProfile = new ChildProfileTestBuilder().build();
  private String world = "old"; // 'old' or 'new'
  private String title = "Memory Title";
  private String story = "Once upon a time...";
  private boolean isFavorite = false;
  private Instant deletedAt = null;

  public MemoryEntryTestBuilder withWorld(String world) {
    this.world = world;
    return this;
  }

  public MemoryEntryTestBuilder withStory(String story) {
    this.story = story;
    return this;
  }

  public MemoryEntryTestBuilder favorite() {
    this.isFavorite = true;
    return this;
  }

  public MemoryEntry build() {
    var memory = new MemoryEntry(id, childProfile, world, story);
    memory.setTitle(title);
    memory.setIsFavorite(isFavorite);
    memory.setDeletedAt(deletedAt);
    return memory;
  }
}
```

## Using Builders in Tests

### Simple Case
```java
@Test
void listMemories_returns_activeOnly() {
  var profile = new ChildProfileTestBuilder().build();

  var memory1 = new MemoryEntryTestBuilder()
    .withChildProfile(profile)
    .withWorld("old")
    .build();

  var memory2 = new MemoryEntryTestBuilder()
    .withChildProfile(profile)
    .withWorld("new")
    .deleted() // soft-deleted
    .build();

  when(repository.findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(profile.getId()))
    .thenReturn(List.of(memory1));

  var responses = service.getMemories(profile.getId());

  assertEquals(1, responses.size());
  assertEquals("old", responses.get(0).world());
}
```

### Complex Setup
```java
@Test
void getMemories_sortsFavorites_first() {
  var profile = new ChildProfileTestBuilder()
    .withNickname("Alice")
    .build();

  var oldMemory1 = new MemoryEntryTestBuilder()
    .withChildProfile(profile)
    .withWorld("old")
    .favorite() // ★
    .withStory("My favorite old memory")
    .build();

  var oldMemory2 = new MemoryEntryTestBuilder()
    .withChildProfile(profile)
    .withWorld("old")
    .withStory("A non-favorite old memory")
    .build();

  var newMemory1 = new MemoryEntryTestBuilder()
    .withChildProfile(profile)
    .withWorld("new")
    .favorite() // ★
    .withStory("My favorite new memory")
    .build();

  when(repository.findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(profile.getId()))
    .thenReturn(List.of(
      oldMemory1, // favorite
      newMemory1, // favorite
      oldMemory2  // not favorite
    ));

  var responses = service.getMemories(profile.getId(), "all");

  // Favorites should appear first
  assertTrue(responses.get(0).isFavorite());
  assertTrue(responses.get(1).isFavorite());
  assertFalse(responses.get(2).isFavorite());
}
```

## DTOs & Builders

### Request DTO Builder
```java
public class CreateMemoryRequestTestBuilder {
  private String world = "old";
  private String title = "Test Memory";
  private String story = "Test story content";
  private boolean isFavorite = false;

  public CreateMemoryRequestTestBuilder withWorld(String world) {
    this.world = world;
    return this;
  }

  public CreateMemoryRequestTestBuilder favorite() {
    this.isFavorite = true;
    return this;
  }

  public CreateMemoryRequest build() {
    return new CreateMemoryRequest(world, title, story, isFavorite);
  }
}
```

### Using Request Builder
```java
@Test
void createMemory_success_saves() {
  var profile = new ChildProfileTestBuilder().build();
  var request = new CreateMemoryRequestTestBuilder()
    .withWorld("new")
    .favorite()
    .build();

  when(repository.save(any(MemoryEntry.class)))
    .thenAnswer(i -> i.getArgument(0));

  var response = service.createMemory(profile.getId(), request);

  assertTrue(response.isFavorite());
  assertEquals("new", response.world());
}
```

## Fixture Classes (For Shared Setup)

If multiple tests use the same fixture, create a base class:

```java
class MemoryServiceTestFixture {
  protected ChildProfile childProfile;
  protected UUID childProfileId;

  @BeforeEach
  void setupFixture() {
    childProfile = new ChildProfileTestBuilder()
      .withNickname("Test Child")
      .withLocale("en")
      .build();
    childProfileId = childProfile.getId();
  }
}

class MemoryServiceTest extends MemoryServiceTestFixture {
  @Mock
  private MemoryEntryRepository repository;

  @InjectMocks
  private MemoryService service;

  @Test
  void createMemory_success() {
    var request = new CreateMemoryRequestTestBuilder().build();
    // childProfile and childProfileId already set up
  }
}
```

## Benefits

1. **Readability** — test setup is declarative, not procedural
2. **Maintainability** — when entity fields change, update builder once (not 50 tests)
3. **Defaults** — sensible defaults reduce boilerplate
4. **Fluency** — method chaining makes test data construction clear
5. **Reusability** — share builders across test classes

## Where to Store Builders

**Convention:** `app/server/src/test/java/com/capybee/server/{domain,web}/` mirroring production structure.

```
app/server/src/test/java/com/capybee/server/
├── domain/
│   ├── ChildProfileTestBuilder.java
│   ├── CheckInTestBuilder.java
│   ├── MemoryEntryTestBuilder.java
│   └── FriendshipEntryTestBuilder.java
├── web/
│   ├── CreateCheckInRequestTestBuilder.java
│   ├── CreateMemoryRequestTestBuilder.java
│   └── UpdateMemoryRequestTestBuilder.java
└── service/
    ├── CheckInServiceTest.java
    ├── MemoryServiceTest.java
    └── ...
```

## Pitfall: Over-Engineering Builders

**Don't:** Create builders for every single class
```java
// ❌ Too granular
HeavyMoodTestBuilder,
OkayMoodTestBuilder,
GoodMoodTestBuilder,
/* ... */
```

**Do:** Keep builders focused on entities and important DTOs
```java
// ✅ Right level
CheckInTestBuilder (entity)
CreateCheckInRequestTestBuilder (DTO)
```

**Guideline:** If you'd write the builder code 3+ times across tests, extract a builder.

## Example: Full Test Using Builders

```java
@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

  @Mock
  private MemoryEntryRepository repository;

  @InjectMocks
  private MemoryService service;

  private ChildProfile childProfile;
  private UUID childProfileId;

  @BeforeEach
  void setup() {
    childProfile = new ChildProfileTestBuilder()
      .withLocale("en")
      .build();
    childProfileId = childProfile.getId();
  }

  @Test
  void getMemories_success_returns_allUserMemories() {
    // Arrange
    var oldMemory = new MemoryEntryTestBuilder()
      .withChildProfile(childProfile)
      .withWorld("old")
      .build();

    var newMemory = new MemoryEntryTestBuilder()
      .withChildProfile(childProfile)
      .withWorld("new")
      .favorite()
      .build();

    when(repository.findByChildProfile_IdAndDeletedAtIsNullOrderByCreatedAtDesc(childProfileId))
      .thenReturn(List.of(oldMemory, newMemory));

    // Act
    var responses = service.getMemories(childProfileId);

    // Assert
    assertEquals(2, responses.size());
    assertTrue(responses.stream().anyMatch(m -> "old".equals(m.world())));
    assertTrue(responses.stream().anyMatch(m -> m.isFavorite()));
  }

  @Test
  void createMemory_success_persists() {
    // Arrange
    var request = new CreateMemoryRequestTestBuilder()
      .withWorld("new")
      .favorite()
      .build();

    when(repository.save(any(MemoryEntry.class)))
      .thenAnswer(i -> i.getArgument(0));

    // Act
    var response = service.createMemory(childProfileId, request);

    // Assert
    assertEquals("new", response.world());
    assertTrue(response.isFavorite());
    verify(repository).save(any(MemoryEntry.class));
  }
}
```

**Note:** Builders reduce setup clutter, letting tests focus on behavior assertions.
