import {useEffect,useLayoutEffect,useRef,useState} from 'react';
import type {GameView,GameCommand} from '../../game/gameApi';
import {runCopy} from './runCopy';
import {GamePlaces} from './GamePlaces';
import {GameCharacterHistory} from './GameCharacters';
type Props={hidePlaces?:boolean;view:GameView;locale:'en'|'pl';locked:boolean;selection?:string;submit:(command:GameCommand)=>void};
export function hasTextInput(view:GameView) {
 return view.input?.mode==='TEXT'||view.input?.mode==='CHOICE_OR_TEXT';
}
const readMore = {en:'Read more',pl:'Czytaj dalej'} as const;
export function SceneBlocks({view,locale,progressive=false}:{view:GameView;locale:'en'|'pl';progressive?:boolean}) {
 const [index,setIndex]=useState(0);
 const finalBlock=useRef<HTMLDivElement>(null);
 const focusFinal=useRef(false);
 const cast='characters' in view?view.characters:[];
 const lastIndex=view.blocks.length-1;
 const paging=progressive&&lastIndex>0;
 useLayoutEffect(()=>{
  if(focusFinal.current&&index===lastIndex){
   focusFinal.current=false;
   finalBlock.current?.focus({preventScroll:true});
  }
 },[index,lastIndex]);
 return <div className={paging?'game-progressive-text':undefined}>{view.blocks.map((block,i)=>{
  const speaker='speaker' in block?block.speaker:'actorId' in block?block.actorId:undefined;
  const final=paging&&i===lastIndex;
  return <div key={i} className="game-block" data-inactive={paging&&i!==index?true:undefined}
   ref={final?finalBlock:undefined} tabIndex={final?-1:undefined}>
   {block.kind==='SPEECH'?<strong>{cast.find(c=>c.id===speaker)?.name??runCopy[locale].speaker}</strong>:null}
   <p className={block.kind==='SYSTEM'?'game-system':undefined}>{block.text}</p>
  </div>;
 })}
 {paging&&index<lastIndex?<button className="game-read-more" type="button" lang={locale} onClick={event=>{
  focusFinal.current=index+1===lastIndex&&document.activeElement===event.currentTarget;
  setIndex(index+1);
 }}>{readMore[locale]}</button>:null}
 </div>;
}
export function SceneActions({view,locale,locked,selection,submit}:Props){
 const [online,setOnline]=useState(navigator.onLine);
 useEffect(()=>{const update=()=>setOnline(navigator.onLine);window.addEventListener('online',update);window.addEventListener('offline',update);return()=>{window.removeEventListener('online',update);window.removeEventListener('offline',update);};},[]);
 const copy=runCopy[locale], [draft,setDraft]=useState(''),[error,setError]=useState(false);
 const textAllowed=hasTextInput(view);
 const bound=Math.min(view.input?.maxChars??0,1000);
 return <div className="game-actions"><fieldset disabled={locked}><legend>{copy.scene}</legend>
 {view.options.map(o=><div key={o.id}><button type="button" disabled={!o.enabled} aria-describedby={o.hint?`hint-${o.id}`:undefined} onClick={()=>submit({type:'CHOOSE',optionId:o.id})}>{o.label}{selection===o.id?` — ${copy.checking}`:''}</button>{o.hint?<p id={`hint-${o.id}`}>{o.hint}</p>:null}</div>)}
 {textAllowed?<form onSubmit={e=>{e.preventDefault();if(!online||!draft.trim())return;if(Array.from(draft).length>bound){setError(true);return;}const text=draft;setDraft('');setError(false);submit({type:'SUBMIT_TEXT',text});}}>
 <label htmlFor="game-response">{view.input?.prompt??copy.input}</label><textarea disabled={!online} id="game-response" value={draft} onChange={e=>{setDraft(e.target.value);setError(false);}} aria-describedby="game-privacy game-length" onKeyDown={e=>{if(e.nativeEvent.isComposing&&e.key==='Enter')e.stopPropagation();}}/>
 {!online?<p>{copy.offline}</p>:null}<p id="game-privacy">{copy.privacy}</p><p id="game-length" role={error?'alert':undefined}>{error?copy.length:`${Array.from(draft).length} / ${bound}`}</p><button type="submit" disabled={!online||!draft.trim()}>{copy.send}</button></form>:null}
 {view.canAdvance===true?<button type="button" onClick={()=>submit({type:'ADVANCE'})}>{copy.continue}</button>:null}
 </fieldset></div>;
}
export function ScenePanels({view,locale,locked,submit,hidePlaces=false}:Props){
 const c=runCopy[locale],p=view.places;
 return <aside>{view.objectives.length?<details><summary>{c.objectives}</summary>{view.objectives.map(o=><div key={o.id}><strong>{o.label}</strong><p>{'description' in o?o.description:null}</p><p>{o.state}</p></div>)}</details>:null}
 <GameCharacterHistory view={view} locale={locale}/>
 {view.status.length?<details><summary>{c.status}</summary><dl>{view.status.map(s=><div key={s.id}><dt>{s.label}</dt><dd>{s.value}</dd></div>)}</dl></details>:null}
 {!hidePlaces&&p?.places.length?<GamePlaces places={p} locale={locale} locked={locked} submit={submit}/>:null}</aside>;
}
export function SceneResult({view,locale}:{view:GameView;locale:'en'|'pl'}) {
 const result='result' in view?view.result:null,c=runCopy[locale];
 return <section><h4>{result?.headline??(view.endReason==='ABANDONED'?c.abandoned:c.completed)}</h4>{result?<><p>{result.summary}</p><p>{result.label}</p>{result.objectives.map(o=><div key={o.id}><strong>{o.label}</strong><p>{o.description}</p><p>{o.state}</p><p>{o.note}</p></div>)}{result.facts.map(f=><p key={f.id}>{f.label}</p>)}<p>{result.reflection}</p></>:null}</section>;
}
