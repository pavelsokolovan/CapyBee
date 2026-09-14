---
# CapyBee Database Schema — Full Reference

This document shows the complete schema for all CapyBee tables, with relationships, audit timestamps, and soft-delete columns.

## Schema Diagram

```
┌─────────────────────┐
│      users          │
├─────────────────────┤
│ id (UUID) PK        │
│ google_account_id   │ UNIQUE
│ avatar_url (2048)   │
│ created_at          │
└──────────┬──────────┘
           │ 1:1
           │
┌──────────▼──────────────┐
│  child_profiles         │
├─────────────────────────┤
│ id (UUID) PK            │
│ user_id (FK) UNIQUE     │
│ nickname (255)          │
│ birth_year              │
│ locale (10)             │
│ created_at              │
│ updated_at              │
└──────────┬──────────────┘
           │ 1:*
           ├─ check_ins
           ├─ mission_completions
           ├─ friendship_entries
           ├─ memory_entries
           └─ session_restore_tokens
```

## Table Definitions

### users
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  google_account_id VARCHAR(255) UNIQUE NOT NULL,
  avatar_url VARCHAR(2048),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Audit:** created_at only (immutable once user logs in)

**Soft-delete:** None (user deletion is out of scope; sessions expire naturally)

### child_profiles
```sql
CREATE TABLE child_profiles (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  nickname VARCHAR(255) NOT NULL,
  birth_year INTEGER,
  locale VARCHAR(10) DEFAULT 'en',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_child_profiles_user_id ON child_profiles(user_id);
```

**Audit:** created_at, updated_at (updated when nickname/locale/birth_year change)

**Soft-delete:** None (profile is singular per user)

### check_ins
```sql
CREATE TABLE check_ins (
  id UUID PRIMARY KEY,
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
  mood VARCHAR(50) NOT NULL,
  note TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_check_ins_child_profile_id ON check_ins(child_profile_id);
CREATE INDEX idx_check_ins_deleted_at ON check_ins(deleted_at);
CREATE INDEX idx_check_ins_created_at ON check_ins(created_at DESC);
```

**Audit:** created_at, updated_at, deleted_at

**Soft-delete:** Yes (deleted_at)

