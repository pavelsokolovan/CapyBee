# 15 — Offline Write Queue (Check-ins, Missions, Friendships, Memories)

## Purpose

Let a child open CapyBee, add a check-in / complete a mission / add a friendship note / save a memory instantly — even if the Fly.io machine is asleep and takes 10-20s+ to wake — with no visible waiting, no error state, and no data loss. The UI writes locally first and syncs in the background; CapyBee never shows a spinner or a failure to the kid.

Depends on `13-session-persistence-instruction.md` shipping first — this queue assumes the session is still valid when the sync finally reaches the server. If sessions still reset on wake, a queued write will resolve to a 401 instead of success, which this spec does not attempt to solve.

Scope: the four **create** actions a child performs during the daily flow — check-in (`submitCheckIn`), mission completion (`completeMission`), friendship entry (`addFriendship`), memory (`addMemory`) — in `AuthenticatedHome.tsx`. Deletes, edits, mission skip/undo, and profile updates are unchanged in this pass (see Out of Scope).

---

## Part A — Backend: accept an optional client-supplied id (idempotent create)

Currently every `create*` entity uses `@GeneratedValue(strategy = GenerationType.UUID)` and the id is always assigned server-side. To make retried offline syncs safe (the same POST might be sent twice if a connection drops mid-response), the client generates the id up front and the server accepts it, checking for an existing row first.

### A1. Check-ins (full pattern — replicate for the other three)

`app/server/src/main/java/com/capybee/server/web/dto/CreateCheckInRequest.java`

```java
package com.capybee.server.web.dto;

import java.util.UUID;

public record CreateCheckInRequest(
        UUID id,
        String mood,
        String note) {
}
```

`app/server/src/main/java/com/capybee/server/service/CheckInService.java` — update `createCheckIn`:

```java
@Transactional
public CheckInResponse createCheckIn(OAuth2AuthenticationToken oauth2Token, CreateCheckInRequest request) {
    UserAccount user = userService.getCurrentUser(oauth2Token);

    if (request.id() != null) {
        Optional<CheckInEntry> existing = checkInEntryRepository.findById(request.id());
        if (existing.isPresent()) {
            CheckInEntry found = existing.get();
            if (!found.getUserAccount().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Id already used by another account");
            }
            // Already synced from a previous attempt — idempotent no-op, return as-is.
            return toResponse(found);
        }
    }

    CheckInEntry entry = new CheckInEntry();
    if (request.id() != null) {
        entry.setId(request.id());
    }
    entry.setUserAccount(user);
    entry.setMood(request.mood());
    entry.setNote(request.note());

    CheckInEntry saved = checkInEntryRepository.save(entry);
    return toResponse(saved);
}
```

Add `import java.util.Optional;` if not already present.

`CheckInEntry.java` already has `setId(UUID id)` — no entity change needed. `@GeneratedValue(strategy = GenerationType.UUID)` only generates when the field is left `null` before persist, so a pre-set id is preserved as-is.

### A2. Apply the same pattern to the other three

| Entity | DTO to update | Service method | Notes |
|---|---|---|---|
| `MissionCompletion` | `CreateMissionCompletionRequest` — add `UUID id` field | `MissionService.completeMission` | Repository is `MissionCompletionRepository`; ownership check compares `getUserAccount().getId()` same as check-ins. |
| `FriendshipEntry` | `CreateFriendshipRequest` — add `UUID id` field | `FriendshipService.createFriendship` | Ownership check compares `getProfile().getParentUserId()` (or however `FamilyProfile` exposes the owning user) instead of a direct `UserAccount` — match whatever `FriendshipService` already uses to resolve the current child profile. |
| `MemoryEntry` | `CreateMemoryRequest` — add `UUID id` field | `MemoryService.createMemory` | Same profile-based ownership check as friendships. |

Each DTO becomes `record CreateXRequest(UUID id, ...existing fields...)`. Each service method gets the same "if id present, look up first, check ownership, return as idempotent no-op; otherwise set id then save" shape as `A1`.

**Do not** change the `@GeneratedValue` strategy or migrations — this only affects application-layer handling of an already-nullable-capable id field.

