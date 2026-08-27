import { beforeEach, describe, expect, it, vi } from 'vitest';

const storage = new Map<string, unknown>();

vi.mock('idb-keyval', () => ({
  get: vi.fn(async (key: string) => storage.get(key)),
  set: vi.fn(async (key: string, value: unknown) => {
    storage.set(key, value);
  }),
  del: vi.fn(async (key: string) => {
    storage.delete(key);
  }),
  keys: vi.fn(async () => Array.from(storage.keys()))
}));

import {
  enqueueAction,
  listQueuedActions,
  removeAction,
  updateAction,
  type QueuedAction
} from './queueStore';

const firstAction: Omit<QueuedAction, 'attempts' | 'status'> = {
  clientId: 'first',
  type: 'checkIn',
  path: '/api/check-ins',
  method: 'POST',
  payload: { mood: 'okay' },
  createdAt: 100
};

describe('offline queue store', () => {
  beforeEach(() => {
    storage.clear();
    vi.clearAllMocks();
  });

  it('enqueues actions with the initial retry state', async () => {
    await enqueueAction(firstAction);

    await expect(listQueuedActions()).resolves.toEqual([
      { ...firstAction, attempts: 0, status: 'pending' }
    ]);
  });

  it('lists only queue records in creation order', async () => {
    storage.set('unrelated-key', { value: 'ignore' });
    storage.set('queued-action:later', {
      ...firstAction,
      clientId: 'later',
      createdAt: 200,
      attempts: 0,
      status: 'pending'
    });
    storage.set('queued-action:missing', undefined);
    storage.set('queued-action:first', { ...firstAction, attempts: 0, status: 'pending' });

    await expect(listQueuedActions()).resolves.toMatchObject([
      { clientId: 'first', createdAt: 100 },
      { clientId: 'later', createdAt: 200 }
    ]);
  });

  it('updates and removes an action', async () => {
    await enqueueAction(firstAction);
    const retryingAction: QueuedAction = {
      ...firstAction,
      attempts: 3,
      status: 'syncing'
    };

    await updateAction(retryingAction);
    await expect(listQueuedActions()).resolves.toEqual([retryingAction]);

    await removeAction(firstAction.clientId);
    await expect(listQueuedActions()).resolves.toEqual([]);
  });

  it('preserves delete actions without a request payload', async () => {
    await enqueueAction({
      clientId: 'delete-id',
      type: 'memoryDelete',
      path: '/api/memories/memory-id',
      method: 'DELETE',
      createdAt: 100
    });

    await expect(listQueuedActions()).resolves.toEqual([
      {
        clientId: 'delete-id',
        type: 'memoryDelete',
        path: '/api/memories/memory-id',
        method: 'DELETE',
        createdAt: 100,
        attempts: 0,
        status: 'pending'
      }
    ]);
  });
});