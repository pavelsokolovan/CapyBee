vi.mock('../game/gameLock',()=>({withGameLock:async(_owner:string,_run:string,work:()=>Promise<unknown>)=>work()}));
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
  forgetGameOwner,migrateOwnerlessGames,getQueuedAction,enqueueAction,
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
describe('engine privacy policy',()=>{
 beforeEach(()=>storage.clear());
 it('rejects starts and private text before storage',async()=>{
  await expect(enqueueAction({...firstAction,type:'gameStart'})).rejects.toThrow();
  await expect(enqueueAction({...firstAction,type:'gameCommand',owner:'o',runId:'r',payload:{command:{type:'SUBMIT_TEXT',text:'sentinel'}}})).rejects.toThrow();
  expect(storage.size).toBe(0);
 });
 it('requires owner identity and one unresolved command per run',async()=>{
  await expect(enqueueAction({...firstAction,type:'gameCommand',payload:{command:{type:'CHOOSE'}}})).rejects.toThrow();
  const action={...firstAction,type:'gameCommand' as const,owner:'o',runId:'r',payload:{expectedRevision:0,command:{type:'CHOOSE',optionId:'choice'}}};
  await enqueueAction(action);
  await expect(enqueueAction({...action,clientId:'second'})).rejects.toThrow('GAME_COMMAND_PENDING');
 });
});

describe('updates retain the durable command allowlist',()=>{
 beforeEach(()=>storage.clear());
 it('rejects private updates and strips extraneous payload properties',async()=>{
  await expect(updateAction({...firstAction,attempts:0,status:'pending',type:'gameStart'})).rejects.toThrow();
  await expect(updateAction({...firstAction,attempts:0,status:'pending',type:'gameCommand',payload:{command:{type:'SUBMIT_TEXT',text:'sentinel'}}})).rejects.toThrow();
  for (const command of [{type:'CHOOSE',optionId:'a',text:'sentinel'},{type:'MOVE',placeId:'b',text:'sentinel'},{type:'ADVANCE',text:'sentinel'}]) {
    await updateAction({...firstAction,attempts:1,status:'pending',owner:'owner',runId:'run',type:'gameCommand',payload:{expectedRevision:1,command,private:'sentinel'}});
    expect(JSON.stringify([...storage.values()])).not.toContain('sentinel');
  }
 });
});

it('migrates ownerless game records and preserves unrelated offline work',async()=>{
 storage.set('queued-action:legacy',{clientId:'legacy',type:'gameCommand'});
 storage.set('queued-action:start',{clientId:'start',type:'gameStart',owner:'owner'});
 storage.set('queued-action:memory',{clientId:'memory',type:'memory'});
 expect(await migrateOwnerlessGames()).toBe(true);expect(sessionStorage.getItem('game-reconfirm')).toBe('1');
 expect(await getQueuedAction('legacy')).toBeUndefined();expect(await getQueuedAction('memory')).toBeTruthy();expect(await migrateOwnerlessGames()).toBe(false);
});
it('cleans only the outgoing owner game records and markers',async()=>{
 storage.set('queued-action:a',{clientId:'a',type:'gameCommand',owner:'a'});storage.set('queued-action:b',{clientId:'b',type:'gameCommand',owner:'b'});
 storage.set('queued-action:memory',{clientId:'memory',type:'memory',owner:'a'});
 sessionStorage.setItem('game-operation:a:id','identity');sessionStorage.setItem('game-operation:b:id','identity');
 await forgetGameOwner('a');expect(await getQueuedAction('a')).toBeUndefined();expect(await getQueuedAction('b')).toBeTruthy();expect(await getQueuedAction('memory')).toBeTruthy();expect(sessionStorage.getItem('game-operation:a:id')).toBeNull();expect(sessionStorage.getItem('game-operation:b:id')).toBe('identity');
});
it('rejects legacy private or malformed typed game records on update',async()=>{
 await expect(updateAction({clientId:'legacy',owner:'owner',runId:'run',type:'gameCommand',path:'/api/arbitrary',method:'POST',createdAt:1,attempts:0,status:'pending',payload:{expectedRevision:0,command:{type:'SUBMIT_TEXT',text:'private'}}})).rejects.toThrow('GAME_TRANSIENT_REQUIRED');
 expect(storage.has('queued-action:legacy')).toBe(false);
});

it('admits owner-scoped completion choices with only non-text versioned linkage',async()=>{
 storage.clear();
 const action={clientId:'completion',owner:'owner',runId:'run',type:'gameCompletion' as const,path:'/evil',method:'POST' as const,createdAt:100,
  completedAt:'2026-11-15T12:00:00Z',payload:{kind:'MISSION',expectedRevision:3,suggestionVersion:1,text:'private-sentinel',missionId:'untrusted'}};
 await enqueueAction(action);
 const stored=(await listQueuedActions())[0];
 expect(stored.path).toBe('/api/game/runs/run/completion/mission');expect(stored.completedAt).toBe('2026-11-15T12:00:00.000Z');
 expect(stored.payload).toEqual({kind:'MISSION',expectedRevision:3,suggestionVersion:1});
 expect(JSON.stringify(stored)).not.toContain('private-sentinel');
 await updateAction({...stored,payload:{...stored.payload,extra:'private-sentinel'}});
 expect(JSON.stringify(await listQueuedActions())).not.toContain('private-sentinel');
 await forgetGameOwner('owner');expect(await listQueuedActions()).toEqual([]);
});
it('rejects ownerless, text-bearing kinds and invalid completion versions',async()=>{
 storage.clear();
 const action={clientId:'completion',owner:'owner',runId:'run',type:'gameCompletion' as const,path:'',method:'POST' as const,createdAt:100,payload:{kind:'CELL',expectedRevision:3,suggestionVersion:1}};
 for(const bad of [{...action,owner:undefined},{...action,completedAt:'private-sentinel'},{...action,payload:{...action.payload,kind:'TEXT'}},{...action,payload:{...action.payload,suggestionVersion:0}}])await expect(enqueueAction(bad)).rejects.toThrow();
 storage.set('queued-action:completion',{...action,owner:undefined});
 expect(await migrateOwnerlessGames()).toBe(true);expect(await listQueuedActions()).toEqual([]);
});