---

## Part B — Frontend: local queue storage

Add a tiny dependency for IndexedDB access (no need to hand-roll IndexedDB boilerplate):

```
npm install idb-keyval
```

### `app/ui/src/offline/queueStore.ts`

```ts
import { get, set, del, keys } from 'idb-keyval';

export type QueueActionType = 'checkIn' | 'missionCompletion' | 'friendship' | 'memory';

export interface QueuedAction {
  clientId: string;       // also used as the entity id sent to the server
  type: QueueActionType;
  path: string;            // e.g. '/api/check-ins' or `/api/missions/${missionId}/completions`
  payload: Record<string, unknown>;
  createdAt: number;       // Date.now(), for local ordering only
  attempts: number;
  status: 'pending' | 'syncing' | 'failed';
}

const KEY_PREFIX = 'queued-action:';

export async function enqueueAction(action: Omit<QueuedAction, 'attempts' | 'status'>): Promise<void> {
  const record: QueuedAction = { ...action, attempts: 0, status: 'pending' };
  await set(KEY_PREFIX + action.clientId, record);
}

export async function listQueuedActions(): Promise<QueuedAction[]> {
  const allKeys = await keys();
  const actionKeys = allKeys.filter((k): k is string => typeof k === 'string' && k.startsWith(KEY_PREFIX));
  const actions = await Promise.all(actionKeys.map((k) => get<QueuedAction>(k)));
  return actions
    .filter((a): a is QueuedAction => Boolean(a))
    .sort((a, b) => a.createdAt - b.createdAt);
}

export async function updateAction(action: QueuedAction): Promise<void> {
  await set(KEY_PREFIX + action.clientId, action);
}

export async function removeAction(clientId: string): Promise<void> {
  await del(KEY_PREFIX + clientId);
}
```

---

## Part C — Frontend: sync engine (retry, backoff, no blocking)

### `app/ui/src/offline/syncEngine.ts`

```ts
import { listQueuedActions, updateAction, removeAction, type QueuedAction } from './queueStore';

const MAX_ATTEMPTS = 8;
const REQUEST_TIMEOUT_MS = 45_000; // generous — Fly cold start can take 10-20s+
const BASE_BACKOFF_MS = 3_000;

type SyncListener = (pendingCount: number) => void;
const listeners = new Set<SyncListener>();
let flushing = false;

export function onSyncStatusChange(listener: SyncListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

async function notifyListeners() {
  const pending = await listQueuedActions();
  const count = pending.filter((a) => a.status !== 'failed' || a.attempts < MAX_ATTEMPTS).length;
  listeners.forEach((l) => l(count));
}

function backoffDelay(attempts: number): number {
  return Math.min(BASE_BACKOFF_MS * 2 ** attempts, 60_000);
}

async function sendOne(action: QueuedAction): Promise<boolean> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const res = await fetch(action.path, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: action.clientId, ...action.payload }),
      signal: controller.signal
    });
    clearTimeout(timer);

    if (res.status === 401) {
      // Session genuinely gone (see 13-session-persistence-instruction.md) — stop
      // retrying this item silently forever; surface via redirect on next foreground fetch.
      return false;
    }
    if (!res.ok && res.status !== 409) {
      return false;
    }
    return true; // 2xx, or 409 already-exists which we also treat as synced
  } catch {
    clearTimeout(timer);
    return false; // network error / timeout / machine still waking
  }
}

export async function flushQueue(): Promise<void> {
  if (flushing) return;
  flushing = true;
  try {
    const actions = await listQueuedActions();
    for (const action of actions) {
      if (action.status === 'failed' && action.attempts >= MAX_ATTEMPTS) {
        continue; // give up silently after MAX_ATTEMPTS — do not spam retries forever
      }

      action.status = 'syncing';
      await updateAction(action);

      const ok = await sendOne(action);

      if (ok) {
        await removeAction(action.clientId);
      } else {
        action.attempts += 1;
        action.status = action.attempts >= MAX_ATTEMPTS ? 'failed' : 'pending';
        await updateAction(action);
        if (action.attempts < MAX_ATTEMPTS) {
          await new Promise((r) => setTimeout(r, backoffDelay(action.attempts)));
        }
      }
    }
  } finally {
    flushing = false;
    await notifyListeners();
  }
}

export function startSyncLoop(): () => void {
  flushQueue();
  const interval = setInterval(flushQueue, 15_000);
  const onOnline = () => flushQueue();
  const onVisible = () => {
    if (document.visibilityState === 'visible') flushQueue();
  };
  window.addEventListener('online', onOnline);
  document.addEventListener('visibilitychange', onVisible);
  return () => {
    clearInterval(interval);
    window.removeEventListener('online', onOnline);
    document.removeEventListener('visibilitychange', onVisible);
  };
}
```