**Queries:** 
- `SELECT * WHERE child_profile_id = ? AND deleted_at IS NULL ORDER BY created_at DESC` (user's check-in history)
- `SELECT * WHERE child_profile_id = ? AND deleted_at IS NULL` (honeycomb progress count)

### mission_completions
```sql
CREATE TABLE mission_completions (
  id UUID PRIMARY KEY,
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
  mission_id UUID NOT NULL, -- FK to missions table
  completion_note TEXT,
  completed_at TIMESTAMP,
  status VARCHAR(50) DEFAULT 'active',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_mission_completions_child_profile_id ON mission_completions(child_profile_id);
CREATE INDEX idx_mission_completions_mission_id ON mission_completions(mission_id);
CREATE INDEX idx_mission_completions_deleted_at ON mission_completions(deleted_at);
```

**Audit:** created_at, updated_at, deleted_at

**Soft-delete:** Yes (deleted_at)

**Queries:**
- `SELECT * WHERE child_profile_id = ? AND status = 'active' AND deleted_at IS NULL` (active missions)
- `SELECT * WHERE child_profile_id = ? AND status = 'completed' AND deleted_at IS NULL ORDER BY completed_at DESC` (history)

### friendship_entries
```sql
CREATE TABLE friendship_entries (
  id UUID PRIMARY KEY,
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
  person_label VARCHAR(255) NOT NULL,
  stage VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_friendship_entries_child_profile_id ON friendship_entries(child_profile_id);
CREATE INDEX idx_friendship_entries_deleted_at ON friendship_entries(deleted_at);
```

**Audit:** created_at, updated_at, deleted_at

**Soft-delete:** Yes (deleted_at)

**Queries:**
- `SELECT * WHERE child_profile_id = ? AND deleted_at IS NULL` (active friendship tracker)

### memory_entries
```sql
CREATE TABLE memory_entries (
  id UUID PRIMARY KEY,
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
  world VARCHAR(50) NOT NULL, -- 'old' or 'new'
  title VARCHAR(255),
  story TEXT NOT NULL,
  is_favorite BOOLEAN DEFAULT false,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_memory_entries_child_profile_id ON memory_entries(child_profile_id);
CREATE INDEX idx_memory_entries_world ON memory_entries(world);
CREATE INDEX idx_memory_entries_deleted_at ON memory_entries(deleted_at);
CREATE INDEX idx_memory_entries_is_favorite ON memory_entries(is_favorite);
```

**Audit:** created_at, updated_at, deleted_at

**Soft-delete:** Yes (deleted_at)

**Queries:**
- `SELECT * WHERE child_profile_id = ? AND world = 'old' AND deleted_at IS NULL ORDER BY created_at DESC` (old world memories)
- `SELECT * WHERE child_profile_id = ? AND world = 'new' AND deleted_at IS NULL ORDER BY is_favorite DESC, created_at DESC` (favorites pinned)

### missions
```sql
CREATE TABLE missions (
  id UUID PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  category VARCHAR(50),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_missions_category ON missions(category);
```

**Audit:** created_at, updated_at

**Soft-delete:** None (system-managed seed data)

**Queries:**
- `SELECT * FROM missions ORDER BY RANDOM() LIMIT 5` (suggest random missions)

### session_restore_tokens
```sql
CREATE TABLE session_restore_tokens (
  id UUID PRIMARY KEY,
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_session_restore_tokens_child_profile_id ON session_restore_tokens(child_profile_id);
CREATE INDEX idx_session_restore_tokens_expires_at ON session_restore_tokens(expires_at);
```

**Audit:** created_at only (single-use token)

**Soft-delete:** None (expiration via TTL)

**Queries:**
- `SELECT * WHERE token_hash = ? AND expires_at > NOW()` (validate token)
- `DELETE FROM session_restore_tokens WHERE expires_at <= NOW()` (cleanup job)

## Indexing Strategy

1. **All foreign keys** — indexed for JOIN performance
   - `child_profile_id` on all child-owned tables

2. **Soft-delete filtering** — indexed for queries filtering `deleted_at IS NULL`
   - Every table with soft-delete has `idx_<table>_deleted_at`

3. **Time-series queries** — indexed for sorting/filtering by date
   - `check_ins`: `created_at DESC` (honeycomb progress)
   - `memory_entries`: `created_at DESC` (newest first)

4. **Favorite/status flags** — indexed if used in WHERE
   - `memory_entries.is_favorite` (for pinned-favorites query)
   - `mission_completions.status` (for active vs. completed split)

## Type Conventions

| Use Case | Type | Example | Notes |
|----------|------|---------|-------|
| Primary key | `UUID` | `id` | Explicit UUIDs, no auto-increment |
| Foreign key | `UUID` | `child_profile_id` | Non-null, cascade delete |
| Short string | `VARCHAR(255)` | `nickname`, `person_label` | Names, labels |
| Long text | `TEXT` | `story`, `description` | Notes, user-generated content |
| Timestamps | `TIMESTAMP` | `created_at`, `deleted_at` | Default `CURRENT_TIMESTAMP`, nullable for soft-delete |
| Enums | `VARCHAR(50)` | `mood`, `status`, `world` | Not native Postgres ENUM (avoids schema lock) |
| Booleans | `BOOLEAN` | `is_favorite` | Default `false` |
| Integers | `INTEGER` | `birth_year` | Small numbers, nullable if optional |

## NULL vs. DEFAULT

**Prefer `NOT NULL DEFAULT <value>` for:**
- `created_at`, `updated_at` — always timestamped
- `locale` — default 'en' for English UI
- `is_favorite` — default false for memories
- `status` — default 'active' for missions

**Use `NULL` only for:**
- `deleted_at` — explicitly nullable (not yet soft-deleted)
- `avatar_url` — some users may not have OAuth profile picture
- `birth_year` — optional user input
- `note` — optional check-in note

## Performance Notes

1. **Avoiding N+1 queries** — service layer must prefetch related data
   - Example: when listing friendship_entries, don't fetch individual check_ins in a loop

2. **Pagination** — not yet implemented; if added, use `OFFSET/LIMIT` with index on sort column

3. **Archival strategy** — soft-delete keeps data at query time; if table grows large, archive old deleted records to a separate table

4. **Audit history** — current schema does not track change history (only updated_at); if needed, implement a separate audit log table
