# CapyBee Screen Descriptions

## Purpose

Describe each MVP screen in implementation-ready detail: who uses it, what it shows, which actions it supports, and which API endpoints it depends on.
The primary usage context is a mobile phone, so screen behavior is specified phone-first.

## Scope

Includes:
- currently implemented screens
- planned MVP screens required by user stories
- key transitions between screens

Excludes:
- pixel-perfect visual design system specs
- animation timing specs
- component-level CSS details

## Screen Principles

- Keep every screen emotionally calm and easy to parse.
- Prefer one primary action per screen.
- Keep copy short, supportive, and age-appropriate.
- Avoid social comparison and competitive UI patterns.
- Design for phone screens first, then adapt upward for tablet/desktop.
- Keep above-the-fold content focused on one task at a time.

## Screen Inventory

| ID | Screen | Route | Actor | Status |
|---|---|---|---|---|
| SCR-01 | Landing | `/` | Parent/Family | Implemented |
| SCR-02 | Auth Redirect/Bootstrap | `/oauth2/authorization/google` + callback | Parent/Family | Implemented |
| SCR-03 | Authenticated Home | `/` (authenticated state) | Child (with parent setup) | Implemented (baseline) |
| SCR-04 | Child Profile Setup | `/profile/setup` | Parent | Planned MVP |
| SCR-05 | Child Profile Settings | `/profile/settings` | Parent | Planned MVP |
| SCR-06 | Missions List | `/missions` | Child | Planned MVP |
| SCR-07 | Mission Completion History | `/missions/history` | Parent/Child | Planned MVP |
| SCR-08 | Friendship Tracker | `/friendships` | Child | Planned MVP |
| SCR-09 | Memory Space (Old/New World) | `/memories` | Child | Planned MVP |
| SCR-10 | Not Authenticated/Session Expired | inline state + redirect | Parent/Family | Implemented (behavior), needs dedicated UI |

## SCR-01 - Landing

Goal:
- Introduce CapyBee and provide one clear sign-in path.

Primary user:
- Parent starting account access.

Core content:
- Hero heading and short explanation.
- Google sign-in CTA.
- Product highlights and early roadmap blocks.

Primary actions:
- Continue with Google.
- Scroll/read overview.

API dependencies:
- `GET /api/auth-status` on load to detect authenticated session.

States:
- Loading: auth status check in progress.
- Unauthenticated: landing content shown.
- Authenticated: redirect/render authenticated home.

Success criteria:
- User understands purpose in under 10 seconds.
- User can start sign-in in one click.

## SCR-02 - Auth Redirect/Bootstrap

Goal:
- Complete OAuth2 sign-in and create/reuse parent account.

Primary user:
- Parent.

Core behavior:
- Browser redirects to Google and returns to app root.
- Backend resolves authenticated principal and creates account if first login.

API dependencies:
- Security OAuth flow.
- `GET /api/auth-status` and `GET /api/me` post-login.

States:
- Redirect in progress.
- Callback success.
- Callback error (show safe retry message).

Success criteria:
- Parent reaches authenticated state without manual refresh.

## SCR-03 - Authenticated Home

Goal:
- Provide a safe default home with quick check-in flow and recent history.

Primary user:
- Child (under parent-managed account).

Core content:
- Welcome header with account info.
- Mood selector (`heavy`, `okay`, `good`).
- Optional free-text note field.
- Save check-in action.
- Recent check-in history list.

Primary actions:
- Submit a check-in.
- Review recent check-ins.
- Log out.

API dependencies:
- `GET /api/check-ins`
- `POST /api/check-ins`

States:
- Initial loading of check-ins.
- Empty history state.
- Save in progress.
- Save success with list refresh.
- 401 state triggers login redirect.

Success criteria:
- Child can complete a check-in in under 2 minutes.
- New check-in appears in history immediately after save.

## SCR-04 - Child Profile Setup

Goal:
- Create the single MVP child profile linked to parent account.

Primary user:
- Parent.

Core fields:
- Nickname (required).
- Birth year (optional).
- Preferred locale (`en`/`pl`).
- Avatar seed (optional preset).

Primary actions:
- Create profile.
- Cancel/back.

API dependencies:
- `POST /api/child-profile`
- `GET /api/child-profile` for existing-profile detection.

Validation and constraints:
- Real name not required.
- One child profile per parent in MVP.

States:
- New profile form.
- Existing profile detected (show link to settings).
- Validation errors.
- Save success transition to home.

Success criteria:
- Parent can complete setup in under 3 minutes.

## SCR-05 - Child Profile Settings

Goal:
- Allow safe updates to child profile preferences.

Primary user:
- Parent.

Editable fields:
- Nickname.
- Preferred locale.
- Avatar seed.
- Optional active toggle.

