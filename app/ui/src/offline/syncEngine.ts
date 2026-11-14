import { listQueuedActions, updateAction, removeAction, type QueuedAction } from './queueStore';

const MAX_ATTEMPTS = 8;
const MAX_GAME_ATTEMPTS = 12;
const REQUEST_TIMEOUT_MS = 45_000;
const BASE_BACKOFF_MS = 3_000;
const SYNC_INTERVAL_MS = 15_000;
const IDLE_SYNC_TIMEOUT_MS = 5 * 60_000;
const USER_ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'touchstart', 'scroll'] as const;

type SyncListener = (pendingCount: number) => void;
export type SyncedEvent = {
  action: QueuedAction;
  body?: unknown;
  status: number;
  outcome: 'done' | 'stale' | 'terminal';
};
type SyncedListener = (event: SyncedEvent) => void;
const listeners = new Set<SyncListener>();
const syncedListeners = new Set<SyncedListener>();
export { GameTransport, retryDelay, decodeGameResponse } from '../game/gameTransport';

let flushing = false;
let gameOwner: string | null = null;
export function setGameOwner(owner: string | null) { gameOwner = owner; }

export function onSyncStatusChange(listener: SyncListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function onSynced(listener: SyncedListener): () => void {
  syncedListeners.add(listener);
  return () => syncedListeners.delete(listener);
}

async function notifyListeners() {
  const pending = await listQueuedActions();
  const count = pending.filter((a) => a.status !== 'failed'
    || a.attempts < (isGameAction(a) ? MAX_GAME_ATTEMPTS : MAX_ATTEMPTS)).length;
  listeners.forEach((l) => l(count));
}

function backoffDelay(attempts: number): number {
  return Math.min(BASE_BACKOFF_MS * 2 ** attempts, 60_000);
}

type SendOutcome =
  | { kind: 'done'; status: number; body?: unknown }
  | { kind: 'stale'; status: number }
  | { kind: 'terminal'; status: number }
  | { kind: 'retry'; afterMs?: number }
  | { kind: 'auth' };

const isGameAction = (action: QueuedAction) => action.type === 'gameStart' || action.type === 'gameCommand';

async function responseBody(res: Response): Promise<unknown> {
  const contentType = res.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) return undefined;
  try {
    return await res.json();
  } catch {
    return undefined;
  }
}

function retryAfterDelay(res: Response): number | undefined {
  const header = res.headers.get('retry-after');
  if (header === null) return undefined;
  const seconds = Number(header);
  return Number.isFinite(seconds) ? Math.max(0, seconds * 1000) : undefined;
}

async function sendOne(action: QueuedAction): Promise<SendOutcome> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const method = action.poll && action.type === 'gameCommand' ? 'GET' : action.method ?? 'POST';
    const requestInit: RequestInit = {
      method,
      credentials: 'include',
      signal: controller.signal
    };

    if (isGameAction(action)) requestInit.cache = 'no-store';
    if (method === 'POST') {
      requestInit.headers = { 'Content-Type': 'application/json' };
      requestInit.body = JSON.stringify({ id: action.clientId, ...(action.payload ?? {}) });
    }

    const res = await fetch(action.poll && action.type === 'gameCommand' ? `${action.path}/${action.clientId}` : action.path, requestInit);
    clearTimeout(timer);

    if (res.status === 401) {
      return { kind: 'auth' };
    }
    if (method === 'DELETE' && res.status === 404) {
      return { kind: 'done', status: res.status };
    }
    if (isGameAction(action)) {
      const body = await responseBody(res);
      const errorCode = body && typeof body === 'object' && 'error' in body
        && (body as { error?: { code?: unknown } }).error
        ? (body as { error: { code?: unknown } }).error.code
        : body && typeof body === 'object' && 'code' in body
          ? (body as { code?: unknown }).code
          : undefined;
      if (action.owner !== gameOwner) return {kind:'auth'};
      if (action.poll && res.status === 404) {
        const current = await fetch(`/api/game/runs/${action.runId}`, {credentials:'include',cache:'no-store'});
        const state = await responseBody(current) as {revision?:unknown; status?:unknown} | undefined;
        if (current.ok && state?.status === 'ACTIVE' && state.revision === action.payload?.expectedRevision) {
          action.poll = false; await updateAction(action); return {kind:'retry'};
        }
        if(action.owner !== gameOwner || current.status === 401)return {kind:'auth'};
        if(!current.ok && (current.status === 429 || current.status >= 500))return {kind:'retry'};
        return {kind:'stale',status:res.status};
      }
      if (res.status === 202) {
        action.poll = true;
        await updateAction(action);
        const retryValue = body && typeof body === 'object' && 'retryAfterMs' in body
          ? (body as { retryAfterMs?: unknown }).retryAfterMs
          : undefined;
        const retryAfterMs = typeof retryValue === 'number' && Number.isFinite(retryValue)
          ? Math.max(0, retryValue)
          : undefined;
        return { kind: 'retry', afterMs: retryAfterMs };
      }
      if (res.status === 409 && (errorCode === 'REVISION_CONFLICT' || errorCode === 'RUN_NOT_ACTIVE')) {
        return { kind: 'stale', status: res.status };
      }
      if (res.status === 409 && errorCode === 'RUN_BUSY') {
        return { kind: 'retry', afterMs: retryAfterDelay(res) };
      }
      if (res.ok) return { kind: 'done', status: res.status, body };
      if (res.status === 408 || res.status === 429 || res.status >= 500) {
        return { kind: 'retry', afterMs: retryAfterDelay(res) };
      }
      return { kind: 'terminal', status: res.status };
    }
    if (res.status === 409 || res.ok) return { kind: 'done', status: res.status };
    if (res.status === 408 || res.status === 429 || res.status >= 500) {
      return { kind: 'retry', afterMs: retryAfterDelay(res) };
    }
    if (res.status >= 400) return { kind: 'terminal', status: res.status };
    return { kind: 'retry' };
  } catch {
    clearTimeout(timer);
    if (action.type === 'gameCommand') { action.poll = true; await updateAction(action); }
    return { kind: 'retry' };
  }
}

