import {useEffect,useLayoutEffect,useRef,useState,useSyncExternalStore} from 'react';
import type {GameRunSummaryDto,GameCommand} from '../../game/gameApi';
import {GameTransport} from '../../game/gameTransport';
import {RunController} from '../../game/runController';
import {restoreGameSession} from '../../game/restoreGameSession';
import {SceneBlocks,SceneActions,ScenePanels,SceneResult,hasTextInput} from './GameScene';
import {adventureCopy} from '../../game/adventureCopy';
import {adventureLocation,adventureTitle} from '../../game/savedAdventure';
import {hiveCapyExpression} from '../../game/hiveCapyExpression';
import {visitedHiveChoices} from '../../game/hiveExpedition';
import {completedHivePlaces} from '../../game/hiveCompletion';
import {sceneFirstKind} from '../../game/sceneFirst';
import {focusScene,resetSceneScroll} from '../../game/sceneScroll';
import {EpisodeInterest} from './EpisodeInterest';
import {EpisodeMap} from './EpisodeMap';
import {runCopy} from './runCopy';
import './gameRun.css';
import {ExpeditionMap} from './ExpeditionMap';
import {boardCopy} from './boardCopy';
import {GameEncounter,encounterCopy} from './GameEncounter';
import {GameCompletion} from './GameCompletion';
import {GameCurrentCharacters, GameParty} from './GameCharacters';
export function GameRun({run,owner,locale,onAnother,onClose,controller:provided}:{run:GameRunSummaryDto;owner:string;locale:'en'|'pl';onAnother:()=>void;onClose?:()=>void;controller?:RunController}){
 const [initialized,setInitialized]=useState(false);
 const [textPreference,setTextPreference]=useState<{runId:string;enabled:boolean}|null>(null);
 const [controller]=useState(()=>provided??new RunController(run,new GameTransport(()=>owner,()=>restoreGameSession(owner)),owner));
 const state=useSyncExternalStore(controller.subscribe,controller.getSnapshot),view=state.run.view;
 const sceneKind=sceneFirstKind(state.run);
 const episode=sceneKind==='episode',expedition=sceneKind==='expedition',detective=sceneKind==='detective';
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
 const section=useRef<HTMLElement>(null),sceneBody=useRef<HTMLDivElement>(null);
 const heading=useRef<HTMLHeadingElement>(null),runHeading=useRef<HTMLHeadingElement>(null),abandon=useRef<HTMLButtonElement>(null),dialog=useRef<HTMLDialogElement>(null);
 const mapDialog=useRef<HTMLDialogElement>(null),mapButton=useRef<HTMLButtonElement>(null);
 const menu=useRef<HTMLDetailsElement>(null),menuSummary=useRef<HTMLElement>(null);
 const pendingFills=useRef(new Set<string>());
 const [animatedFills,setAnimatedFills]=useState<string[]>([]);
 const encounter=useRef<HTMLDivElement>(null);
 const [mapOpen,setMapOpen]=useState(false),[selectedPlace,setSelectedPlace]=useState<string|null>(null);
 useEffect(()=>{let alive=true;void controller.initialize().then(()=>{if(alive)setInitialized(true);}).catch(()=>{});return()=>{alive=false;controller.detach();};},[controller]);

 const ended=!!view?.ended||state.run.status==='ENDED';
 const episodeCompleted=episode&&state.run.status==='ENDED'&&view?.endReason==='COMPLETED';
 const endingHeading=useRef<HTMLHeadingElement>(null);
 const unsupported=!view||typeof view.canAdvance!=='boolean'||!!(view.input?.mode==='TEXT'&&view.options.some(o=>o.enabled))||!!(view.places?.availableMoves.some(id=>!view.places?.places.some(p=>p.id===id)));
 const locked=!initialized||state.pending||state.run.status!=='ACTIVE'||!!view?.ended||unsupported;
 const presentation=view?.presentation;
 const places=view?.places;
 const mapAvailable=expedition&&!!places?.places.length;
 const hasSceneHeading=expedition&&!!view;
 const currentLocation=adventureLocation(view);
 const intention=useRef<{command:GameCommand;revision:number;runId:string;entry:boolean;completedPlaces:string[]}|null>(null);
 const focusBaseline=useRef<string|null>(null);
 const submit=(command:GameCommand)=>{
  intention.current={command,revision:state.run.revision,runId:state.run.runId,
   completedPlaces:expedition?completedHivePlaces(state.run):[],
   entry:command.type==='MOVE'&&view?.places?.places.find(p=>p.id===command.placeId)?.moveBehavior==='ENTER_SCENE'};
  return controller.submit(command);
 };
 useLayoutEffect(()=>{
  const overlay=section.current?.closest<HTMLElement>('.adventure-overlay')??null;
  const scroller=expedition?sceneBody.current:overlay;
  if(focusBaseline.current!==state.run.runId){
   focusBaseline.current=state.run.runId;
   intention.current=null;
   pendingFills.current.clear();
   setAnimatedFills([]);
   if(expedition)resetSceneScroll(overlay);
   focusScene((episodeCompleted?endingHeading:hasSceneHeading?runHeading:heading).current,scroller);
   return;
  }
  const requested=intention.current;
  if(!requested||state.pending)return;
  const feedback=()=>{
   for(const place of completedHivePlaces(state.run)){
    if(!requested.completedPlaces.includes(place))pendingFills.current.add(place);
   }
   if(requested.command.type!=='ABANDON'&&!window.matchMedia('(prefers-reduced-motion: reduce)').matches){
    encounter.current?.animate?.([{opacity:.78},{opacity:1}],{duration:220,easing:'ease-out'});
   }
  };
  if(expedition&&requested.command.type==='MOVE'){
   const accepted=state.message==='saved'&&state.run.revision>requested.revision;
   if(!accepted&&state.message!=='rejected'&&state.message!=='changed')return;
   intention.current=null;
   if(accepted&&requested.entry){
    feedback();
    setAnimatedFills([]);
    mapDialog.current?.close();
    setMapOpen(false);
    focusScene(heading.current,scroller);
   }else{
    setSelectedPlace(places?.currentPlaceId??null);
    if(mapDialog.current?.open)mapDialog.current.querySelector<HTMLButtonElement>('button[aria-current="location"]')?.focus();
   }
   return;
  }
  if(state.run.revision<=requested.revision){if(state.message==='rejected')intention.current=null;return;}
  intention.current=null;
  if(state.message!=='saved'||requested.runId!==state.run.runId)return;
  if(expedition)feedback();
  if(episode&&episodeMap.current)episodeMap.current.open=false;
  if(episodeCompleted)focusScene(endingHeading.current,scroller);
  else if(episode||expedition||requested.command.type!=='MOVE'||requested.entry||ended)focusScene(heading.current,scroller);
  else {
   const cell=[...document.querySelectorAll<HTMLElement>('.game-run [data-place-id]')].find(n=>n.dataset.placeId===view?.places?.currentPlaceId);
   cell?.querySelector<HTMLButtonElement>('button')?.focus();
  }
 },[state.run,state.pending,state.message,ended,episode,episodeCompleted,expedition,hasSceneHeading,places?.currentPlaceId]);
 const closeMenu=()=>{if(menu.current)menu.current.open=false;menuSummary.current?.focus();};
 const openMap=()=>{
  if(!mapDialog.current)return;
  if(menu.current)menu.current.open=false;
  setSelectedPlace(places?.currentPlaceId??null);
  setAnimatedFills([...pendingFills.current]);
  pendingFills.current.clear();
  mapDialog.current.showModal();
  setMapOpen(true);
  mapDialog.current.querySelector('h3')?.focus();
 };
 const closeMap=()=>{mapDialog.current?.close();setMapOpen(false);setAnimatedFills([]);mapButton.current?.focus();};
 const cancel=()=>{dialog.current?.close();(expedition?menuSummary:abandon).current?.focus();};
 const title=adventureTitle(state.run);
 const endingLabel=view?.endReason==='ABANDONED' ? c.abandoned : 'Zagadka rozwiązana';
 const textToggle=!expedition&&presentation?<button type="button" lang={controlsLocale} aria-pressed={textMode}
  onClick={()=>setTextPreference({runId:state.run.runId,enabled:!textMode})}>{encounterCopy[controlsLocale].text}</button>:null;
 const quietSave=expedition&&initialized&&state.message==='saved'&&!state.pending;
 const status=<p className={quietSave?'game-run-status game-visually-hidden':'game-run-status'} role="status" aria-live="polite" lang={controlsLocale}>{initialized
  ? (!episode||state.message!=='saved'||saveVisible ? c[state.message] : '') : c.checking}</p>;
 const actions=view&&initialized&&(!locked||state.pending)?<SceneActions key={state.run.revision} view={view}
  locale={controlsLocale} locked={locked} selection={state.selection} visitedChoices={visitedHiveChoices(state.run)} submit={submit}/>:null;
 const party=view&&!detective?<GameParty view={view} locale={locale} runId={state.run.runId} revision={state.run.revision}
  ready={initialized} announce={initialized&&!locked&&state.message==='saved'} hideEmpty={expedition}/>:null;
 const runClass=episode?'game-run game-scene-first game-episode':expedition?'game-run game-scene-first game-expedition':detective?'game-run game-scene-first game-detective':'game-run';
 const titleHeading=<h3 tabIndex={-1} ref={hasSceneHeading?runHeading:heading}
  className={expedition&&title===null?'game-visually-hidden':undefined}
  lang={expedition&&title===null?controlsLocale:undefined}>{title??c.scene}</h3>;
 const notices=<>{unsupported?<p>{c.unsupported}</p>:null}
  {state.run.status!=='ACTIVE'&&!ended?<p>{c.unavailable}</p>:null}</>;
 const recovery=state.retryable||state.cancellable||state.pending?<>
  {state.retryable?<button type="button" onClick={()=>void controller.retryUnsent()}>{c.retry}</button>:null}
  {state.cancellable?<button type="button" onClick={()=>void controller.removeUnsent()}>{c.remove}</button>:null}
  {state.pending?<button type="button" onClick={()=>void controller.check()}>{c.check}</button>:null}
 </>:null;
 const completion=!episode&&ended?<>{view?.endReason==='COMPLETED'?<GameCompletion key={`${owner}:${state.run.runId}`} owner={owner} runId={state.run.runId} revision={state.run.revision} locale={locale}/>:null}
  <button type="button" onClick={onAnother}>{c.newGame}</button></>:null;
 const exit=<>
  {!expedition&&state.run.status==='ACTIVE'?<button type="button" ref={abandon} disabled={locked} onClick={()=>dialog.current?.showModal()}>{c.abandon}</button>:null}
  <dialog onKeyDown={e=>e.stopPropagation()} ref={dialog} aria-labelledby="game-abandon-title" onCancel={e=>{e.preventDefault();cancel();}}><h4 id="game-abandon-title">{c.confirm}</h4><button type="button" onClick={cancel}>{c.cancel}</button><button type="button" disabled={locked} onClick={()=>{cancel();void submit({type:'ABANDON'});}}>{c.abandon}</button></dialog>
 </>;

 if(expedition) {
  const capyExpression=hiveCapyExpression(state.run);
  const actionCount=view?view.options.length+Number(view.canAdvance===true):0;
  const pinned=actions&&view&&!ended&&!hasTextInput(view)&&actionCount>=1&&actionCount<=3;
  const caption=<h4 className={currentLocation?'game-expedition-location':'game-visually-hidden'} tabIndex={-1} ref={heading}
   lang={currentLocation?state.run.locale:locale}>{currentLocation??boardCopy[locale].moment}</h4>;
  const canAbandon=state.run.status==='ACTIVE';
  return <section ref={section} className={runClass} lang={state.run.locale}>
   {mapAvailable||canAbandon?<div className="game-scene-tools" lang={locale}>
    {mapAvailable?<button className="game-map-button" type="button" aria-haspopup="dialog" ref={mapButton} onClick={openMap}>
     <span aria-hidden="true">🗺</span> {boardCopy[locale].map}
    </button>:null}
    {canAbandon?<details className="game-scene-menu" ref={menu}
     onKeyDown={event=>{if(event.key==='Escape'&&event.currentTarget.open){event.preventDefault();event.stopPropagation();closeMenu();}}}>
     <summary ref={menuSummary}><span aria-hidden="true">⋯</span> {boardCopy[locale].menu}</summary>
     <div className="game-scene-menu-items">
      <button type="button" disabled={locked} onClick={()=>{closeMenu();dialog.current?.showModal();}}>{c.abandon}</button>
     </div>
    </details>:null}
   </div>:null}
   {mapAvailable&&mapOpen?null:status}
   {mapAvailable&&places?<ExpeditionMap places={places} presentation={presentation} locale={locale} dialog={mapDialog}
    selectedId={selectedPlace} onSelect={setSelectedPlace} onClose={closeMap}
    pending={state.pending} locked={locked} status={mapOpen?status:null} submit={submit}
    completedPlaces={completedHivePlaces(state.run)} animatedFills={animatedFills}/>:null}
   <div className="game-scene-body" ref={sceneBody}>
    {titleHeading}{notices}
    {view?<>
    <GameEncounter view={view} stage caption={caption} encounterRef={encounter} capyExpression={capyExpression}>
     <SceneBlocks key={JSON.stringify([state.run.runId,view.blocks])} view={view} locale={controlsLocale}
      progressive={!!presentation}/>
    </GameEncounter>
    {pinned?null:actions}
    <GameCurrentCharacters view={view} locale={locale} collapsible capyExpression={capyExpression}/>{party}
    <ScenePanels hidePlaces view={view} locale={locale} locked={locked} submit={submit}/>
    {ended?<SceneResult view={view} locale={locale}/>:null}</>:null}
    {completion}{exit}
   </div>
   {pinned||recovery?<footer className="game-scene-footer">{recovery}{pinned?actions:null}</footer>:null}
  </section>;
 }

 return <section ref={section} className={runClass} data-text-mode={textMode} lang={state.run.locale}>
  {titleHeading}
  {!episode&&(!detective||state.run.locale!==locale)?<p>{adventureCopy[locale].locale}: {new Intl.DisplayNames([locale],{type:'language'}).of(state.run.locale)}</p>:null}
  {!episode&&!detective?<>{initialized&&state.message==='saved'?<p>{adventureCopy[locale].ready}</p>:null}{status}</>:null}
  {notices}
  {detective?<div className="game-scene-tools game-detective-tools">{textToggle}{status}</div>:null}
  {view?episode?<><div className="game-scene-tools">
   {view.places?.places.length&&!ended?<EpisodeMap places={view.places} locked={locked} submit={submit} details={episodeMap}/>:null}
   {textToggle}</div>
   <GameEncounter view={view} stage>
    {ended?<h4 className="game-episode-ending-title" tabIndex={-1} ref={endingHeading}>{endingLabel}</h4>:null}
    <SceneBlocks view={view} locale={controlsLocale}/>{actions}
    {episodeCompleted?<EpisodeInterest key={state.run.runId}/>:null}
    {ended?<button type="button" className={episodeCompleted?'game-episode-return':'game-episode-exit'} onClick={onClose??onAnother}>{c.back}</button>:null}
   </GameEncounter>{status}</>:<>
   {party}
   <GameEncounter view={view} stage={detective} textMode={detective&&textMode}>
    {!detective?<GameCurrentCharacters view={view} locale={locale}/>:null}
    <SceneBlocks view={view} locale={locale}/>{actions}
   </GameEncounter>
   {detective?<GameCurrentCharacters view={view} locale={locale} collapsible/>:null}
   <ScenePanels view={view} locale={locale} locked={locked} submit={submit} caseNotes={detective}/>{ended?<SceneResult view={view} locale={locale}/>:null}
  </>:episode?status:null}
  {recovery}{completion}{exit}
 </section>;
}
