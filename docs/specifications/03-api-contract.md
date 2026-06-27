# CapyBee API Contract Specification

## Purpose

Define the HTTP API contract for CapyBee Phase 2. This document covers the currently implemented endpoints and the target MVP endpoints needed to satisfy user stories and data model decisions.

## Contract Scope

Includes:
- authentication and session status
- child profile bootstrap
- daily check-ins (with optional free-text notes)
- mission catalog and mission completion history
- friendship tracker entries
- memory entries (old world and new world)

Excludes:
- real-time features
- user-to-user communication
- admin-only operational endpoints

## Design Decisions Applied

- MVP supports one child profile per parent account.
- Check-ins in MVP are mood plus optional free-text note.
- Media assets are stored in object storage and referenced by URL.
- API path base is `/api` (no version segment in MVP).
- Mobile phone is the primary client form factor; API responses should be optimized for mobile latency and payload size.

## Authentication Model

### Session and identity

- Authentication uses Google OAuth2 through Spring Security.
- Browser clients use session cookies.
- Most `/api` endpoints require an authenticated session.

Public endpoints:
- `GET /api/health`
- `GET /api/auth-status`

Authenticated endpoints:
- everything else under `/api/**` unless explicitly marked public

### Authorization rule in MVP

- A parent can only read and modify records linked to their own account.
- Child data ownership is enforced through parent account scoping in query filters.

## Common Conventions

### Content type

- Request body: `application/json`
- Response body: `application/json`

### Time and ids

- IDs are UUID strings.
- Timestamps are ISO-8601 UTC strings.

### Paging

- MVP list endpoints use simple query params: `limit`, `before`.
- Default `limit` is endpoint-specific (usually 20).

### Mobile payload guidance

- Keep default list payloads small enough for mobile networks; prefer 10 to 20 items per page.
- Return only fields required by the current screen; avoid oversized nested response objects.
- Prefer incremental fetch patterns over loading full histories in one request.
- API latency target for common read operations should be tuned for responsive phone UX.

### Error response envelope

All non-2xx responses should use:

```json
{
  "error": {
    "code": "string",
    "message": "human readable message",
    "details": {}
  }
}
```

Standard codes:
- `UNAUTHORIZED` (401)
- `FORBIDDEN` (403)
- `NOT_FOUND` (404)
- `VALIDATION_ERROR` (400)
- `CONFLICT` (409)
- `INTERNAL_ERROR` (500)

## Currently Implemented Endpoints

### 1) Health

`GET /api/health`

Response 200:

```json
{
  "status": "ok"
}
```

### 2) Authentication status

`GET /api/auth-status`

Response 200 when unauthenticated:

```json
{
  "authenticated": false
}
```

Response 200 when authenticated:

```json
{
  "authenticated": true,
  "id": "uuid",
  "email": "parent@example.com",
  "displayName": "Parent Name",
  "avatarUrl": "https://..."
}
```

### 3) Current user

`GET /api/me`

Response 200:

```json
{
  "authenticated": true,
  "id": "uuid",
  "email": "parent@example.com",
  "displayName": "Parent Name",
  "avatarUrl": "https://..."
}
```

Response 401 if no valid session.

### 4) Create check-in

`POST /api/check-ins`

Request:

```json
{
  "mood": "heavy",
  "note": "Today felt hard at lunch"
}
```

Validation:
- `mood` required, allowed values in MVP: `heavy`, `okay`, `good`
- `note` optional, max 500 chars recommended

Response 200:

```json
{
  "id": "uuid",
  "mood": "heavy",
  "note": "Today felt hard at lunch",
  "createdAt": "2026-06-27T12:34:56Z"
}
```

### 5) List recent check-ins

`GET /api/check-ins`

Response 200:

```json
[
  {
    "id": "uuid",
    "mood": "okay",
    "note": "Better than yesterday",
    "createdAt": "2026-06-26T18:10:00Z"
  }
]
```

Current implementation returns the 10 most recent items.

## Target MVP Endpoints (Phase 2)

These endpoints are contract targets to implement next.

### 6) Child profile bootstrap and read

`POST /api/child-profile`

Creates the single child profile for the authenticated parent.

Request:

```json
{
  "nickname": "Mila",
  "birthYear": 2014,
  "preferredLocale": "pl",
  "avatarSeed": "bee-01"
}
```

Response 201:

