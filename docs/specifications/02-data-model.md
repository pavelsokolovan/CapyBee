# CapyBee Data Model Specification

## Purpose

Define a practical, privacy-first data model for the first CapyBee release. This document extends the existing schema and aligns with the user stories in `01-user-stories.md`.

## Modeling Principles

- Child safety first: store only data required for product behavior.
- Family privacy: no cross-family social graph and no public records.
- Auditability: all core user actions should be time-based and traceable.
- Incremental delivery: keep V1 tables stable and add features through additive migrations.

## Scope

This spec covers:
- account and profile data
- check-ins and mission progress
- friendship tracker entries
- old world memory entries
- optional honeycomb projection support

This spec does not cover:
- external analytics warehouse schema
- real-time chat or any child-to-child communication model
- media storage internals (object store lifecycle policies)

## Current Schema (Already Implemented)

The following tables exist in migration `V1__init.sql` and JPA entities:

- `users`
- `check_ins`
- `missions`
- `mission_completions`

### users

Purpose: identity and login profile for the authenticated account.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Internal user id |
| google_subject | varchar(255) | no | UNIQUE | Google identity subject |
| email | varchar(255) | no | UNIQUE | Account email |
| display_name | varchar(255) | no |  | Display label |
| avatar_url | varchar(512) | yes |  | Profile image URL |
| locale | varchar(64) | yes |  | UI locale preference |
| created_at | timestamptz | no |  | Creation timestamp |
| updated_at | timestamptz | no |  | Last update timestamp |

### check_ins

Purpose: child or account owner mood entries.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Check-in id |
| user_id | uuid | no | FK -> users(id) ON DELETE CASCADE | Owner |
| mood | varchar(64) | no |  | Mood code |
| note | text | yes |  | Optional note |
| created_at | timestamptz | no |  | Event time |

### missions

Purpose: catalog of mission prompts.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Mission id |
| code | varchar(128) | no | UNIQUE | Stable mission key |
| title | varchar(255) | no |  | Localizable via key in future |
| description | text | no |  | Mission prompt |
| active | boolean | no | DEFAULT true | Visibility toggle |
| created_at | timestamptz | no |  | Creation timestamp |
| updated_at | timestamptz | no |  | Last update timestamp |

### mission_completions

Purpose: completion history by user.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Completion id |
| mission_id | uuid | no | FK -> missions(id) ON DELETE CASCADE | Mission reference |
| user_id | uuid | no | FK -> users(id) ON DELETE CASCADE | Owner |
| completed_at | timestamptz | no |  | Completion time |
| note | text | yes |  | Optional user reflection |

## Target Additions For Phase 2

To satisfy user stories around parent-child setup, private tracking, and Old/New World content, add the following tables.

### 1) family_profiles

Purpose: child profile records linked to a parent account.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Child profile id |
| parent_user_id | uuid | no | FK -> users(id) ON DELETE CASCADE | Parent account owner |
| nickname | varchar(80) | no |  | Child-visible name; real name not required |
| birth_year | int | yes | CHECK between 2000 and current_year | Optional age signal without full DOB |
| preferred_locale | varchar(16) | no | DEFAULT 'en' | `en` or `pl` in V1 |
| avatar_seed | varchar(64) | yes |  | Optional generated avatar token |
| active | boolean | no | DEFAULT true | Soft disable profile |
| created_at | timestamptz | no |  | Creation timestamp |
| updated_at | timestamptz | no |  | Last update timestamp |

Indexes:
- `idx_family_profiles_parent_user_id(parent_user_id)`

### 2) friendship_entries

Purpose: private social progress notes for one child profile.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Entry id |
| profile_id | uuid | no | FK -> family_profiles(id) ON DELETE CASCADE | Child profile owner |
| person_label | varchar(120) | no |  | Nickname or short description |
| stage | varchar(32) | no |  | One of defined stage codes |
| note | text | yes |  | Optional context |
| created_at | timestamptz | no |  | Event time |
| updated_at | timestamptz | no |  | Last update timestamp |

Allowed `stage` values (application enum):
- `noticed`
- `was_nice`
- `talked`
- `want_to_know_better`

