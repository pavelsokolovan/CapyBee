import {useEffect,useLayoutEffect,useRef,useState,useSyncExternalStore} from 'react';
import type {GameRunSummaryDto,GameCommand} from '../../game/gameApi';
import {GameTransport} from '../../game/gameTransport';
import {RunController} from '../../game/runController';
import {restoreGameSession} from '../../game/restoreGameSession';
import {SceneBlocks,SceneActions,ScenePanels,SceneResult} from './GameScene';
import {adventureCopy} from '../../game/adventureCopy';
import {anonymousNotePackId} from '../../game/anonymousNote';
import {hiveExpeditionPackId} from '../../game/hiveExpedition';
import {adventureLocation,adventureTitle} from '../../game/savedAdventure';
import {EpisodeMap} from './EpisodeMap';
import {runCopy} from './runCopy';
import './gameRun.css';
import {GamePlaces,hexLayout} from './GamePlaces';
import {boardCopy} from './boardCopy';
import {GameEncounter,encounterCopy} from './GameEncounter';
import {GameCompletion} from './GameCompletion';
import {GameCurrentCharacters, GameParty} from './GameCharacters';
export function GameRun({run,owner,locale,onAnother,onClose,controller:provided}:{run:GameRunSummaryDto;owner:string;locale:'en'|'pl';onAnother:()=>void;onClose?:()=>void;controller?:RunController}){
 const [initialized,setInitialized]=useState(false);
 const [textPreference,setTextPreference]=useState<{runId:string;enabled:boolean}|null>(null);
 const [controller]=useState(()=>provided??new RunController(run,new GameTransport(()=>owner,()=>restoreGameSession(owner)),owner));
 const state=useSyncExternalStore(controller.subscribe,controller.getSnapshot),view=state.run.view;
 const episode=state.run.packId===anonymousNotePackId;
 const expedition=state.run.packId===hiveExpeditionPackId;
 const controlsLocale=episode?'pl':locale,c=runCopy[controlsLocale];
 const episodeMap=useRef<HTMLDetailsElement>(null);
 const [saveVisible,setSaveVisible]=useState(false);
 useEffect(()=>{
  if(!episode||!initialized||state.message!=='saved'||state.pending){setSaveVisible(false);return;}
  setSaveVisible(true);
  const timeout=window.setTimeout(()=>setSaveVisible(false),1800);
  return()=>window.clearTimeout(timeout);
 },[episode,initialized,state.message,state.pending,state.run.revision]);
 const textMode=textPreference?.runId===state.run.runId&&textPreference.enabled;
 const heading=useRef<HTMLHeadingElement>(null),runHeading=useRef<HTMLHeadingElement>(null),abandon=useRef<HTMLButtonElement>(null),dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{let alive=true;void controller.initialize().then(()=>{if(alive)setInitialized(true);}).catch(()=>{});return()=>{alive=false;controller.detach();};},[controller]);

 const ended=!!view?.ended||state.run.status==='ENDED';
 const unsupported=!view||typeof view.canAdvance!=='boolean'||!!(view.input?.mode==='TEXT'&&view.options.some(o=>o.enabled))||!!(view.places?.availableMoves.some(id=>!view.places?.places.some(p=>p.id===id)));
 const locked=!initialized||state.pending||state.run.status!=='ACTIVE'||!!view?.ended||unsupported;
 const presentation=view&&'presentation' in view?view.presentation:null;
 const illustrated=!episode&&!!view?.places&&!!presentation&&!!hexLayout(view.places);
 const hasSceneHeading=illustrated||(expedition&&!!view);
 const currentLocation=adventureLocation(view);
 const intention=useRef<{command:GameCommand;revision:number;runId:string;entry:boolean}|null>(null);
 const focusBaseline=useRef<string|null>(null);
 const submit=(command:GameCommand)=>{
  intention.current={command,revision:state.run.revision,runId:state.run.runId,
   entry:command.type==='MOVE'&&view?.places?.places.find(p=>p.id===command.placeId)?.moveBehavior==='ENTER_SCENE'};
  return controller.submit(command);
 };
 useLayoutEffect(()=>{
  if(focusBaseline.current!==state.run.runId){focusBaseline.current=state.run.runId;intention.current=null;(hasSceneHeading?runHeading:heading).current?.focus();return;}
  const requested=intention.current;
  if(!requested||state.pending)return;
  if(state.run.revision<=requested.revision){if(state.message==='rejected')intention.current=null;return;}
  intention.current=null;
  if(state.message!=='saved'||requested.runId!==state.run.runId)return;
  if(episode&&episodeMap.current)episodeMap.current.open=false;
  if(episode||expedition||requested.command.type!=='MOVE'||requested.entry||ended)heading.current?.focus();
  else {
   const cell=[...document.querySelectorAll<HTMLElement>('.game-run [data-place-id]')].find(n=>n.dataset.placeId===view?.places?.currentPlaceId);
   cell?.querySelector<HTMLButtonElement>('button')?.focus();
  }
 },[state.run.runId,state.run.revision,state.pending,state.message,ended,episode,expedition,hasSceneHeading,view?.places?.currentPlaceId]);
 const cancel=()=>{dialog.current?.close();abandon.current?.focus();};
 const title=adventureTitle(state.run)??c.scene;
 const endingLabel=view?.endReason==='ABANDONED' ? c.abandoned : 'Zagadka rozwiązana';
 const textToggle=presentation?<button type="button" lang={controlsLocale} aria-pressed={textMode}
  onClick={()=>setTextPreference({runId:state.run.runId,enabled:!textMode})}>{encounterCopy[controlsLocale].text}</button>:null;
 const quietSave=expedition&&initialized&&state.message==='saved'&&!state.pending;
 const status=<p className={quietSave?'game-run-status game-visually-hidden':'game-run-status'} role="status" aria-live="polite" lang={controlsLocale}>{initialized
  ? (!episode||state.message!=='saved'||saveVisible ? c[state.message] : '') : c.checking}</p>;
 const actions=view&&initialized&&(!locked||state.pending)?<SceneActions key={state.run.revision} view={view}
  locale={controlsLocale} locked={locked} selection={state.selection} submit={submit}/>:null;
 const board=(illustrated||expedition)&&view?.places?.places.length?<GamePlaces places={view.places} presentation={presentation} compact={expedition}
  runId={state.run.runId} revision={state.run.revision} pending={state.pending} forceList={textMode}
  locale={locale} locked={locked&&!ended} submit={submit}/>:null;
 const party=view?<GameParty view={view} locale={locale} runId={state.run.runId} revision={state.run.revision}
  ready={initialized} announce={initialized&&!locked&&state.message==='saved'} hideEmpty={expedition}/>:null;
 const runClass=episode?'game-run game-scene-first game-episode':expedition?'game-run game-scene-first game-expedition':'game-run';
 return <section className={runClass} data-text-mode={textMode} lang={state.run.locale}>
 <h3 tabIndex={-1} ref={hasSceneHeading?runHeading:heading}>{title}</h3>
 {!episode?<>{!expedition?<><p>{adventureCopy[locale].locale}: {new Intl.DisplayNames([locale],{type:'language'}).of(state.run.locale)}</p>
 {initialized&&state.message==='saved'?<p>{adventureCopy[locale].ready}</p>:null}</>:null}{status}</>:null}
 {unsupported?<p>{c.unsupported}</p>:null}{state.run.status!=='ACTIVE'&&!ended?<p>{c.unavailable}</p>:null}
 {view?episode?<><div className="game-scene-tools">
 {view.places?.places.length&&!ended?<EpisodeMap places={view.places} locked={locked} submit={submit} details={episodeMap}/>:null}
 {textToggle}</div>
 <GameEncounter view={view} stage>
 {ended?<h4>{endingLabel}</h4>:null}
 <SceneBlocks view={view} locale={controlsLocale}/>{actions}
 {ended?<button type="button" className="game-episode-exit" onClick={onClose??onAnother}>{c.back}</button>:null}
 </GameEncounter>{status}</>:<>
 {expedition?<div className="game-scene-tools">
  {board?<details className="game-scene-map"><summary lang={locale}>{boardCopy[locale].map}</summary>{board}</details>:null}
  {textToggle}
 </div>:<>{textToggle}{board}{party}</>}
 {hasSceneHeading?<h4 className={expedition?'game-expedition-location':undefined} tabIndex={-1} ref={heading}
  lang={expedition&&currentLocation?state.run.locale:locale}>{expedition&&currentLocation?currentLocation:boardCopy[locale].moment}</h4>:null}
 <GameEncounter view={view} stage={expedition}>
 {!expedition?<GameCurrentCharacters view={view} locale={locale}/>:null}
 <SceneBlocks view={view} locale={locale}/>{actions}
 </GameEncounter>
 {expedition?<><GameCurrentCharacters view={view} locale={locale} collapsible/>{party}</>:null}
 <ScenePanels hidePlaces={illustrated||expedition} view={view} locale={locale} locked={locked} submit={submit}/>{ended?<SceneResult view={view} locale={locale}/>:null}</>:episode?status:null}
 {state.retryable?<button type="button" onClick={()=>void controller.retryUnsent()}>{c.retry}</button>:null}
 {state.cancellable?<button type="button" onClick={()=>void controller.removeUnsent()}>{c.remove}</button>:null}
 {state.pending?<button type="button" onClick={()=>void controller.check()}>{c.check}</button>:null}
 {!episode&&ended&&view?.endReason==='COMPLETED'?<GameCompletion owner={owner} runId={state.run.runId} revision={state.run.revision} locale={locale}/>:null}
 {!episode&&ended?<button type="button" onClick={onAnother}>{c.newGame}</button>:null}
 {state.run.status==='ACTIVE'?<button type="button" ref={abandon} disabled={locked} onClick={()=>dialog.current?.showModal()}>{c.abandon}</button>:null}
 <dialog onKeyDown={e=>e.stopPropagation()} ref={dialog} aria-labelledby="game-abandon-title" onCancel={e=>{e.preventDefault();cancel();}}><h4 id="game-abandon-title">{c.confirm}</h4><button type="button" onClick={cancel}>{c.cancel}</button><button type="button" disabled={locked} onClick={()=>{cancel();void submit({type:'ABANDON'});}}>{c.abandon}</button></dialog></section>;
}
