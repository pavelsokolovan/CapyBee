---
# PostgreSQL Data Types — CapyBee Reference

This reference explains which Postgres data types to use in CapyBee migrations and why.

## Primary Keys

### UUID (Recommended for CapyBee)
```sql
id UUID PRIMARY KEY
```

**Advantages:**
- Globally unique across distributed systems (future-proof for microservices)
- No auto-increment dependency
- Offline-safe: client can generate id for offline-first patterns

**Disadvantages:**
- Larger storage (16 bytes vs 8 for BIGINT)
- No natural ordering (can impact index performance)

**CapyBee uses UUID** for all entities because offline mutations require client-generated identifiers.

### BIGSERIAL (Not Used in CapyBee)
```sql
id BIGSERIAL PRIMARY KEY
```

**When to use:** Traditional server-side auto-increment (e.g., public-facing IDs in APIs)

**Why CapyBee avoids:** Incompatible with offline-first, client-generated IDs for retries.

## String Types

### VARCHAR(n) — Limited-Length Strings
```sql
nickname VARCHAR(255),
mood VARCHAR(50),
world VARCHAR(10)
```

**Use for:** Names, labels, enums, short identifiers

**Sizing guidelines:**
- Names/labels: `VARCHAR(255)` (covers 99% of real names)
- Enum-like values: `VARCHAR(50)` (mood, status, stage, world)
- URLs/long identifiers: `VARCHAR(2048)` (avatar URLs)

**Advantages:** Enforced length, prevents accidental bloat

**Disadvantages:** Requires migration to increase length if exceeded

### TEXT — Unlimited-Length Strings
```sql
story TEXT,
note TEXT,
description TEXT
```

**Use for:** User-generated content, rich text, multi-paragraph fields

**Advantages:** No length limit, can store arbitrarily large text

**Disadvantages:** Can waste space if mostly short (prefer VARCHAR for consistent short strings)

**CapyBee uses TEXT for:**
- `check_ins.note` — optional user note (~500 chars typical, but user might paste long content)
- `memory_entries.story` — required story field (can be lengthy)
- `memory_entries.title` — optional, but could be long (use TEXT, not VARCHAR(255))

## Numeric Types

### INTEGER (Standard Int)
```sql
birth_year INTEGER,
CHECK (birth_year >= 1900 AND birth_year <= 2025)
```

**Use for:** Small numbers, ages, years

**Range:** -2,147,483,648 to 2,147,483,647

**Advantages:** Small storage (4 bytes), SQL can do arithmetic

**Disadvantages:** Limited range for very large numbers

### BIGINT (Large Int)
```sql
large_counter BIGINT
```

**Use for:** Counters that may exceed 2 billion

**Range:** -9,223,372,036,854,775,808 to 9,223,372,036,854,775,807

**CapyBee note:** Not currently used; `birth_year` fits in INTEGER.

### NUMERIC / DECIMAL (Exact Decimal)
```sql
-- Not used in CapyBee currently
amount NUMERIC(10, 2) -- For money: 10 total digits, 2 after decimal
```

**Use for:** Financial amounts, precise decimals where rounding matters

**CapyBee:** Not needed (no payments).

## Boolean

### BOOLEAN
```sql
is_favorite BOOLEAN DEFAULT false,
is_active BOOLEAN DEFAULT true
```

**Use for:** Binary flags, yes/no fields

**Advantages:** Compact (1 byte), semantic clarity

**Queries:**
```sql
SELECT * FROM memory_entries WHERE is_favorite = true;
SELECT * FROM friendship_entries WHERE is_favorite = false; -- filter active only
```

**CapyBee uses:**
- `memory_entries.is_favorite` — soft pinning in UI (favorite-first sort)
- `mission_completions.status` — use VARCHAR instead (`'active'`, `'completed'`, `'skipped'`) for extensibility

## Timestamp Types

### TIMESTAMP (Without Timezone) — Recommended for CapyBee
```sql
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
deleted_at TIMESTAMP NULL
```

**Use for:** Server-side audit timestamps

**Advantages:**
- No timezone confusion (all stored in server time)
- Compact storage (8 bytes)
- SQL functions: `CURRENT_TIMESTAMP`, `NOW()`

**Disadvantages:**
- Assumes single timezone (usually UTC)

**CapyBee convention:** Always use `TIMESTAMP`, assume UTC server time.

### TIMESTAMPTZ (With Timezone)
```sql
-- Not used in CapyBee
event_at TIMESTAMPTZ NOT NULL
```

**Use for:** Events with explicit timezone context (rare in single-timezone apps)

