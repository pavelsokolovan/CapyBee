import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {afterEach,beforeEach,expect,it,vi} from 'vitest';
const fixture=vi.hoisted(()=>({value:{runId:'run',revision:3,completedAt:'2026-11-15T12:00:00Z',suggestionKey:'say_thank_you_helper',suggestionVersion:1,mission:{id:'mission',code:'say_thank_you_helper',title:'Reviewed catalog title',timeHint:'A minute'},missionAccepted:false,cellAdded:false},actions:[] as unknown[],notify:(_event:unknown)=>{}}));
vi.mock('../../game/gameTransport',()=>({GameTransport:class {setLanguage(){} detach(){} async poll(_path:unknown,_signal:unknown,accept:(reply:unknown)=>void){accept({status:200,body:fixture.value});}}}));
vi.mock('../../offline/queueStore',()=>({listQueuedActions:async()=>fixture.actions,updateAction:vi.fn(async()=>{}),enqueueAction:vi.fn(async(action:unknown)=>{fixture.actions.push(action);})}));
vi.mock('../../offline/syncEngine',()=>({flushQueue:vi.fn(async()=>{}),onSynced:(notify:(event:unknown)=>void)=>{fixture.notify=notify;return()=>{};}}));
import {GameCompletion} from './GameCompletion';
import {GameProductContext} from './GameProductContext';
import {enqueueAction,updateAction} from '../../offline/queueStore';
import {activateGameOwner} from '../../game/gameOwner';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
let host:HTMLDivElement,root:Root;
beforeEach(()=>{vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-15T15:00:00Z'));activateGameOwner('owner');fixture.actions=[];fixture.value.missionAccepted=false;fixture.value.cellAdded=false;host=document.createElement('div');document.body.append(host);root=createRoot(host);vi.clearAllMocks();});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();vi.useRealTimers();});
const button=(label:string)=>[...host.querySelectorAll('button')].find(b=>b.textContent===label)!;
it.each(['en','pl'] as const)('reads without rewarding and dismisses without mutation (%s)',async locale=>{
 await act(async()=>root.render(<GameCompletion owner="owner" runId="run" revision={3} locale={locale}/>));
 expect(host.textContent).toContain('Reviewed catalog title');expect(enqueueAction).not.toHaveBeenCalled();
 await act(async()=>button(locale==='en'?'Not now':'Nie teraz').click());expect(host.textContent).toBe('');expect(enqueueAction).not.toHaveBeenCalled();
});
it('accepts a reviewed Mission through the owner-scoped non-text queue, locks pending choices and waits for acknowledgement',async()=>{
 const open=vi.fn();
 await act(async()=>root.render(<GameProductContext.Provider value={{games:[],refresh:vi.fn(),openMission:open}}><GameCompletion owner="owner" runId="run" revision={3} locale="en"/></GameProductContext.Provider>));
 expect(button('Add to my hive')).toBeTruthy();
 await act(async()=>button('Choose this Mission').click());
 expect(enqueueAction).toHaveBeenCalledWith(expect.objectContaining({owner:'owner',runId:'run',type:'gameCompletion',payload:{kind:'MISSION',expectedRevision:3,suggestionVersion:1}}));
 expect(button('Choose this Mission').disabled).toBe(true);expect(open).not.toHaveBeenCalled();
 fixture.value.missionAccepted=true;fixture.actions=[];
 await act(async()=>fixture.notify({action:{type:'gameCompletion',owner:'owner',runId:'run'},outcome:'done'}));
 expect(host.textContent).toContain('it is not completed yet');
 await act(async()=>button('Open Missions').click());expect(open).toHaveBeenCalledWith('mission');
});
it('suppresses the cell offer for any game already saved today or an offline cell reservation',async()=>{
 const render=async(pendingCell:boolean,games:{id:string;gameKey:string;completedAt:string}[])=>act(async()=>root.render(<GameProductContext.Provider value={{games,pendingCell,refresh:vi.fn(),openMission:vi.fn()}}><GameCompletion owner="owner" runId="run" revision={3} locale="en"/></GameProductContext.Provider>));
 await render(false,[{id:'mini',gameKey:'hive_builder_tap',completedAt:fixture.value.completedAt}]);expect(button('Add to my hive')).toBeUndefined();
 await render(true,[]);expect(button('Add to my hive')).toBeUndefined();
 await render(false,[]);expect(button('Add to my hive')).toBeTruthy();
 await act(async()=>activateGameOwner('other'));expect(host.textContent).toBe('');
});

it('retries only the original failed owner/run choice after bounded retry exhaustion',async()=>{
 fixture.actions=[{clientId:'same-id',owner:'owner',runId:'run',type:'gameCompletion',status:'failed',attempts:12,payload:{kind:'MISSION',expectedRevision:3,suggestionVersion:1}},
  {clientId:'other',owner:'other',runId:'run',type:'gameCompletion',status:'failed'}];
 await act(async()=>root.render(<GameCompletion owner="owner" runId="run" revision={3} locale="en"/>));
 await act(async()=>button('Check / retry saved choice').click());
 expect(updateAction).toHaveBeenCalledTimes(1);expect(updateAction).toHaveBeenCalledWith(expect.objectContaining({clientId:'same-id',owner:'owner',runId:'run',status:'pending',attempts:0,payload:{kind:'MISSION',expectedRevision:3,suggestionVersion:1}}));
});
