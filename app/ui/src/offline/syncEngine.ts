import { listQueuedActions, updateAction, removeAction, type QueuedAction } from './queueStore';

const MAX_ATTEMPTS = 8;
const REQUEST_TIMEOUT_MS = 45_000;
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
    const method = action.method ?? 'POST';
    const requestInit: RequestInit = {
      method,
      credentials: 'include',
      signal: controller.signal
    };

    if (method === 'POST') {
      requestInit.headers = { 'Content-Type': 'application/json' };
      requestInit.body = JSON.stringify({ id: action.clientId, ...(action.payload ?? {}) });
    }

    const res = await fetch(action.path, requestInit);
    clearTimeout(timer);

    if (res.status === 401) {
      return false;
    }
    if (method === 'DELETE' && res.status === 404) {
      return true;
    }
    if (!res.ok && res.status !== 409) {
      return false;
    }
    return true;
  } catch {
    clearTimeout(timer);
    return false;
  }
}

export async function flushQueue(): Promise<void> {
  if (flushing) return;
  flushing = true;
  try {
    const actions = await listQueuedActions();
    for (const action of actions) {
      if (action.status === 'failed' && action.attempts >= MAX_ATTEMPTS) {
        continue;
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