`startSyncLoop()` is called once from `AuthenticatedHome.tsx` in a `useEffect` on mount (empty dependency array), with its cleanup function returned normally.

409 handling assumes the create endpoints return `409 Conflict` if a Copilot-generated duplicate check prefers that over a silent 200 — either is fine as long as it's treated as "already synced" here. If Part A's services always return `200`/`201` for the idempotent-no-op case (as written above), the `409` branch simply never triggers and can stay as a defensive no-op.

---

## Part D — Frontend: optimistic writes wired into the four handlers

### D1. Pattern (check-in — full example)

```ts
import { enqueueAction } from './offline/queueStore';
import { flushQueue } from './offline/syncEngine';

const submitCheckIn = async (event: React.FormEvent) => {
  event.preventDefault();
  const submittedMood = mood;
  const submittedNote = note;
  const clientId = crypto.randomUUID();
  const optimisticCheckIn: CheckIn = {
    id: clientId,
    mood: submittedMood,
    note: submittedNote,
    createdAt: new Date().toISOString()
  };

  // 1. Update UI immediately — no loading state, no network wait.
  setCheckIns((current) => [...current, optimisticCheckIn]);
  setNote('');
  setMissionSuggestionVisible(true);
  triggerFeedback({
    kind: 'checkin',
    phrase: pickPhrase(moodPoolKey(submittedMood)),
    avatar: moodReactionAvatar(submittedMood)
  });
  if (activeTab === 'home') {
    setHomeAnimatedCellId(clientId);
  }

  // 2. Queue the real write, fire-and-forget.
  await enqueueAction({
    clientId,
    type: 'checkIn',
    path: '/api/check-ins',
    payload: { mood: submittedMood, note: submittedNote },
    createdAt: Date.now()
  });
  flushQueue(); // don't await — runs in background, retries handle failure
};
```

`setCheckInLoading` and its associated spinner UI can be removed from this handler entirely — the whole point is that the child never waits. If `checkInLoading` is used elsewhere (e.g. disabling the submit button to prevent double-taps), keep a very short local debounce instead (disable for ~500ms after tap) rather than tying it to network round-trip time.

### D2. Apply the same shape to the other three handlers

| Handler | Queue `type` | `path` | `payload` | Optimistic object id used for |
|---|---|---|---|---|
| `completeMission` | `'missionCompletion'` | `` `/api/missions/${missionId}/completions` `` | `{ note: noteValue }` | `setHomeAnimatedCellId(clientId)`, `MissionCompletion.id` |
| `addFriendship` | `'friendship'` | `'/api/friendships'` | `{ personLabel: friendLabel, stage: friendStage, note: friendNote }` | `FriendshipEntry.id`, toast trigger unaffected |
| `addMemory` | `'memory'` | `'/api/memories'` | `{ worldType, title: memoryTitle, textContent: memoryText, isFavorite: memoryFavorite }` | `MemoryEntry.id` |

For each: build the optimistic object matching that entity's response shape (reuse the existing TypeScript types already used for `setCheckIns`/`setMissionCompletions`/`setFriendships`/`setAllMemories`), push it into local state immediately, clear the form, trigger the existing celebratory feedback (toast/phrase/avatar) exactly as today, then `enqueueAction` + `flushQueue()`.

