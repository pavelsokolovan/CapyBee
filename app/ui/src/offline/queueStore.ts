import {ownerAllowed} from '../game/gameOwner';
import {withGameLock} from '../game/gameLock';
import { get, set, del, keys } from 'idb-keyval';

export type QueueActionType =
  | 'checkIn'
  | 'missionCompletion'
  | 'friendship'
  | 'memory'
  | 'gameStart'
  | 'gameCommand'
  | 'gameResult'
  | 'gameCompletion'
  | 'checkInDelete'
  | 'missionCompletionDelete'
  | 'friendshipDelete'
  | 'memoryDelete';

export interface QueuedAction {
  clientId: string;
  owner?: string;
  completedAt?: string;
  runId?: string;
  poll?: boolean;
  sent?: boolean;
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
  if(action.type==='gameCompletion') {
    const safe=completionAction({...action,attempts:0,status:'pending'});
    if(!ownerAllowed(safe.owner!))throw new Error('GAME_OWNER_CHANGED');
    await set(KEY_PREFIX+safe.clientId,safe);
    if(!ownerAllowed(safe.owner!)){await removeAction(safe.clientId);throw new Error('GAME_OWNER_CHANGED');}
    return;
  }
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
      const admitted=await withGameLock(action.owner,action.runId,async()=>{
      if(!ownerAllowed(action.owner!))throw new Error('GAME_OWNER_CHANGED');
      const existing=await listQueuedActions();
      if(existing.some(a=>a.type==='gameCommand'&&a.owner===action.owner&&a.runId===action.runId))throw new Error('GAME_COMMAND_PENDING');
      await set(KEY_PREFIX + action.clientId, {clientId:action.clientId,owner:action.owner,runId:action.runId,
        type:'gameCommand',path:`/api/game/runs/${action.runId}/commands`,method:'POST',
        payload:{expectedRevision:action.payload!.expectedRevision,command:safeCommand},
        createdAt:action.createdAt,attempts:0,status:'pending'});if(!ownerAllowed(action.owner!)){await removeAction(action.clientId);throw new Error('GAME_OWNER_CHANGED');}return true;});
      if(!admitted)throw new Error('GAME_COMMAND_PENDING');
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
    if(!validGameAction(action))throw new Error('GAME_TRANSIENT_REQUIRED');
    const command = action.payload?.command as Record<string,unknown> | undefined;
    if (!command || !['CHOOSE','ADVANCE','MOVE','ABANDON'].includes(String(command.type)))
      throw new Error('GAME_TRANSIENT_REQUIRED');
    action = {clientId:action.clientId,owner:action.owner,runId:action.runId,type:action.type,
      path:`/api/game/runs/${action.runId}/commands`,method:'POST',createdAt:action.createdAt,attempts:action.attempts,
      status:action.status,notBefore:action.notBefore,poll:action.poll,sent:action.sent,
      payload:{expectedRevision:action.payload?.expectedRevision, command: command.type === 'CHOOSE'
        ? {type:'CHOOSE',optionId:command.optionId} : command.type === 'MOVE'
        ? {type:'MOVE',placeId:command.placeId} : {type:command.type}}};
  }
  if(action.type==='gameCompletion')action=completionAction(action);
  await set(KEY_PREFIX + action.clientId, action);
}

export async function removeAction(clientId: string): Promise<void> {
  await del(KEY_PREFIX + clientId);
}

/** Explicit logout/account replacement only; temporary auth expiry preserves safe work. */
export async function forgetGameOwner(owner:string){
 for(const action of await listQueuedActions())if((action.type==='gameStart'||action.type==='gameCommand'||action.type==='gameCompletion')&&action.owner===owner)await removeAction(action.clientId);
 for(const key of Object.keys(sessionStorage))if(key.startsWith('game-operation:'+owner+':'))sessionStorage.removeItem(key);
}
export async function migrateOwnerlessGames():Promise<boolean>{
 let removed=false;for(const action of await listQueuedActions())if((action.type==='gameStart'||action.type==='gameCommand'||action.type==='gameCompletion')&&(!action.owner||action.type==='gameStart')){await removeAction(action.clientId);removed=true;}
 if(removed)sessionStorage.setItem('game-reconfirm','1');return removed;
}

export const getQueuedAction=(id:string)=>get<QueuedAction>(KEY_PREFIX+id);

export function validGameAction(action:QueuedAction):boolean {
 if(action.type==='gameCompletion'){try{return completionAction(action).path===action.path&&action.method==='POST';}catch{return false;}}
 const command=action.payload?.command as Record<string,unknown>|undefined;
 return !!command&&['CHOOSE','ADVANCE','MOVE','ABANDON'].includes(String(command.type))
  && [action.owner,action.runId,action.clientId,command.type==='CHOOSE'?command.optionId:command.type==='MOVE'?command.placeId:'ok'].every(value=>typeof value==='string'&&/^[A-Za-z0-9_-]{1,256}$/.test(value))
  &&Number.isSafeInteger(action.payload?.expectedRevision)&&Number(action.payload?.expectedRevision)>=0;
}

function completionAction(action:QueuedAction):QueuedAction {
 const p=action.payload;
 if(![action.owner,action.runId,action.clientId].every(v=>typeof v==='string'&&/^[A-Za-z0-9_-]{1,256}$/.test(v))
   || !['CELL','MISSION'].includes(String(p?.kind)) || !Number.isSafeInteger(p?.expectedRevision)||Number(p?.expectedRevision)<0
   || !Number.isSafeInteger(p?.suggestionVersion)||Number(p?.suggestionVersion)<1)throw new Error('GAME_COMPLETION_INVALID');
 if(action.completedAt!==undefined&&!Number.isFinite(Date.parse(action.completedAt)))throw new Error('GAME_COMPLETION_INVALID');
 return {clientId:action.clientId,owner:action.owner,runId:action.runId,completedAt:action.completedAt===undefined?undefined:new Date(action.completedAt).toISOString(),type:'gameCompletion',method:'POST',
   path:`/api/game/runs/${action.runId}/completion/${String(p!.kind).toLowerCase()}`,
   payload:{expectedRevision:p!.expectedRevision,suggestionVersion:p!.suggestionVersion,kind:p!.kind},
   createdAt:action.createdAt,attempts:action.attempts,status:action.status,notBefore:action.notBefore};
}
