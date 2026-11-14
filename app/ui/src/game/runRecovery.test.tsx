import 'fake-indexeddb/auto';
import {StrictMode,act} from 'react';
import {createRoot} from 'react-dom/client';
import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import {RunController} from './runController';
import {GameTransport} from './gameTransport';
import {decodeSummary} from './gameDecoders';
import {activateGameOwner} from './gameOwner';
import {setGameOwner} from '../offline/syncEngine';
import {listQueuedActions,removeAction,forgetGameOwner} from '../offline/queueStore';
import {GameRun} from '../components/game/GameRun';
import fixtures from './fixtures/contracts.json';
(globalThis as typeof globalThis&{IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
beforeEach(async()=>{for(const action of await listQueuedActions())await removeAction(action.clientId);sessionStorage.clear();setGameOwner('owner');Object.defineProperty(navigator,'locks',{configurable:true,value:undefined});vi.spyOn(navigator,'onLine','get').mockReturnValue(false);vi.stubGlobal('fetch',vi.fn());});
afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();});
function controller(){return new RunController(decodeSummary(fixtures.pack),new GameTransport(()=> 'owner',async()=>{}),'owner');}
it('admits exactly one offline command without speculative progress and can remove a proved unsent intent',async()=>{
 const c=controller();await c.initialize();const original=c.getSnapshot().run;await c.submit({type:'CHOOSE',optionId:'first'});await c.submit({type:'ADVANCE'});
 const actions=await listQueuedActions();expect(actions).toHaveLength(1);expect(actions[0].payload).toEqual({expectedRevision:original.revision,command:{type:'CHOOSE',optionId:'first'}});expect(c.getSnapshot().run).toEqual(original);expect(c.getSnapshot().message).toBe('waiting');expect(fetch).not.toHaveBeenCalled();
 await c.removeUnsent();expect(await listQueuedActions()).toHaveLength(0);expect(c.getSnapshot().pending).toBe(false);c.detach();
});
it('refuses offline text and clears in-memory views on outgoing owner cleanup',async()=>{
 const c=controller();await c.initialize();await c.submit({type:'SUBMIT_TEXT',text:'private-sentinel'});expect(fetch).not.toHaveBeenCalled();expect(await listQueuedActions()).toHaveLength(0);expect(JSON.stringify(sessionStorage)).not.toContain('private-sentinel');
 await c.submit({type:'ABANDON'});activateGameOwner(null);await forgetGameOwner('owner');expect(c.getSnapshot().run.view).toBeNull();expect(await listQueuedActions()).toHaveLength(0);c.detach();
});
it('React cleanup/remount recovers identity without submitting an action in an effect',async()=>{
 const element=document.createElement('div');document.body.append(element);const root=createRoot(element);
 await act(async()=>root.render(<StrictMode><GameRun run={decodeSummary(fixtures.pack)} owner="owner" locale="en" onAnother={()=>{}}/></StrictMode>));
 expect(fetch).not.toHaveBeenCalled();expect(await listQueuedActions()).toHaveLength(0);await act(async()=>root.unmount());element.remove();
});
it('does not apply a response after an owner changes during fetch',async()=>{
 vi.spyOn(navigator,'onLine','get').mockReturnValue(true);
 vi.mocked(fetch).mockImplementation(async()=>{activateGameOwner('other');return new Response(JSON.stringify(fixtures.pack),{headers:{'content-type':'application/json'}});});
 const transport=new GameTransport(()=> 'owner',async()=>{});await expect(transport.read('/api/game/runs/current')).rejects.toThrow('GAME_OWNER_CHANGED');
});
it('adopts a competing unsent identity without replacing its command or revision',async()=>{
 const first=controller(),second=controller();await first.initialize();await second.initialize();await Promise.all([first.submit({type:'CHOOSE',optionId:'first'}),second.submit({type:'CHOOSE',optionId:'second'})]);await second.check();
 const actions=await listQueuedActions();expect(actions).toHaveLength(1);expect(actions[0].payload?.expectedRevision).toBe(fixtures.pack.revision);expect(second.getSnapshot().pending).toBe(true);expect(fetch).not.toHaveBeenCalled();first.detach();second.detach();
});
