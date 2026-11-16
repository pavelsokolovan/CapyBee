import {useEffect,useLayoutEffect,useRef,useState,useSyncExternalStore} from 'react';
import type {GameRunSummaryDto,GameCommand} from '../../game/gameApi';
import {GameTransport} from '../../game/gameTransport';
import {RunController} from '../../game/runController';
import {restoreGameSession} from '../../game/restoreGameSession';
import {SceneBlocks,SceneActions,ScenePanels,SceneResult} from './GameScene';
import {adventureCopy} from '../../game/adventureCopy';
import {runCopy} from './runCopy';
import './gameRun.css';
import {GamePlaces,hexLayout} from './GamePlaces';
import {boardCopy} from './boardCopy';
import {GameCompletion} from './GameCompletion';
import {GameCurrentCharacters, GameParty} from './GameCharacters';
export function GameRun({run,owner,locale,onAnother,controller:provided}:{run:GameRunSummaryDto;owner:string;locale:'en'|'pl';onAnother:()=>void;controller?:RunController}){
 const [initialized,setInitialized]=useState(false);
 const [controller]=useState(()=>provided??new RunController(run,new GameTransport(()=>owner,()=>restoreGameSession(owner)),owner));
 const state=useSyncExternalStore(controller.subscribe,controller.getSnapshot),c=runCopy[locale],view=state.run.view;
 const heading=useRef<HTMLHeadingElement>(null),runHeading=useRef<HTMLHeadingElement>(null),abandon=useRef<HTMLButtonElement>(null),dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{let alive=true;void controller.initialize().then(()=>{if(alive)setInitialized(true);}).catch(()=>{});return()=>{alive=false;controller.detach();};},[controller]);

 const ended=!!view?.ended||state.run.status==='ENDED';
 const unsupported=!view||typeof view.canAdvance!=='boolean'||!!(view.input?.mode==='TEXT'&&view.options.some(o=>o.enabled))||!!(view.places?.availableMoves.some(id=>!view.places?.places.some(p=>p.id===id)));
 const locked=!initialized||state.pending||state.run.status!=='ACTIVE'||!!view?.ended||unsupported;
 const presentation=view&&'presentation' in view?view.presentation:null;
 const illustrated=!!view?.places&&!!presentation&&!!hexLayout(view.places);
 const intention=useRef<{command:GameCommand;revision:number;runId:string;entry:boolean}|null>(null);
 const focusBaseline=useRef<string|null>(null);
 const submit=(command:GameCommand)=>{
  intention.current={command,revision:state.run.revision,runId:state.run.runId,
   entry:command.type==='MOVE'&&view?.places?.places.find(p=>p.id===command.placeId)?.moveBehavior==='ENTER_SCENE'};
  return controller.submit(command);
 };
 useLayoutEffect(()=>{
  if(focusBaseline.current!==state.run.runId){focusBaseline.current=state.run.runId;intention.current=null;(illustrated?runHeading:heading).current?.focus();return;}
  const requested=intention.current;
  if(!requested||state.pending)return;
  if(state.run.revision<=requested.revision){if(state.message==='rejected')intention.current=null;return;}
  intention.current=null;
  if(state.message!=='saved'||requested.runId!==state.run.runId)return;
  if(requested.command.type!=='MOVE'||requested.entry||ended)heading.current?.focus();
  else {
   const cell=[...document.querySelectorAll<HTMLElement>('.game-run [data-place-id]')].find(n=>n.dataset.placeId===view?.places?.currentPlaceId);
   cell?.querySelector<HTMLButtonElement>('button')?.focus();
  }
 },[state.run.runId,state.run.revision,state.pending,state.message,ended]);
 const cancel=()=>{dialog.current?.close();abandon.current?.focus();};
 return <section className="game-run" lang={state.run.locale}><h3 tabIndex={-1} ref={illustrated?runHeading:heading}>{view&&'story' in view?view.story?.title??c.scene:c.scene}</h3>
 <p>{adventureCopy[locale].locale}: {new Intl.DisplayNames([locale],{type:'language'}).of(state.run.locale)}</p>
 {initialized&&state.message==='saved'?<p>{adventureCopy[locale].ready}</p>:null}<p role="status" aria-live="polite">{initialized?c[state.message]:c.checking}</p>{unsupported?<p>{c.unsupported}</p>:null}{state.run.status!=='ACTIVE'&&!ended?<p>{c.unavailable}</p>:null}
 {view?<>{illustrated?<GamePlaces places={view.places!} presentation={presentation} runId={state.run.runId} revision={state.run.revision} pending={state.pending} locale={locale} locked={locked&&!ended} submit={submit}/>:null}<GameParty view={view} locale={locale} runId={state.run.runId} revision={state.run.revision} ready={initialized} announce={initialized&&!locked&&state.message==='saved'}/>
 {illustrated?<h4 tabIndex={-1} ref={heading} lang={locale}>{boardCopy[locale].moment}</h4>:null}<SceneBlocks view={view} locale={locale}/><GameCurrentCharacters view={view} locale={locale}/>{initialized&&(!locked||state.pending)?<SceneActions key={state.run.revision} view={view} locale={locale} locked={locked} selection={state.selection} submit={submit}/>:null}
 <ScenePanels hidePlaces={illustrated} view={view} locale={locale} locked={locked} submit={submit}/>{ended?<SceneResult view={view} locale={locale}/>:null}</>:null}
 {state.retryable?<button type="button" onClick={()=>void controller.retryUnsent()}>{c.retry}</button>:null}
 {state.cancellable?<button type="button" onClick={()=>void controller.removeUnsent()}>{c.remove}</button>:null}
 {state.pending?<button type="button" onClick={()=>void controller.check()}>{c.check}</button>:null}
 {ended&&view?.endReason==='COMPLETED'?<GameCompletion owner={owner} runId={state.run.runId} revision={state.run.revision} locale={locale}/>:null}
 {ended?<button type="button" onClick={onAnother}>{c.newGame}</button>:null}
 {state.run.status==='ACTIVE'?<button type="button" ref={abandon} disabled={locked} onClick={()=>dialog.current?.showModal()}>{c.abandon}</button>:null}
 <dialog onKeyDown={e=>e.stopPropagation()} ref={dialog} aria-labelledby="game-abandon-title" onCancel={e=>{e.preventDefault();cancel();}}><h4 id="game-abandon-title">{c.confirm}</h4><button type="button" onClick={cancel}>{c.cancel}</button><button type="button" disabled={locked} onClick={()=>{cancel();void submit({type:'ABANDON'});}}>{c.abandon}</button></dialog></section>;
}