API dependencies:
- `GET /api/child-profile`
- `PATCH /api/child-profile`

States:
- Loading current settings.
- Edit mode.
- Save success toast/banner.
- Save failure with retry guidance.

Success criteria:
- Parent can change language preference and see it applied on next screen load.

## SCR-06 - Missions List

Goal:
- Show small, actionable missions and let child pick one.

Primary user:
- Child.

Core content:
- Mission cards with title and short description.
- Primary action on each card: mark as completed.

API dependencies:
- `GET /api/missions?active=true&limit=20`
- `POST /api/missions/{missionId}/completions`

States:
- Loading missions.
- Empty state when no active missions.
- Completion success feedback.
- Completion failure with retry.

Success criteria:
- Child can complete a mission in 2 taps after opening the page.

## SCR-07 - Mission Completion History

Goal:
- Show progress over time in a simple list.

Primary user:
- Parent and child.

Core content:
- Reverse chronological list.
- Mission title, completion time, optional note.

API dependencies:
- `GET /api/missions/completions?limit=20&before=...`

States:
- Loading list.
- Empty state.
- Pagination/load more.

Success criteria:
- User can understand recent mission progress at a glance.

## SCR-08 - Friendship Tracker

Goal:
- Capture small social steps privately.

Primary user:
- Child.

Core content:
- Entry form: person label, stage, note.
- Existing entries list with edit/delete.

API dependencies:
- `POST /api/friendships`
- `GET /api/friendships?limit=20`
- `PATCH /api/friendships/{entryId}`
- `DELETE /api/friendships/{entryId}`

States:
- Loading entries.
- Empty state prompt.
- Entry created/updated/deleted feedback.

Success criteria:
- Child can add a tracker entry in under 1 minute.

## SCR-09 - Memory Space (Old World / New World)

Goal:
- Preserve old-home memories and build new-home moments side-by-side.

Primary user:
- Child.

Core content:
- Two tabs/segments: Old World and New World.
- Memory cards with title/text/media preview.
- Favorite marker for treasured entries.

API dependencies:
- `POST /api/memories`
- `GET /api/memories?worldType=old_world|new_world&limit=20`
- `PATCH /api/memories/{memoryId}`
- `DELETE /api/memories/{memoryId}`

States:
- Loading memories for selected world.
- Empty world state with supportive prompt.
- Save/update/delete feedback.

Success criteria:
- Child can add one old-world memory and one new-world moment without confusion.

## SCR-10 - Not Authenticated / Session Expired

Goal:
- Prevent dead-end behavior when session is missing or expired.

Primary user:
- Parent/Family.

Core content:
- Clear short message: session expired or sign-in required.
- Primary button: continue with Google.

API dependencies:
- Any protected endpoint returning 401.

States:
- Inline intercept from protected screen.
- Redirect fallback if inline rendering is not available.

Success criteria:
- User can return to authenticated flow with one click.

## Primary User Flows

### Flow A - First-time setup

1. Landing (`SCR-01`)
2. OAuth sign-in (`SCR-02`)
3. Child profile setup (`SCR-04`)
4. Authenticated home (`SCR-03`)

### Flow B - Daily check-in

1. Authenticated home (`SCR-03`)
2. Submit mood and optional note
3. See updated history on same screen

### Flow C - Mission loop

1. Missions list (`SCR-06`)
2. Complete mission
3. Mission history (`SCR-07`)

### Flow D - Relationship and memory journaling

1. Friendship tracker (`SCR-08`)
2. Memory space (`SCR-09`)
3. Return to home (`SCR-03`)

## Navigation Model (MVP)

- Mobile-first navigation after login:
  - Bottom navigation/tab bar on phone for core destinations.
  - Optional top navigation on larger screens.
- Core destinations:
  - Home
  - Missions
  - Friendship Tracker
  - Memories
  - Profile Settings
- Keep nav labels short and child-readable.

## Non-Functional UX Requirements

- Phone-first layout is the default target.
- Mobile-first layout for 360px and above.
- Primary actions are thumb-reachable on mobile.
- Form validation messages are plain-language and non-technical.
- Loading and empty states are always explicit; no blank screens.
- Touch targets should be comfortably tappable (target size at least 44x44 px).
- Avoid interaction patterns that depend on hover states.

## Traceability

- `SCR-03` maps to US-003, US-004, US-005.
- `SCR-04` and `SCR-05` map to US-002.
- `SCR-06` and `SCR-07` map to US-009, US-010, US-011.
- `SCR-08` maps to US-012, US-013.
- `SCR-09` maps to US-008, US-014.
- Copy and tone across all screens map to US-017.
- Privacy boundaries across all screens map to US-018 and US-019.
