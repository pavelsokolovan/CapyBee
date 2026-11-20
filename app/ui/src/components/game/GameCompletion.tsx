import {useContext,useEffect,useRef,useState} from 'react';
import {GameTransport} from '../../game/gameTransport';
import {restoreGameSession} from '../../game/restoreGameSession';
import {ownerAllowed,watchGameOwner} from '../../game/gameOwner';
import {decodeCompletion,type GameCompletion as Completion} from '../../game/gameCompletion';
import {enqueueAction,listQueuedActions,updateAction} from '../../offline/queueStore';
import {flushQueue,onSynced} from '../../offline/syncEngine';
import {hasGameCellOnDay} from '../../game/dailyGameCells';
import {useLocalCalendar} from '../../hooks/useLocalCalendar';
import {GameProductContext} from './GameProductContext';
const copy={
 en:{title:'A small step, if you like',rest:'You can rest here. There is no Mission to accept.',accept:'Choose this Mission',dismiss:'Not now',selected:'Mission chosen. Do it in real life when you are ready; it is not completed yet.',open:'Open Missions',cell:'Add to my hive',saved:'Added to your hive',pending:'Waiting for the saved choice',retry:'Check / retry saved choice',error:'Check the saved adventure before choosing again.'},
 pl:{title:'Mały krok, jeśli chcesz',rest:'Możesz tu odpocząć. Nie ma Misji do przyjęcia.',accept:'Wybierz tę Misję',dismiss:'Nie teraz',selected:'Misja wybrana. Wykonaj ją w prawdziwym świecie, kiedy zechcesz; nie jest jeszcze ukończona.',open:'Otwórz Misje',cell:'Dodaj do mojego ula',saved:'Dodano do twojego ula',pending:'Czekamy na zapisaną decyzję',retry:'Sprawdź / ponów zapisaną decyzję',error:'Sprawdź zapisaną przygodę, zanim wybierzesz ponownie.'}
};
export function GameCompletion({owner,runId,revision,locale}:{owner:string;runId:string;revision:number;locale:'en'|'pl'}) {
 const product=useContext(GameProductContext),now=useLocalCalendar(),c=copy[locale];
 const [value,setValue]=useState<Completion|null>(null),[pending,setPending]=useState(false),[dismissed,setDismissed]=useState(false),[error,setError]=useState(false);
 const [animateCell,setAnimateCell]=useState(false);
 const localCellClaim=useRef(false);
 const loadRef=useRef<()=>void>(()=>{});
 useEffect(()=>{
  const transport=new GameTransport(()=>owner,()=>restoreGameSession(owner));transport.setLanguage(locale);
  const abort=new AbortController();let alive=true;
  const load=()=>{if(!ownerAllowed(owner))return;void transport.poll(`/api/game/runs/${runId}/completion`,abort.signal,reply=>{
   if(!alive||!ownerAllowed(owner))return;
   if(reply.status===200){
    try{
     const accepted=decodeCompletion(reply.body);
     if(accepted.runId!==runId||accepted.revision!==revision)throw new Error();
     if(accepted.cellAdded&&localCellClaim.current){
      localCellClaim.current=false;
      setAnimateCell(true);
     }
     setValue(accepted);setError(false);
    }catch{setError(true);}
   }else if(reply.status!==202){
    setAnimateCell(false);setValue(null);
   }
  }).catch(()=>{if(alive)setError(true);});
  void listQueuedActions().then(actions=>{if(alive)setPending(actions.some(a=>a.type==='gameCompletion'&&a.owner===owner&&a.runId===runId));}).catch(()=>{if(alive){setPending(true);setError(true);}});};
  loadRef.current=load;load();
  const synced=onSynced(event=>{if(event.action.type==='gameCompletion'&&event.action.owner===owner&&event.action.runId===runId){setError(event.outcome!=='done');load();product?.refresh();}});
  const updated=(event:Event)=>{const v=(event as CustomEvent).detail;if(v.owner===owner&&v.runId===runId)load();};
  window.addEventListener('game-revision',updated);window.addEventListener('online',load);window.addEventListener('focus',load);
  const unwatch=watchGameOwner(()=>{if(!ownerAllowed(owner)){abort.abort();setValue(null);setPending(false);}});
  return()=>{alive=false;abort.abort();transport.detach();synced();unwatch();window.removeEventListener('game-revision',updated);window.removeEventListener('online',load);window.removeEventListener('focus',load);};
 // Product callbacks are read via the surrounding owner-scoped screen; they do not own transport.
 },[owner,runId,revision,locale]);
 const choose=async(kind:'CELL'|'MISSION')=>{
  if(!value||pending||!ownerAllowed(owner))return;
  if(kind==='CELL'&&(!product||product.pendingCell||hasGameCellOnDay(product.games,new Date(value.completedAt))))return;
  setPending(true);setError(false);
  try {await enqueueAction({clientId:crypto.randomUUID(),owner,runId,completedAt:value.completedAt,type:'gameCompletion',method:'POST',path:'',
    payload:{kind,expectedRevision:revision,suggestionVersion:value.suggestionVersion},createdAt:Date.now()});
  if(kind==='CELL')localCellClaim.current=true;
  await flushQueue();}
  catch{if(ownerAllowed(owner)){setError(true);setPending(false);}}
 };
 const retry=async()=>{
  if(!ownerAllowed(owner))return;
  for(const action of await listQueuedActions())if(action.type==='gameCompletion'&&action.owner===owner&&action.runId===runId&&action.status==='failed'){
   await updateAction({...action,status:'pending',attempts:0,notBefore:0});
  }
  await flushQueue();loadRef.current();
 };
 if(!value||dismissed)return null;
 // Completion timestamps come from the server. Local calendar policy only controls the offer.
 const canAdd=product&&!product.pendingCell&&!value.cellAdded&&!hasGameCellOnDay(product.games,new Date(value.completedAt))
   &&hasGameCellOnDay([{id:runId,gameKey:'capybee_rpg',completedAt:value.completedAt}],now);
 return <section className="game-completion" aria-label={c.title}><h4>{c.title}</h4>
  {value.mission?<><p>{value.mission.title}</p><p>{value.mission.timeHint}</p>
   {value.missionAccepted?<><p>{c.selected}</p>{product?<button type="button" onClick={()=>product.openMission(value.mission!.id)}>{c.open}</button>:null}</>
    :<button type="button" disabled={pending} onClick={()=>void choose('MISSION')}>{c.accept}</button>}</>:<p>{c.rest}</p>}
  <div role="status" className="game-cell-status">{value.cellAdded?<p data-new-cell={animateCell||undefined}>
   <span className="game-cell-glyph" aria-hidden="true"/>{c.saved}
  </p>:null}</div>
  {!value.cellAdded&&canAdd?<button type="button" disabled={pending} onClick={()=>void choose('CELL')}>{c.cell}</button>:null}
  {pending?<p role="status">{c.pending}</p>:null}{error?<p role="status">{c.error}</p>:null}
  {pending||error?<button type="button" onClick={()=>void retry().catch(()=>{if(ownerAllowed(owner))setError(true);})}>{c.retry}</button>:null}
  <button type="button" onClick={()=>setDismissed(true)}>{c.dismiss}</button>
 </section>;
}