**When to consider:** If users in different timezones need local times stored (CapyBee doesn't need this; UI handles timezone display).

### DATE (Date Only)
```sql
-- Not used in CapyBee; use TIMESTAMP instead
birth_date DATE
```

**Use for:** Standalone dates with no time component

**CapyBee avoids:** We store `birth_year` (INTEGER) only, not full date.

## UUID for Foreign Keys

### UUID as Foreign Key
```sql
child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE
```

**Advantages:**
- Matches entity IDs (all UUIDs in CapyBee)
- Orphan cleanup via CASCADE
- No numeric auto-increment coupling

**Indexes:**
```sql
CREATE INDEX idx_table_child_profile_id ON table_name(child_profile_id);
```

**Always index foreign keys** (used in WHERE clauses and JOINs).

## Enums (Not Recommended for CapyBee)

### Postgres NATIVE ENUM
```sql
-- ❌ Not used in CapyBee
CREATE TYPE mood_enum AS ENUM ('heavy', 'okay', 'good');
ALTER TABLE check_ins ADD COLUMN mood mood_enum;
```

**Why CapyBee avoids:**
- Schema-locked: adding a new mood requires `ALTER TYPE` (DDL, blocks concurrent queries)
- Client-side flexibility: easier to add moods in app logic than database schema

**CapyBee uses VARCHAR instead:**
```sql
-- ✅ Recommended
ALTER TABLE check_ins ADD COLUMN mood VARCHAR(50);
-- Adding a new mood: just save it, no schema change needed
```

## JSON / JSONB (Not Currently Used)

### JSONB (Binary JSON)
```sql
-- Not used in CapyBee currently, but future option
metadata JSONB DEFAULT '{}',
SELECT * FROM table WHERE metadata->>'key' = 'value';
```

**Use case:** Semi-structured data (flexible fields, nested objects)

**Advantages:** Flexible schema, queryable, indexed via GIN

**Disadvantages:** Type safety only at application layer; schema validation must be in code

**CapyBee future:** If new entity types have optional heterogeneous fields, consider JSONB over creating new columns.

## Null Handling

### NOT NULL with DEFAULT
```sql
-- Prefer this
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
locale VARCHAR(10) NOT NULL DEFAULT 'en'
```

**Advantages:** No null checks in queries, predictable values

**Use for:** Audit timestamps, defaults that are always set

### Nullable Columns (Explicit NULL)
```sql
-- Use sparingly
deleted_at TIMESTAMP NULL,
avatar_url VARCHAR(2048) NULL,
birth_year INTEGER NULL
```

**Advantages:** Semantic clarity (field is truly optional)

**Use for:**
- Soft-delete (`deleted_at` = null until deleted)
- Optional user input (`birth_year`, `avatar_url`)
- Never for audit fields (`created_at`, `updated_at`)

### Avoid Nullable Booleans
```sql
-- ❌ AVOID
is_active BOOLEAN NULL -- What does null mean?

-- ✅ PREFER
is_active BOOLEAN NOT NULL DEFAULT true -- Clear: true or false
```

## Storage Size Reference

| Type | Storage | Notes |
|------|---------|-------|
| UUID | 16 bytes | Fixed size |
| VARCHAR(n) | n bytes (+ 1 header) | Variable up to n |
| TEXT | Variable | No limit |
| BOOLEAN | 1 byte | |
| INTEGER | 4 bytes | |
| BIGINT | 8 bytes | |
| TIMESTAMP | 8 bytes | With or without TZ |
| DATE | 4 bytes | |

**CapyBee table size estimate** (order-of-magnitude):
- 1000 child profiles: ~50 KB
- 10,000 check-ins: ~500 KB
- 10,000 memories: ~1 MB (depends on story length)

Not a concern for operational scale (<1 GB total).

## Migration Examples

### Adding a New VARCHAR Column
```sql
ALTER TABLE memory_entries ADD COLUMN tags VARCHAR(255) DEFAULT '';
```

### Widening an Existing VARCHAR (schema evolution)
```sql
-- As done for avatar_url (V10 migration)
ALTER TABLE users ALTER COLUMN avatar_url TYPE VARCHAR(2048);
```

### Adding a NULL Column (no default required)
```sql
ALTER TABLE child_profiles ADD COLUMN avatar_seed VARCHAR(50) NULL;
```

### Adding a NOT NULL Column (must have default or fill existing rows)
```sql
-- Option A: Default + NOT NULL
ALTER TABLE friendships ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'friend';

-- Option B: Fill + NOT NULL (two statements)
ALTER TABLE friendships ADD COLUMN category VARCHAR(50);
UPDATE friendships SET category = 'friend' WHERE category IS NULL;
ALTER TABLE friendships ALTER COLUMN category SET NOT NULL;
```

### Removing a Column
```sql
ALTER TABLE memory_entries DROP COLUMN is_archived;
```

**Note:** Flyway does not automatically roll back schema changes. If DROP is wrong, create a new migration to restore the column (or restore from backup).
