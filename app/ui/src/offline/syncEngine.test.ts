import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { QueuedAction } from './queueStore';

const queueStore = vi.hoisted(() => ({
  listQueuedActions: vi.fn(),
  updateAction: vi.fn(),
  removeAction: vi.fn()
}));

vi.mock('./queueStore', () => ({
  listQueuedActions: queueStore.listQueuedActions,
  updateAction: queueStore.updateAction,
  removeAction: queueStore.removeAction
}));

import { flushQueue, onSyncStatusChange, startSyncLoop } from './syncEngine';

const createAction = (overrides: Partial<QueuedAction> = {}): QueuedAction => ({
  clientId: 'action-id',
  type: 'checkIn',
  path: '/api/check-ins',
  method: 'POST',
  payload: { mood: 'good' },
  createdAt: 100,
  attempts: 0,
  status: 'pending',
  ...overrides
});

describe('offline sync engine', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    queueStore.listQueuedActions.mockResolvedValue([]);
    queueStore.updateAction.mockResolvedValue(undefined);
    queueStore.removeAction.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it('sends a queued POST with its client id and removes it after success', async () => {
    const action = createAction();
    queueStore.listQueuedActions.mockResolvedValueOnce([action]).mockResolvedValueOnce([]);
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 201 }));

    await flushQueue();

    expect(queueStore.updateAction).toHaveBeenCalledWith({ ...action, status: 'syncing' });
    expect(fetch).toHaveBeenCalledWith('/api/check-ins', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: 'action-id', mood: 'good' }),
      signal: expect.any(AbortSignal)
    });
    expect(queueStore.removeAction).toHaveBeenCalledWith('action-id');
  });

  it('treats an already absent delete target as synchronized', async () => {
    const action = createAction({
      clientId: 'memory-id',
      type: 'memoryDelete',
      path: '/api/memories/memory-id',
      method: 'DELETE',
      payload: undefined
    });
    queueStore.listQueuedActions.mockResolvedValueOnce([action]).mockResolvedValueOnce([]);
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 404 }));

    await flushQueue();

    expect(fetch).toHaveBeenCalledWith('/api/memories/memory-id', {
      method: 'DELETE',
      credentials: 'include',
      signal: expect.any(AbortSignal)
    });
    expect(queueStore.removeAction).toHaveBeenCalledWith('memory-id');
  });

  it('treats a conflict as idempotent synchronization success', async () => {
    const action = createAction();
    queueStore.listQueuedActions.mockResolvedValueOnce([action]).mockResolvedValueOnce([]);
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 409 }));

    await flushQueue();

    expect(queueStore.removeAction).toHaveBeenCalledWith('action-id');
  });

  it('marks the final failed attempt and does not retry it in a later flush', async () => {
    const action = createAction({ attempts: 7 });
    queueStore.listQueuedActions.mockResolvedValueOnce([action]).mockResolvedValueOnce([action]).mockResolvedValueOnce([action]);
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));

    await flushQueue();
    await flushQueue();

    expect(queueStore.updateAction).toHaveBeenLastCalledWith({ ...action, attempts: 8, status: 'failed' });
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(queueStore.removeAction).not.toHaveBeenCalled();
  });

  it('notifies subscribers with the remaining synchronizable count', async () => {
    const listener = vi.fn();
    const unsubscribe = onSyncStatusChange(listener);
    const action = createAction({ attempts: 8, status: 'failed' });
    queueStore.listQueuedActions.mockResolvedValueOnce([]).mockResolvedValueOnce([action]);

    await flushQueue();
    unsubscribe();

    expect(listener).toHaveBeenCalledWith(0);
  });

  it('registers sync triggers and removes them during cleanup', async () => {
    vi.useFakeTimers();
    const addWindowListener = vi.spyOn(window, 'addEventListener');
    const removeWindowListener = vi.spyOn(window, 'removeEventListener');
    const addDocumentListener = vi.spyOn(document, 'addEventListener');
    const removeDocumentListener = vi.spyOn(document, 'removeEventListener');

    const cleanup = startSyncLoop();
    await vi.runOnlyPendingTimersAsync();
    cleanup();

    expect(addWindowListener).toHaveBeenCalledWith('online', expect.any(Function));
    expect(addDocumentListener).toHaveBeenCalledWith('visibilitychange', expect.any(Function));
    expect(removeWindowListener).toHaveBeenCalledWith('online', expect.any(Function));
    expect(removeDocumentListener).toHaveBeenCalledWith('visibilitychange', expect.any(Function));
  });

  it('flushes on reconnect and when the app becomes visible', async () => {
    vi.useFakeTimers();
    const addWindowListener = vi.spyOn(window, 'addEventListener');
    const addDocumentListener = vi.spyOn(document, 'addEventListener');

    const cleanup = startSyncLoop();
    await vi.advanceTimersByTimeAsync(0);

    const onOnline = addWindowListener.mock.calls.find(([eventName]) => eventName === 'online')?.[1];
    const onVisible = addDocumentListener.mock.calls.find(([eventName]) => eventName === 'visibilitychange')?.[1];
    expect(onOnline).toBeTypeOf('function');
    expect(onVisible).toBeTypeOf('function');

    (onOnline as EventListener)(new Event('online'));
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
    (onVisible as EventListener)(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(1_500);

    expect(queueStore.listQueuedActions).toHaveBeenCalledTimes(6);
    cleanup();
  });
});