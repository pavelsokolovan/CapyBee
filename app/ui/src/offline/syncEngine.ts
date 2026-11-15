import {decodeCompletion} from '../game/gameCompletion';
import {decodeGameResponse} from '../game/gameTransport';
import {restoreGameSession} from '../game/restoreGameSession';
import {withGameLock} from '../game/gameLock';
import {activateGameOwner,notifyGameRevision,watchGameOwner,ownerAllowed} from '../game/gameOwner';
import { validGameAction,getQueuedAction, listQueuedActions, updateAction, removeAction, type QueuedAction } from './queueStore';

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
export type { GameReply, OperationMarker } from '../game/gameTransport';
export { GameTransport, retryDelay, decodeGameResponse } from '../game/gameTransport';

let flushing = false;
let gameOwner: string | null = null;
export function setGameOwner(owner: string | null) { gameOwner = owner; activateGameOwner(owner); }

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
  const count = pending.filter(a=>!isGameAction(a)||((a.type==='gameCommand'||a.type==='gameCompletion')&&a.owner===gameOwner)).filter((a) => a.status !== 'failed'
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

const isGameAction = (action: QueuedAction) => action.type === 'gameStart' || action.type === 'gameCommand' || action.type === 'gameCompletion';

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
  const unwatch=watchGameOwner(()=>{if(isGameAction(action)&&!ownerAllowed(action.owner!))controller.abort();});
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const method = action.poll && action.type === 'gameCommand' ? 'GET' : action.method ?? 'POST';
    const requestInit: RequestInit = {
      method,
      credentials: 'include',
      signal: controller.signal
    };

    if (isGameAction(action)) {requestInit.cache = 'no-store';requestInit.headers={'X-Game-Owner':action.owner!};}
    if (method === 'POST') {
      requestInit.headers = { ...requestInit.headers,'Content-Type': 'application/json' };
      requestInit.body = JSON.stringify(action.type==='gameCompletion'
        ? {id:action.clientId,expectedRevision:action.payload?.expectedRevision,suggestionVersion:action.payload?.suggestionVersion}
        : { id: action.clientId, ...(action.payload ?? {}) });
    }

    if(isGameAction(action)&&(action.owner!==gameOwner||!ownerAllowed(action.owner!)))return {kind:'auth'};
    if(action.type==='gameCommand'&&method==='POST'){action.poll=true;action.sent=true;await updateAction(action);}
    const res = await fetch(method==='GET' && action.type === 'gameCommand' ? `${action.path}/${action.clientId}` : action.path, requestInit);
    clearTimeout(timer);

    if (res.status === 401) {
      if(isGameAction(action)){try{await restoreGameSession(action.owner!);}catch{window.dispatchEvent(new Event('game-auth-required'));}}
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
      if(errorCode==='GAME_OWNER_CHANGED'){setGameOwner(null);window.dispatchEvent(new Event('game-session-changed'));return {kind:'auth'};}
      if (action.owner !== gameOwner) return {kind:'auth'};
      if (method==='GET' && res.status === 404) {
        const current = await fetch(`/api/game/runs/${action.runId}`, {credentials:'include',cache:'no-store',headers:{'X-Game-Owner':action.owner!}});
        const state = await responseBody(current) as {revision?:unknown; status?:unknown} | undefined;
        if(action.owner !== gameOwner)return {kind:'auth'};
        if (current.ok && state?.status === 'ACTIVE' && state.revision === action.payload?.expectedRevision) {
          action.poll = false; await updateAction(action); return {kind:'retry'};
        }
        if(action.owner !== gameOwner || current.status === 401)return {kind:'auth'};
        if(!current.ok && (current.status === 429 || current.status >= 500))return {kind:'retry'};
        return {kind:'stale',status:res.status};
      }
      if (res.status === 202) {
        if(action.type==='gameCommand'){action.poll = true;await updateAction(action);}
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
      if (res.ok) {
        if(action.type==='gameCompletion') {
          const accepted=decodeCompletion(body);
          if(accepted.runId!==action.runId||accepted.revision!==action.payload?.expectedRevision
            ||accepted.suggestionVersion!==action.payload?.suggestionVersion
            ||(action.payload?.kind==='MISSION'?!accepted.missionAccepted:!accepted.cellAdded))return {kind:'retry'};
          return {kind:'done',status:res.status,body:accepted};
        }
        const decoded=decodeGameResponse({status:res.status,body});
        if(!decoded||typeof decoded!=='object'||!('commandId' in decoded)||decoded.commandId!==action.clientId||!('outcome' in decoded))return {kind:'retry'};
        return { kind: 'done', status: res.status, body:decoded };
      }
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
    if(isGameAction(action)&&(action.owner!==gameOwner||!ownerAllowed(action.owner!)))return {kind:'auth'};
    if (action.type === 'gameCommand') { action.poll = true; await updateAction(action); }
    return { kind: 'retry' };
  } finally {unwatch();clearTimeout(timer);}
}

export async function flushQueue(): Promise<void> {
  if(flushing)return;
  if(navigator.onLine===false){await notifyListeners();return;}
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
        if (!action.owner || action.type === 'gameStart' || !validGameAction(action)) { await removeAction(action.clientId);sessionStorage.setItem('game-reconfirm','1'); continue; }
      if (action.owner !== gameOwner) continue;
      }
      const process=async()=>{
      if(isGameAction(action)){
        const fresh=await getQueuedAction(action.clientId);
        if(!fresh||fresh.owner!==gameOwner||!ownerAllowed(fresh.owner!))return false;
        Object.assign(action,fresh);
      }
      action.status = 'syncing';
      await updateAction(action);

      const outcome = await sendOne(action);
      if(isGameAction(action)&&action.owner!==gameOwner)return true;
      if (outcome.kind === 'done' || outcome.kind === 'stale' || outcome.kind === 'terminal') {
        await removeAction(action.clientId);
        if (isGameAction(action)) {
          const event: SyncedEvent = {
            action,
            body: outcome.kind === 'done' ? outcome.body : undefined,
            status: outcome.status,
            outcome: outcome.kind
          };
          notifyGameRevision(action.owner!,action.runId!,typeof (event.body as {revision?:unknown})?.revision==='number'?(event.body as {revision:number}).revision:0);
          syncedListeners.forEach((listener) => listener(event));
        }
      } else if (outcome.kind === 'auth') {
        action.status = 'pending';
        await updateAction(action);
        return true;
      } else {
        action.attempts += 1;
        action.status = action.attempts >= maxAttempts ? 'failed' : 'pending';
        action.notBefore = Date.now() + (outcome.afterMs ?? backoffDelay(action.attempts));
        await updateAction(action);
      }
      return false;
      };
      if(isGameAction(action)){
        try{if(await withGameLock(action.owner!,action.runId!,process))break;}
        catch{/* Unsafe coordination pauses game dispatch; unrelated actions still sync. */}
      }else if(await process())break;
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