```json
{
  "id": "uuid",
  "nickname": "Mila",
  "birthYear": 2014,
  "preferredLocale": "pl",
  "avatarSeed": "bee-01",
  "active": true,
  "createdAt": "2026-06-27T12:34:56Z",
  "updatedAt": "2026-06-27T12:34:56Z"
}
```

Errors:
- 409 `CONFLICT` if parent already has a child profile in MVP.

`GET /api/child-profile`

Response 200: same shape as create response.

### 7) Update child profile settings

`PATCH /api/child-profile`

Request (partial update):

```json
{
  "nickname": "Mila B",
  "preferredLocale": "en"
}
```

Response 200: updated profile object.

### 8) Missions list

`GET /api/missions?active=true&limit=20`

Response 200:

```json
[
  {
    "id": "uuid",
    "code": "say_hi_once",
    "title": "Say hi to one person",
    "description": "Try saying hi to one person today.",
    "active": true
  }
]
```

### 9) Complete mission

`POST /api/missions/{missionId}/completions`

Request:

```json
{
  "note": "I said hi to a classmate"
}
```

Response 201:

```json
{
  "id": "uuid",
  "missionId": "uuid",
  "profileId": "uuid",
  "completedAt": "2026-06-27T12:34:56Z",
  "note": "I said hi to a classmate"
}
```

Note:
- Contract uses `profileId` for long-term consistency.
- During transition, backend may internally map from authenticated user to their single profile.

### 10) List mission completions

`GET /api/missions/completions?limit=20&before=2026-06-27T12:34:56Z`

Response 200:

```json
[
  {
    "id": "uuid",
    "missionId": "uuid",
    "missionCode": "say_hi_once",
    "title": "Say hi to one person",
    "profileId": "uuid",
    "completedAt": "2026-06-27T12:34:56Z",
    "note": "I said hi"
  }
]
```

### 11) Friendship tracker

`POST /api/friendships`

Request:

```json
{
  "personLabel": "Girl from art class",
  "stage": "talked",
  "note": "We chatted about drawing"
}
```

Response 201:

```json
{
  "id": "uuid",
  "personLabel": "Girl from art class",
  "stage": "talked",
  "note": "We chatted about drawing",
  "createdAt": "2026-06-27T12:34:56Z",
  "updatedAt": "2026-06-27T12:34:56Z"
}
```

Allowed stages:
- `noticed`
- `was_nice`
- `talked`
- `want_to_know_better`

`GET /api/friendships?limit=20`

Response 200: list of friendship entries sorted by newest first.

`PATCH /api/friendships/{entryId}`

Allows updating `stage` and `note`.

`DELETE /api/friendships/{entryId}`

Response 204.

### 12) Memory entries

`POST /api/memories`

Request:

```json
{
  "worldType": "old_world",
  "title": "Grandma's kitchen",
  "textContent": "I miss making dumplings together",
  "mediaUrl": null,
  "isFavorite": true
}
```

Validation:
- `worldType` required: `old_world` or `new_world`
- at least one of `textContent` or `mediaUrl` required
- `mediaUrl` must be HTTPS URL when present

Response 201:

```json
{
  "id": "uuid",
  "worldType": "old_world",
  "title": "Grandma's kitchen",
  "textContent": "I miss making dumplings together",
  "mediaUrl": null,
  "isFavorite": true,
  "createdAt": "2026-06-27T12:34:56Z",
  "updatedAt": "2026-06-27T12:34:56Z"
}
```

`GET /api/memories?worldType=old_world&limit=20`

Response 200: list of memory entries.

`PATCH /api/memories/{memoryId}`

Allows updating `title`, `textContent`, `isFavorite`.

`DELETE /api/memories/{memoryId}`

Response 204.

## Endpoint Validation Rules

- Reject unknown enum values with 400 `VALIDATION_ERROR`.
- Reject payloads larger than configured body limits.
- Trim leading and trailing whitespace on text fields.
- Reject empty string values where semantic content is required.

## Security and Privacy Contract

- Never return Google subject identifier in API responses.
- Never expose cross-account data in list endpoints.
- All writes must resolve the authenticated parent and enforce ownership.
- Do not expose storage bucket internals for media; only canonical media URL.

## Implementation Notes

- Existing API currently returns map-based payloads for auth endpoints.
- As contract hardening step, introduce explicit DTOs for all responses.
- Add global exception handler to normalize error envelope.
- Add request validation annotations for required fields and enum checks.

## Out of Scope for MVP

- API versioning strategy (`/api/v2`) and deprecation headers
- bulk endpoints
- webhook integrations
