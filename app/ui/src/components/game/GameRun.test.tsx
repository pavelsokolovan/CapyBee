vi.mock('../../game/gameLock',()=>({withGameLock:async(_owner:string,_run:string,work:()=>Promise<unknown>)=>work()}));
import 'fake-indexeddb/auto';
import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import {SceneActions,SceneBlocks,ScenePanels,SceneResult} from './GameScene';
import {GameRun} from './GameRun';
import {decodeSummary} from '../../game/gameDecoders';
import fixtures from '../../game/fixtures/contracts.json';
import {illustratedRun} from '../../game/fixtures/illustratedRun';
import {adventureCopy} from '../../game/adventureCopy';
import type {GameRunSummaryDto} from '../../game/gameApi';
import {RunController,type RunState} from '../../game/runController';
import {GameTransport} from '../../game/gameTransport';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
let host:HTMLDivElement,root:Root;
beforeEach(()=>{sessionStorage.clear();host=document.createElement('div');document.body.append(host);root=createRoot(host);});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();vi.restoreAllMocks();});
const run=()=>decodeSummary(structuredClone(fixtures.dynamic));
async function renderRun(run:GameRunSummaryDto) {
 let state:RunState={run,pending:false,message:'saved'};
 const listeners=new Set<()=>void>();
 const controller={getSnapshot:()=>state,subscribe:(listener:()=>void)=>{
  listeners.add(listener);return()=>{listeners.delete(listener);};
 },initialize:vi.fn(async()=>{}),detach:vi.fn(),submit:vi.fn()};
 await act(async()=>root.render(<GameRun run={run} owner="owner" locale="en" onAnother={vi.fn()}
  controller={controller as unknown as RunController}/>));
 const update=async(patch:Partial<RunState>)=>act(async()=>{
  state={...state,...patch};listeners.forEach(listener=>listener());
 });
 return {submit:controller.submit,update,accept:(run:GameRunSummaryDto)=>update({run})};
}
it('shows a title-less illustrated pack as an expedition and preserves text and focus on travel',async()=>{
 const run=illustratedRun(),{submit,accept}=await renderRun(run);
 const game=host.querySelector('.game-scene-first.game-expedition')!;
 const title=game.querySelector('.game-scene-body > h3')!;
 expect(title.textContent).toBe('Adventure');
 expect(title.classList.contains('game-visually-hidden')).toBe(true);
 expect(title.getAttribute('lang')).toBe('en');
 expect(document.activeElement).toBe(title);
 expect(game.textContent).not.toContain(adventureCopy.en.locale);
 expect(game.textContent).not.toContain(adventureCopy.en.ready);
 expect(game.querySelector('p[role="status"].game-visually-hidden')?.textContent).toBe('Saved');
 const map=game.querySelector<HTMLDetailsElement>('details.game-scene-map')!;
 expect(map.open).toBe(false);
 expect(map.querySelector('summary')?.textContent).toBe('Map');
 expect([...game.querySelectorAll('.game-board')]).toEqual([map.querySelector('.game-board')]);
 const travel=[...map.querySelectorAll<HTMLButtonElement>('[data-place-id] button')];
 expect(travel.map(button=>button.getAttribute('aria-label'))).toEqual(['Visit: Rzeka']);
 expect(game.querySelector<HTMLDetailsElement>('details.game-current-characters')?.open).toBe(false);
 expect(game.querySelector('.game-party')).toBeNull();
 const encounter=game.querySelector('.game-encounter-content')!;
 const text=encounter.textContent;
 const choices=()=>[...game.querySelectorAll('.game-actions button')].map(button=>button.textContent);
 expect(choices()).toEqual(['Hold the reed']);
 const toggle=[...game.querySelectorAll('button')].find(button=>button.textContent==='Text mode')!;
 await act(async()=>toggle.click());
 expect(toggle.getAttribute('aria-pressed')).toBe('true');
 expect(encounter.textContent).toBe(text);
 expect(choices()).toEqual(['Hold the reed']);
 expect(submit).not.toHaveBeenCalled();
 map.open=true;
 await act(async()=>map.querySelector<HTMLButtonElement>('[aria-label="Visit: Rzeka"]')!.click());
 expect(submit).toHaveBeenCalledExactlyOnceWith({type:'MOVE',placeId:'river'});
 const accepted=structuredClone(run);
 accepted.revision++;
 accepted.view.places!.currentPlaceId='river';
 await accept(accepted);
 expect(document.activeElement).toBe(game.querySelector('.game-expedition-location'));
 expect(document.activeElement?.textContent).toBe('Rzeka');
 expect(document.activeElement?.getAttribute('lang')).toBe('pl');
});
it('keeps the fallback moment heading accessible when an illustrated run has no location',async()=>{
 const run=illustratedRun();delete run.view.places;
 await renderRun(run);
 const heading=host.querySelector('.game-encounter h4.game-visually-hidden');
 expect(heading?.textContent).toBe('Current moment');
 expect(heading?.getAttribute('lang')).toBe('en');
});
it('pins up to three actions, counting disabled choices and Continue',async()=>{
 const run=illustratedRun();
 run.view.options.push({id:'disabled',label:'Wait here',enabled:false,hint:'The path is closed.'});
 run.view.canAdvance=true;
 await renderRun(run);
 const footer=host.querySelector('.game-scene-footer')!;
 expect([...footer.querySelectorAll('fieldset button')].map(button=>button.textContent)).toEqual(['Hold the reed','Wait here','Continue']);
 expect(footer.querySelector<HTMLButtonElement>('button[aria-describedby="hint-disabled"]')?.disabled).toBe(true);
 expect(footer.querySelector('#hint-disabled')?.textContent).toBe('The path is closed.');
 expect(host.querySelector('.game-scene-body fieldset')).toBeNull();
});
it.each([[6,'CHOICE'],[1,'CHOICE_OR_TEXT']] as const)('keeps %i choices with %s input below the bubble',async(count,mode)=>{
 const run=illustratedRun();
 run.view.options=Array.from({length:count},(_,index)=>({id:String(index),label:`Option ${index}`,enabled:true}));
 run.view.input={mode,maxChars:35};
 await renderRun(run);
 expect(host.querySelector('.game-scene-footer')).toBeNull();
 const actions=host.querySelector('.game-scene-body .game-encounter + .game-actions')!;
 expect(actions.querySelectorAll('button[type=button]')).toHaveLength(count);
 expect(Boolean(actions.querySelector('textarea'))).toBe(mode==='CHOICE_OR_TEXT');
});
it('keeps recovery controls in the footer while an inline action is locked',async()=>{
 const run=illustratedRun();
 run.view.input={mode:'CHOICE_OR_TEXT',maxChars:35};
 const {update}=await renderRun(run);
 await update({pending:true,retryable:true,cancellable:true,message:'checking'});
 expect(host.querySelector('.game-expedition > [role=status]')?.textContent).toBe('Saving / checking');
 expect(host.querySelector<HTMLFieldSetElement>('.game-scene-body fieldset')?.disabled).toBe(true);
 expect([...host.querySelectorAll('.game-scene-footer button')].map(button=>button.textContent))
  .toEqual(['Retry original action','Remove unsent action','Check saved game']);
});
it('renders ending prose and the result in the body without a footer',async()=>{
 const run=illustratedRun();
 run.status='ENDED';run.view.ended=true;run.view.endReason='ABANDONED';run.view.options=[];
 await renderRun(run);
 expect(host.querySelector('.game-scene-body .game-encounter-content')?.textContent).toContain('We can take our time.');
 expect(host.querySelector('.game-scene-body')?.textContent).toContain('Abandoned');
 expect(host.querySelector('.game-scene-footer')).toBeNull();
});
it('keeps the legacy text layout, status, actions and focus for a run without presentation',async()=>{
 const run=illustratedRun();delete run.view.presentation;
 run.view.places!.places[1].moveBehavior='LOCATION_ONLY';
 await renderRun(run);
 const game=host.querySelector('.game-run')!;
 const title=game.querySelector(':scope > h3')!;
 expect(game.classList.contains('game-scene-first')).toBe(false);
 expect(title.classList.contains('game-visually-hidden')).toBe(false);
 expect(document.activeElement).toBe(title);
 expect(game.textContent).toContain(adventureCopy.en.locale);
 expect(game.textContent).toContain(adventureCopy.en.ready);
 expect(game.querySelector('.game-scene-map')).toBeNull();
 expect(game.querySelector('.game-encounter svg')).toBeNull();
 expect(game.querySelector('.game-actions legend')?.textContent).toBe('Adventure');
 expect([...game.querySelectorAll('button')].some(button=>button.textContent==='Go to: Rzeka'&&!button.disabled)).toBe(true);
});
it('renders escaped ordered prose and resolves only discovered speakers',async()=>{
 const v=run().view!;v.blocks=[{kind:'NARRATION',text:'<img src="https://evil.test" onerror="alert(1)">',refs:[]},{kind:'SPEECH',speaker:'secret',text:'Zażółć\nHello',refs:[]},{kind:'FUTURE',text:'https://evil.test',refs:[]}];
 await act(async()=>root.render(<SceneBlocks view={v} locale="pl"/>));
 expect(host.querySelector('img,a,script')).toBeNull();expect(host.textContent).toContain('<img');expect(host.textContent).not.toContain('secret');expect(host.querySelector('strong')?.textContent).toBe('Postać');
 expect(Array.from(host.querySelectorAll('p')).map(p=>p.textContent)).toEqual(v.blocks.map(b=>b.text));
});
it('uses only enabled options and explicit authoritative advance',async()=>{
 const v=run().view!,submit=vi.fn();v.options=[{id:'legal',label:'Choose',enabled:true},{id:'no',label:'Disabled',enabled:false,hint:'Wait here'}];v.canAdvance=false;
 await act(async()=>root.render(<SceneActions view={v} locale="en" locked={false} submit={submit}/>));
 expect(host.textContent).not.toContain('Continue');expect(host.textContent).toContain('Wait here');expect(host.querySelectorAll('button')[1].disabled).toBe(true);
 await act(async()=>host.querySelector('button')!.click());expect(submit).toHaveBeenCalledWith({type:'CHOOSE',optionId:'legal'});
 v.canAdvance=true;await act(async()=>root.render(<SceneActions view={v} locale="en" locked submit={submit}/>));expect(host.querySelector('fieldset')!.disabled).toBe(true);
});
it('bounds text by code points without truncation and keeps multiline IME input',async()=>{
 const v=run().view!,submit=vi.fn();v.input={mode:'TEXT',maxChars:2};
 await act(async()=>root.render(<SceneActions view={v} locale="en" locked={false} submit={submit}/>));
 const box=host.querySelector('textarea')!;
 const input=async(value:string)=>{await act(async()=>{Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')!.set!.call(box,value);box.dispatchEvent(new Event('input',{bubbles:true}));box.dispatchEvent(new Event('change',{bubbles:true}));});};
 await input('😀😀😀');await act(async()=>host.querySelector('form')!.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
 expect(submit).not.toHaveBeenCalled();expect(box.value).toBe('😀😀😀');expect(host.textContent).toContain('too long');
 await input('😀😀');await act(async()=>box.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',isComposing:true,bubbles:true})));expect(submit).not.toHaveBeenCalled();
 await act(async()=>host.querySelector('form')!.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
 expect(submit).toHaveBeenCalledWith({type:'SUBMIT_TEXT',text:'😀😀'});expect(box.value).toBe('');
});
it('renders informational places but authorizes only availableMoves',async()=>{
 const v=run().view!,submit=vi.fn();v.places={currentPlaceId:'a',places:['a','b','c'].map(id=>({id,label:id,description:'Place',x:null,y:null})),links:[{from:'a',to:'c',bidirectional:true}],availableMoves:['b'],visitedPlaceIds:['a'],layoutKind:'LIST'};
 await act(async()=>root.render(<ScenePanels view={v} locale="en" locked={false} submit={submit}/>));
 const buttons=[...host.querySelectorAll('button')].filter(b=>b.textContent?.startsWith('Go to:'));expect(buttons.map(b=>b.disabled)).toEqual([true,false,true]);await act(async()=>buttons[1].click());expect(submit).toHaveBeenCalledWith({type:'MOVE',placeId:'b'});
});
it('reads legacy and supplied endings without creating results or rewards',async()=>{
 const v=run().view!;v.ended=true;v.endReason='ABANDONED';await act(async()=>root.render(<SceneResult view={v} locale="en"/>));expect(host.textContent).toContain('Abandoned');
});
it('shows safe reading-only state for unsupported controls',async()=>{
 const r=run();delete r.view!.canAdvance;await act(async()=>root.render(<GameRun run={r} owner="owner" locale="en" onAnother={vi.fn()}/>));expect(host.textContent).toContain('needs an update');expect(host.querySelector('fieldset')).toBeNull();
});
function harness(){const r=run();let resolve!:(v:unknown)=>void;
 const t={setLanguage:vi.fn(),detach:vi.fn(),transient:vi.fn(()=>new Promise(r=>{resolve=r;})),read:vi.fn(async()=>({status:200,body:{...r,revision:r.revision+1}})),poll:vi.fn(async()=>{})};
 const controller=new RunController(r,t as unknown as GameTransport,'owner');return{controller,t,r,resolve:async(v:unknown)=>{await vi.waitFor(()=>expect(resolve).toBeTypeOf('function'));resolve(v);}};}
it('locks all commands until accepted summary, reconciles rejected and replayed results',async()=>{
 for(const outcome of ['APPLIED','REPLAYED','REJECTED']){const h=harness();const pending=h.controller.submit({type:'SUBMIT_TEXT',text:'fictional text'});await h.controller.submit({type:'ABANDON'});await vi.waitFor(()=>expect(h.t.transient).toHaveBeenCalledTimes(1));expect(h.controller.getSnapshot().pending).toBe(true);
 await vi.waitFor(()=>expect(h.t.transient).toHaveBeenCalled());const marker=h.t.transient.mock.calls[0] as unknown as [{id:string}];h.resolve({status:200,body:{commandId:marker[0].id,outcome}});await pending;expect(h.controller.getSnapshot().pending).toBe(false);expect(h.controller.getSnapshot().run.revision).toBe(h.r.revision+1);expect(sessionStorage.length).toBe(0);}
});
it('ignores responses after Leave and never rolls revision backward',async()=>{
 const h=harness();const pending=h.controller.submit({type:'SUBMIT_TEXT',text:'fictional text'});await vi.waitFor(()=>expect(h.t.transient).toHaveBeenCalled());h.controller.detach();h.resolve({status:200,body:{}});await pending;expect(h.t.read).not.toHaveBeenCalled();
 const other=harness();other.t.read.mockResolvedValue({status:200,body:{...other.r,revision:0}});const task=other.controller.submit({type:'SUBMIT_TEXT',text:'fictional text'});await vi.waitFor(()=>expect(other.t.transient).toHaveBeenCalled());const marker=other.t.transient.mock.calls[0] as unknown as [{id:string}];other.resolve({status:200,body:{commandId:marker[0].id,outcome:'REPLAYED'}});await task;expect(other.controller.getSnapshot().run.revision).toBe(other.r.revision);expect(other.controller.getSnapshot().pending).toBe(true);
});
it('reconciles conflicts without retargeting the original choice',async()=>{
 const h=harness();const task=h.controller.submit({type:'SUBMIT_TEXT',text:'fictional text'});h.resolve({status:409,body:{error:{code:'REVISION_CONFLICT'}}});await task;
 expect(h.controller.getSnapshot().message).toBe('changed');expect(h.t.transient).toHaveBeenCalledTimes(1);
});
it('restores a body-free pending marker and polls its original ID',async()=>{
 const r=run(),path=`/api/game/runs/${r.runId}/commands`,id='uncertain';sessionStorage.setItem('game-operation:owner:'+id,JSON.stringify({owner:'owner',id,path,runId:r.runId,expectedRevision:r.revision,createdAt:1}));
 const t={setLanguage:vi.fn(),detach:vi.fn(),poll:vi.fn(async(_path:string,_signal:AbortSignal,accept:(reply:{status:number;body:unknown})=>void)=>{accept({status:404,body:{error:{code:'NOT_FOUND'}}});}),read:vi.fn(),transient:vi.fn()};
 const c=new RunController(r,t as unknown as GameTransport,'owner');expect(c.getSnapshot().pending).toBe(true);await c.check();
 expect(t.poll.mock.calls[0][0]).toBe(path+'/'+id);expect(c.getSnapshot().pending).toBe(true);expect(t.transient).not.toHaveBeenCalled();expect(sessionStorage.length).toBe(1);
});
it('reads v1 authored content and v2 result fields in server order',async()=>{
 const legacy=decodeSummary(fixtures.packV1).view!;await act(async()=>root.render(<SceneBlocks view={legacy} locale="pl"/>));expect(host.querySelectorAll('p')).toHaveLength(legacy.blocks.length);
 const v=run().view!;if(!('characters' in v))throw new Error('v2 fixture required');v.result={outcomeId:'end',label:'A thoughtful outcome',resultClass:'PARTIAL_SUCCESS',headline:'A beginning',summary:'Progress',objectives:[{id:'one',label:'Goal',description:'Goal detail',state:'DONE',note:'Remember'}],facts:[{id:'fact',label:'A fact'}],reflection:'What helped?'};
 await act(async()=>root.render(<SceneResult view={v} locale="en"/>));expect(host.textContent).toBe('A beginningProgressA thoughtful outcomeGoalGoal detailDONERememberA factWhat helped?');
});
it('accepts the public place envelope with absent descriptions',()=>{
 const r=run();r.view!.places={currentPlaceId:'cell',places:[{id:'cell',label:'Cell',description:null,x:0,y:0}],links:[],availableMoves:[],visitedPlaceIds:['cell'],layoutKind:'GRAPH'};
 expect(decodeSummary(r).view?.places?.places[0].description).toBeNull();
});
it('keeps controls locked when a replay summary has not reached appliedRevision',async()=>{
 vi.useFakeTimers();try{
  const h=harness();const task=h.controller.submit({type:'SUBMIT_TEXT',text:'fictional text'});await vi.waitFor(()=>expect(h.t.transient).toHaveBeenCalled());const marker=h.t.transient.mock.calls[0] as unknown as [{id:string}];h.resolve({status:200,body:{commandId:marker[0].id,outcome:'REPLAYED',appliedRevision:h.r.revision+2,revision:h.r.revision+2}});await task;
  expect(h.controller.getSnapshot().pending).toBe(true);expect(h.controller.getSnapshot().run.revision).toBe(h.r.revision);h.controller.detach();
 }finally{vi.useRealTimers();}
});