Indexes:
- `idx_friendship_entries_profile_id_created_at(profile_id, created_at desc)`

### 3) memory_entries

Purpose: old-world and new-world memory artifacts.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Memory id |
| profile_id | uuid | no | FK -> family_profiles(id) ON DELETE CASCADE | Child profile owner |
| world_type | varchar(16) | no |  | `old_world` or `new_world` |
| title | varchar(120) | yes |  | Optional short title |
| text_content | text | yes |  | Optional narrative text |
| media_url | varchar(512) | yes |  | Optional image/drawing URL |
| is_favorite | boolean | no | DEFAULT false | Marks treasured entries |
| created_at | timestamptz | no |  | Creation timestamp |
| updated_at | timestamptz | no |  | Last update timestamp |

Rules:
- At least one of `text_content` or `media_url` should be present.

Indexes:
- `idx_memory_entries_profile_world_created(profile_id, world_type, created_at desc)`

### 4) profile_check_ins (optional bridge)

Purpose: if the product supports multiple children per parent account, check-ins should attach to a profile, not only to `users`.

| Column | Type | Null | Constraints | Notes |
|---|---|---|---|---|
| id | uuid | no | PK | Check-in id |
| profile_id | uuid | no | FK -> family_profiles(id) ON DELETE CASCADE | Child profile owner |
| mood | varchar(32) | no |  | See mood enum |
| note | text | yes |  | Optional reflection |
| created_at | timestamptz | no |  | Event time |

Mood enum (V1):
- `heavy`
- `okay`
- `good`

Indexes:
- `idx_profile_check_ins_profile_id_created_at(profile_id, created_at desc)`

Decision note:
- MVP decision: one child profile per parent account.
- Therefore, existing `check_ins.user_id` remains in MVP.
- Revisit `profile_check_ins` only when multi-child support is introduced.

## Entity Relationship Summary

- `users (1) -> (N) family_profiles`
- `users (1) -> (N) mission_completions`
- `missions (1) -> (N) mission_completions`
- `family_profiles (1) -> (N) friendship_entries`
- `family_profiles (1) -> (N) memory_entries`
- `family_profiles (1) -> (N) profile_check_ins` (if enabled)

## Normalization And Integrity Rules

- Use UUID primary keys for all user-generated entities.
- Use `timestamptz` for all time columns.
- Keep presentation labels (`title`, `person_label`) mutable.
- Keep stable machine keys (`missions.code`) immutable once published.
- Enforce referential integrity with `ON DELETE CASCADE` for child-owned data.

## Privacy Rules At Data Layer

- No table contains public visibility flags because public sharing is out of scope.
- No direct relationship between profiles from different parent accounts.
- Avoid storing sensitive free-form metadata unless required by UX.
- Avoid storing exact birth date; use `birth_year` or age range if needed.

## Suggested Migration Plan

### V2 migration
- Add `family_profiles`.
- Add `friendship_entries`.
- Add `memory_entries`.

### V3 migration (conditional)
- Add `profile_check_ins` and migrate from `check_ins` only when multi-child support is added beyond MVP.

### V4 migration
- Add check constraints and indexes after production data shape is validated.

## API Mapping Notes (For Next Spec)

- `family_profiles` backs child bootstrap and settings endpoints.
- `friendship_entries` backs friendship tracker CRUD endpoints.
- `memory_entries` backs old/new world memory endpoints.
- `missions` and `mission_completions` back mission feed and completion endpoints.
- check-in endpoint should target `profile_check_ins` if multi-child mode is enabled.

## Decision Log

- Parent account supports multiple child profiles in MVP: No.
- Media storage strategy: object storage with URL references (no media blobs in Postgres).
- Check-in content in MVP: mood plus optional free-text note.

## One Pending Decision

- Mission completion ownership (`users` vs `family_profiles`): pending.

Recommended default:
- Use `family_profiles` for long-term consistency with child-centered data ownership.
- Keep compatibility with current schema by adding `profile_id` to `mission_completions` in a follow-up migration, then gradually deprecate direct dependence on `user_id` for child activity queries.