export async function flushQueue(): Promise<void> {
  if (flushing) return;
  flushing = true;
  try {
    const actions = await listQueuedActions();
    for (const action of actions) {
      const maxAttempts = isGameAction(action) ? MAX_GAME_ATTEMPTS : MAX_ATTEMPTS;
      if (action.status === 'failed' && action.attempts >= maxAttempts) {
        continue;
      }
      if ((action.notBefore ?? 0) > Date.now()) continue;

      if (isGameAction(action)) {
        if (!action.owner || action.type === 'gameStart') { await removeAction(action.clientId); continue; }
        if (action.owner !== gameOwner) continue;
      }
      action.status = 'syncing';
      await updateAction(action);

      const outcome = await sendOne(action);
      if (outcome.kind === 'done' || outcome.kind === 'stale' || outcome.kind === 'terminal') {
        await removeAction(action.clientId);
        if (isGameAction(action)) {
          const event: SyncedEvent = {
            action,
            body: outcome.kind === 'done' ? outcome.body : undefined,
            status: outcome.status,
            outcome: outcome.kind
          };
          syncedListeners.forEach((listener) => listener(event));
        }
      } else if (outcome.kind === 'auth') {
        action.status = 'pending';
        await updateAction(action);
        break;
      } else {
        action.attempts += 1;
        action.status = action.attempts >= maxAttempts ? 'failed' : 'pending';
        action.notBefore = Date.now() + (outcome.afterMs ?? backoffDelay(action.attempts));
        await updateAction(action);
      }
    }
  } finally {
    flushing = false;
    await notifyListeners();
  }
}

export function startSyncLoop(): () => void {
  let lastUserActivityAt = Date.now();
  let activityFlushQueued = false;
  let activityFlushTimer: ReturnType<typeof setTimeout> | null = null;

  const shouldFlushPeriodically = () => {
    return document.visibilityState === 'visible' && Date.now() - lastUserActivityAt < IDLE_SYNC_TIMEOUT_MS;
  };

  const flushIfActive = () => {
    if (shouldFlushPeriodically()) {
      void flushQueue();
    }
  };

  const queueActivityFlush = () => {
    if (activityFlushQueued) return;
    activityFlushQueued = true;
    activityFlushTimer = setTimeout(() => {
      activityFlushQueued = false;
      activityFlushTimer = null;
      void flushQueue();
    }, 0);
  };

  const markUserActive = () => {
    const wasIdle = !shouldFlushPeriodically();
    lastUserActivityAt = Date.now();
    if (document.visibilityState === 'visible' && wasIdle) {
      queueActivityFlush();
    }
  };

  flushQueue();
  const interval = setInterval(flushIfActive, SYNC_INTERVAL_MS);
  const onOnline = () => {
    markUserActive();
    flushIfActive();
  };
  const onVisible = () => {
    if (document.visibilityState === 'visible') {
      markUserActive();
      setTimeout(flushQueue, 1500);
    }
  };
  window.addEventListener('online', onOnline);
  document.addEventListener('visibilitychange', onVisible);
  USER_ACTIVITY_EVENTS.forEach((eventName) => {
    window.addEventListener(eventName, markUserActive, { passive: true });
  });
  return () => {
    clearInterval(interval);
    if (activityFlushTimer) {
      clearTimeout(activityFlushTimer);
    }
    window.removeEventListener('online', onOnline);
    document.removeEventListener('visibilitychange', onVisible);
    USER_ACTIVITY_EVENTS.forEach((eventName) => {
      window.removeEventListener(eventName, markUserActive);
    });
  };
}
