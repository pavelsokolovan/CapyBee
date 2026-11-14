import { get, set, del, keys } from 'idb-keyval';

export type QueueActionType =
  | 'checkIn'
  | 'missionCompletion'
  | 'friendship'
  | 'memory'
  | 'gameStart'
  | 'gameCommand'
  | 'gameResult'
  | 'checkInDelete'
  | 'missionCompletionDelete'
  | 'friendshipDelete'
  | 'memoryDelete';

export interface QueuedAction {
  clientId: string;
  owner?: string;
  runId?: string;
  poll?: boolean;
  type: QueueActionType;
  path: string;
  method: 'POST' | 'DELETE';
  payload?: Record<string, unknown>;
  createdAt: number;
  attempts: number;
  notBefore?: number;
  status: 'pending' | 'syncing' | 'failed';
}

const KEY_PREFIX = 'queued-action:';
const admissions = new Set<string>();

export async function enqueueAction(action: Omit<QueuedAction, 'attempts' | 'status'>): Promise<void> {
  if (action.type === 'gameStart') throw new Error('GAME_TRANSIENT_REQUIRED');
  let key: string | undefined;
  if (action.type === 'gameCommand') {
    const command = action.payload?.command as Record<string, unknown> | undefined;
    if (!action.owner || !action.runId || !command || !['CHOOSE','ADVANCE','MOVE','ABANDON'].includes(String(command.type)))
      throw new Error('GAME_TRANSIENT_REQUIRED');
    if (!Number.isSafeInteger(action.payload?.expectedRevision) || Number(action.payload?.expectedRevision) < 0)
      throw new Error('GAME_REVISION_INVALID');
    for (const value of [action.owner, action.runId, command.type === 'CHOOSE' ? command.optionId : command.type === 'MOVE' ? command.placeId : 'ok'])
      if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,256}$/.test(value)) throw new Error('GAME_ID_INVALID');
    key = action.owner + ':' + action.runId;
    if (admissions.has(key)) throw new Error('GAME_COMMAND_PENDING');
    admissions.add(key);
    try {
      const pending = await listQueuedActions();
      if (pending.some(a => a.type === 'gameCommand' && a.owner === action.owner && a.runId === action.runId))
        throw new Error('GAME_COMMAND_PENDING');
      const safeCommand = command.type === 'CHOOSE' ? {type:'CHOOSE',optionId:command.optionId}
        : command.type === 'MOVE' ? {type:'MOVE',placeId:command.placeId} : {type:command.type};
      await set(KEY_PREFIX + action.clientId, {clientId:action.clientId,owner:action.owner,runId:action.runId,
        type:'gameCommand',path:`/api/game/runs/${action.runId}/commands`,method:'POST',
        payload:{expectedRevision:action.payload!.expectedRevision,command:safeCommand},
        createdAt:action.createdAt,attempts:0,status:'pending'});
    } finally { admissions.delete(key); }
    return;
  }
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
  if (action.type === 'gameStart') throw new Error('GAME_TRANSIENT_REQUIRED');
  if (action.type === 'gameCommand') {
    const command = action.payload?.command as Record<string,unknown> | undefined;
    if (!command || !['CHOOSE','ADVANCE','MOVE','ABANDON'].includes(String(command.type)))
      throw new Error('GAME_TRANSIENT_REQUIRED');
    action = {clientId:action.clientId,owner:action.owner,runId:action.runId,type:action.type,
      path:action.path,method:action.method,createdAt:action.createdAt,attempts:action.attempts,
      status:action.status,notBefore:action.notBefore,poll:action.poll,
      payload:{expectedRevision:action.payload?.expectedRevision, command: command.type === 'CHOOSE'
        ? {type:'CHOOSE',optionId:command.optionId} : command.type === 'MOVE'
        ? {type:'MOVE',placeId:command.placeId} : {type:command.type}}};
  }
  await set(KEY_PREFIX + action.clientId, action);
}

export async function removeAction(clientId: string): Promise<void> {
  await del(KEY_PREFIX + clientId);
}
