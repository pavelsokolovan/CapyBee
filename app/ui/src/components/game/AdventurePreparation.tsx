import {ownerAllowed,watchGameOwner} from '../../game/gameOwner';
import {useEffect,useLayoutEffect,useRef,useState,useSyncExternalStore} from 'react';
import type {GameRunSummaryDto,SetupInput} from '../../game/gameApi';
import {GameTransport} from '../../offline/syncEngine';
import {AdventureStart} from '../../game/adventureStart';
import {adventureCopy} from '../../game/adventureCopy';
import {compatible,effective,inputsOf,localesOf,titleOf,validation,codePoints,type Adventure} from '../../game/preparation';
import {GameRun} from './GameRun';
import {restoreGameSession} from '../../game/restoreGameSession';

type Props={owner:string;locale:'en'|'pl';onClose:()=>void;onRunReady?:(run:GameRunSummaryDto)=>void;flow?:AdventureStart};
export function AdventurePreparation({owner,locale,onClose,onRunReady,flow:provided}:Props) {
 const [flow]=useState(()=>provided??new AdventureStart(owner,locale,new GameTransport(()=>owner,()=>restoreGameSession(owner)),onRunReady));
 const state=useSyncExternalStore(flow.subscribe,flow.getSnapshot),copy=adventureCopy[locale];
 useEffect(()=>watchGameOwner(()=>{if(!ownerAllowed(owner)){flow.dispose();onClose();}}),[flow,owner,onClose]);
 const [online,setOnline]=useState(navigator.onLine);
 useEffect(()=>{const update=()=>setOnline(navigator.onLine);window.addEventListener('online',update);window.addEventListener('offline',update);return()=>{window.removeEventListener('online',update);window.removeEventListener('offline',update);};},[]);
 const [wish,setWish]=useState(''),[invalid,setInvalid]=useState(false),[composing,setComposing]=useState(false);
 const heading=useRef<HTMLHeadingElement>(null),panel=useRef<HTMLDivElement>(null);
 const closeRef=useRef(onClose);closeRef.current=onClose;
 useEffect(()=>{void flow.load();return()=>flow.dispose();},[flow]);
 useEffect(()=>{void flow.shellLocale(locale);},[flow,locale]);
 useEffect(()=>{setWish('');},[state.clearIdea]);
 useLayoutEffect(()=>{heading.current?.focus();},[state.phase,state.step]);
 useEffect(()=>{
  const previous=document.activeElement as HTMLElement|null;
  const oldOverflow=document.body.style.overflow;document.body.style.overflow='hidden';
  if(!history.state?.adventure)history.pushState({adventure:'panel'},'');
  const back=()=>closeRef.current();
  window.addEventListener('popstate',back);
  return()=>{window.removeEventListener('popstate',back);document.body.style.overflow=oldOverflow;previous?.focus();};
 },[flow]);
 const close=()=>{setWish('');flow.dispose();if(history.state?.adventure)history.go(-1);onClose();};

 const select=(game:Adventure)=>{setWish('');setInvalid(false);flow.select(game);};
 const selected=state.selected;
 const allInputs=selected?inputsOf(selected):[];
 const steps=selected?.kind==='DYNAMIC'?selected.profile.steps:selected?.pack.setup?.steps??[];
 const visibleSteps=steps.filter(step=>step.inputIds.some(id=>allInputs.find(i=>i.id===id)?.kind!=='FREE_TEXT' || selected?.kind==='DYNAMIC'));
 const currentStep=visibleSteps[state.step];
 const currentInputs=currentStep?allInputs.filter(i=>currentStep.inputIds.includes(i.id) && (i.kind!=='FREE_TEXT'||selected?.kind==='DYNAMIC')):[];
 const generated=selected?.kind==='DYNAMIC'||state.generated;
 const problems=selected?validation(selected,state.answers,wish):[];
 const next=()=>{
  setInvalid(true);if(currentInputs.some(i=>problems.includes(i.id))||composing)return;
  setInvalid(false);
  if(state.step+1<visibleSteps.length){flow.step(state.step+1);}else{flow.review();}
 };
 const renderInput=(input:SetupInput)=>{
  const values=effective(input,state.answers),helpId=`adventure-help-${input.id}`;
  return <fieldset key={input.id} className="adventure-field" aria-describedby={helpId}>
   <legend>{input.label}{input.required?' *':''}</legend><p id={helpId}>{input.help}</p>
   {input.kind==='FREE_TEXT'?<>
    <p>{copy.disclosure}</p><p>{input.notice}</p><p>{copy.privacy}</p>
    <textarea aria-label={input.label} value={wish} onChange={e=>setWish(e.target.value)}
      onCompositionStart={()=>setComposing(true)} onCompositionEnd={()=>setComposing(false)} autoComplete="off" spellCheck={false}/>
    <small>{codePoints(wish)} / {input.maxCodePoints}</small>
   </>:<>
    {input.kind==='ENUM_SET'?<p>{copy.limit} {input.maxItems}.</p>:null}
    {input.options.map(option=><label key={option.id} className="adventure-option">
     <input type={input.kind==='ENUM_SET'?'checkbox':'radio'} name={`adventure-${input.id}`} checked={values.includes(option.id)}
      disabled={state.loading || input.kind==='ENUM_SET' && values.length>=input.maxItems && !values.includes(option.id)}
      onChange={()=>{const nextValues=input.kind==='ENUM_SET'?(values.includes(option.id)?values.filter(v=>v!==option.id):[...values,option.id]):[option.id];flow.answer(input.id,nextValues);}}/>
     {option.label}
    </label>)}
    {!input.required?<button type="button" className="secondary-button" onClick={()=>flow.answer(input.id,[])}>{copy.clear}</button>:null}
   </>}
   {invalid&&problems.includes(input.id)?<p role="alert">{input.kind==='FREE_TEXT'?copy.textTooLong:copy.required}</p>:null}
  </fieldset>;
 };
 const activeCurrent=state.caps?.gameEnabled&&state.current&&['ACTIVE','PREPARING'].includes(state.current.status);
 return <div className="adventure-overlay" role="dialog" aria-modal="true" aria-labelledby="adventure-title" ref={panel}
  onKeyDown={event=>{
   if(event.key==='Escape'&&!composing){event.preventDefault();close();}
   if(event.key==='Tab'){
    const nodes=Array.from(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),select,textarea,summary,a[href]')??[]).filter(node=>!node.closest('dialog:not([open])')&&!node.closest('fieldset:disabled'));
    if(!nodes?.length)return;const first=nodes[0],last=nodes[nodes.length-1];
    if(event.shiftKey&&(document.activeElement===first||document.activeElement===heading.current)){event.preventDefault();last.focus();}
    else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
   }
  }}>
  <div className="adventure-panel">
   <header><h2 id="adventure-title" tabIndex={-1} ref={heading}>{copy.title}</h2><button type="button" onClick={close}>{copy.close}</button></header>
   {sessionStorage.getItem('game-reconfirm')?<p>{locale==='pl'?'Sprawdź zapisaną grę. Potwierdź ponownie przed rozpoczęciem.':'Check the saved game. Confirm again before starting.'}</p>:null}
   {state.message?<p role="status">{copy[state.message]}</p>:null}
   {state.loading?<p role="status">{copy.loading}</p>:null}
   {state.message==='signin'?<a href="/oauth2/authorization/google">{copy.signInAction}</a>:null}
   {state.phase==='catalog'||state.phase==='signin'?<>
    {activeCurrent?<section><h3>{'story' in (state.current!.view??{})?(state.current!.view as {story?:{title:string}}).story?.title??copy.saved:copy.saved}</h3>
     <p>{new Intl.DisplayNames([locale],{type:'language'}).of(state.current!.locale)}</p><button className="primary-button" onClick={()=>void flow.resume()}>{copy.continue}</button></section>:null}
    <h3>{copy.newGame}</h3>
    {!state.loading&&!state.games.length&&!state.message?<p>{copy.empty}</p>:null}
    {state.games.map(game=><button type="button" className="play-game-row" key={`${game.kind}:${game.kind==='DYNAMIC'?game.profile.id:game.pack.id}`}
      disabled={state.loading||!compatible(game)||!state.caps?.viewFeatures.includes('canAdvance')} onClick={()=>select(game)}>
      <span><strong>{titleOf(game)||copy.unsupported}</strong><small>{!compatible(game)?copy.unsupported:game.kind==='DYNAMIC'?copy.dynamic:copy.authored}</small></span>
    </button>)}
    <button type="button" className="secondary-button" onClick={()=>void flow.load()} disabled={state.loading}>{copy.retry}</button>
   </>:null}
   {state.phase==='setup'&&selected?<>
    <h3>{titleOf(selected)}</h3><h4>{currentStep?.title??copy.reviewTitle}</h4>
    {currentInputs.map(renderInput)}
    {!allInputs.some(i=>i.id==='game-locale')?<label className="adventure-field">{copy.locale}<select value={state.answers['game-locale']?.[0]} onChange={e=>flow.answer('game-locale',[e.target.value])}>
      {localesOf(selected).map(l=><option value={l} key={l}>{l==='pl'?'Polski':l==='en'?'English':l}</option>)}
    </select></label>:null}
    {selected.kind==='PACK'&&allInputs.some(i=>i.kind==='FREE_TEXT')?<p>{copy.optionalUnavailable}</p>:null}
    {selected.kind==='PACK'&&selected.pack.generated&&state.caps?.packGenerationEnabled?<label className="adventure-option"><input type="checkbox" checked={state.generated} onChange={e=>flow.generated(e.target.checked)}/>{copy.generated}</label>:null}
    {state.generated?<p>{copy.packGenerated}</p>:null}
    <div className="adventure-actions"><button type="button" onClick={()=>{setInvalid(false);flow.back();}}>{copy.back}</button>
    <button type="button" className="primary-button" disabled={state.loading||composing} onClick={next}>{state.step+1<visibleSteps.length?copy.next:copy.review}</button></div>
   </>:null}
   {state.phase==='review'&&selected?<>
    <h3>{copy.reviewTitle}</h3><h4>{titleOf(selected)}</h4>
    <p>{selected.kind==='DYNAMIC'?copy.dynamic:state.generated?copy.packGenerated:copy.authored}</p>
    <p>{copy.locale}: {new Intl.DisplayNames([locale],{type:'language'}).of(state.answers['game-locale']?.[0]??locale)}</p>
    <dl>{allInputs.filter(i=>i.kind!=='FREE_TEXT').map(i=><div key={i.id}><dt>{i.label}</dt><dd>{effective(i,state.answers).map(id=>i.options.find(o=>o.id===id)?.label).join(', ')||copy.notSelected}</dd></div>)}</dl>
    <p>{wish.trim()?copy.ideaAdded:copy.ideaNone}</p>
    {wish?<button type="button" onClick={()=>{setWish('');flow.back();}}>{copy.remove}</button>:null}
    {generated?<p>{copy.disclosure}</p>:null}
    {selected.kind==='DYNAMIC'&&activeCurrent?<><p>{copy.replacementNotice}</p><label className="adventure-option"><input type="checkbox" disabled={state.loading} checked={state.replacement} onChange={e=>flow.replacement(e.target.checked)}/>{copy.replace}</label></>:null}
    {!online?<p>{copy.offline}</p>:null}
    <div className="adventure-actions"><button type="button" onClick={()=>flow.back()} disabled={state.loading}>{copy.edit}</button>
     <button type="button" className="primary-button" disabled={state.loading||!online||problems.length>0||!!(selected.kind==='DYNAMIC'&&activeCurrent&&!state.replacement)}
      onClick={()=>{const idea=wish;setWish('');void flow.confirm(idea);}}>{copy.start}</button></div>
   </>:null}
   {state.phase==='waiting'?<><p role="status">{copy.waiting}</p><p>{copy.waitHint}</p>
    {flow.canCancel()?<><p>{copy.cancelHint}</p><button type="button" onClick={()=>void flow.cancel()}>{copy.cancel}</button></>:null}</>:null}
   {['unknown','failed','needsInput','fallback','cancelled'].includes(state.phase)?<>
    {selected?<button type="button" onClick={()=>{setWish('');flow.step(0);}}>{copy.edit}</button>:null}
    <button type="button" onClick={()=>void flow.check()}>{copy.retry}</button>
    {activeCurrent?<button type="button" onClick={()=>void flow.resume()}>{copy.continue}</button>:null}
    <button type="button" onClick={()=>{setWish('');flow.catalog();void flow.load(false);}}>{copy.newGame}</button>
   </>:null}
   {state.phase==='ready'&&state.ready?<GameRun key={owner+state.ready.runId} run={state.ready} owner={owner} locale={locale} onAnother={()=>{flow.catalog();void flow.load(false);}}/>:null}
  </div>
 </div>;
}
