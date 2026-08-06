import { get, set, del, keys } from 'idb-keyval';

export type QueueActionType = 'checkIn' | 'missionCompletion' | 'friendship' | 'memory';

export interface QueuedAction {
  clientId: string;
  type: QueueActionType;
  path: string;
  payload: Record<string, unknown>;
  createdAt: number;
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