**Important:** remove the `await fetchXxx()` re-fetch calls that currently follow each of these creates (e.g. `await fetchCheckIns()`, `await fetchMissionCompletions()`, `await fetchFriendships()`, `await fetchMemories(worldType)` / `fetchAllMemories()`). Those calls block on the network and defeat the optimistic update while the server is asleep. The local optimistic entry is sufficient until the next natural full-list refresh (tab switch, app reopen, or the periodic sync loop). If a periodic background re-fetch is wanted later for multi-device consistency, that's separate scope — not needed for a single-child-profile app.

---

## Part E — Warm-up ping

Fire a cheap, unauthenticated, no-op request the instant the app loads, so the Fly machine starts waking before the child has even finished their first tap.

### `app/ui/src/main.tsx`

Add near the top of the module, before React renders:

```ts
// Wake the Fly machine in the background immediately on load — ignore the result,
// this exists purely to shorten however long the *next* real request has to wait.
fetch('/api/auth-status', { credentials: 'include' }).catch(() => {});
```

This duplicates the `checkAuthStatus()` call already made in `App.tsx`'s `useEffect`, which is fine — it's cheap, idempotent, and firing it a few hundred milliseconds earlier (before React has even mounted) is the entire point.

---

## Part F — Subtle sync indicator (tone-consistent)

Never show a spinner, error banner, or "failed to save" message to the child — consistent with CapyBee's "never pushes, never lectures" voice. A small, optional, quiet indicator is enough.

### Suggested placement

A tiny bee icon in the corner of the home screen header, only rendered when `pendingCount > 0` (from `onSyncStatusChange`), no text, subtle pulse animation via Framer Motion — nothing resembling a loading spinner or a warning icon. If it's still there after a long time, that's fine; it never blocks anything and never demands acknowledgment.

```tsx
// small addition to the home header, wired to onSyncStatusChange
const [pendingSync, setPendingSync] = useState(0);

useEffect(() => {
  const unsubscribe = onSyncStatusChange(setPendingSync);
  const stopLoop = startSyncLoop();
  return () => {
    unsubscribe();
    stopLoop();
  };
}, []);

{pendingSync > 0 && (
  <motion.span
    className="sync-indicator"
    animate={{ opacity: [0.4, 1, 0.4] }}
    transition={{ duration: 2, repeat: Infinity }}
    aria-hidden="true"
  >
    🐝
  </motion.span>
)}
```

Do not add `aria-live` announcements or any parent-facing "sync failed" surface in this pass — if that's wanted later for the parent view (`SCR-07`), treat it as separate scope so it doesn't leak network/error language into the child-facing UI.

---

## Out of scope for this pass

- Offline **reads** — the honeycomb map, mission list, friendship list, and memory list still require a successful `GET` on load; this pass only makes *writes* resilient to a sleeping server. A full offline read cache (mirroring GET responses into IndexedDB) is a reasonable follow-up spec if cold-start latency on initial load remains noticeable after `13` and `15` ship.
- Deletes, edits, mission skip/undo, and profile updates — same queue mechanism could extend to these later; not covered here to keep this pass reviewable.
- Multi-device conflict resolution — out of scope given the MVP is single-child-profile; last-write-wins via the idempotent-id pattern is sufficient.
- Any change to `PATCH`/`DELETE` idempotency — only `POST` create endpoints are touched in Part A.

---

## Traceability

- Directly implements the offline-mode / postponed-sync behavior requested for Fly.io free-tier cold starts.
- Depends on `13-session-persistence-instruction.md` (queued writes are worthless if the session itself resets on wake).
- Touches `SCR-03` (check-in), `SCR-06` (missions), `SCR-08` (friendship tracker), `SCR-09` (memories) from `04-screen-descriptions.md` — all four "Save success" states change from network-blocking to instant/optimistic.
- Supports US-004, US-009, US-010, US-012, US-014 (the core create-and-save acceptance criteria across check-ins, missions, friendships, memories) without changing their acceptance criteria — completion still ends in a saved record, just no longer synchronously.
- Tone requirements per US-017 — Part F specifically avoids any error/warning UI pattern that would read as a lecture or a failure state to the child.
