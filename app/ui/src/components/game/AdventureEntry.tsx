import {useEffect,useRef,useState} from 'react';
import {GameTransport} from '../../offline/syncEngine';
import {decodeCapabilities} from '../../game/gameDecoders';
import {restoreGameSession} from '../../game/restoreGameSession';
import {adventureCopy} from '../../game/adventureCopy';
import {AdventurePreparation} from './AdventurePreparation';
export function AdventureEntry({owner,locale}:{owner:string;locale:'en'|'pl'}) {
 const [enabled,setEnabled]=useState(false),[open,setOpen]=useState(false);const button=useRef<HTMLButtonElement>(null);
 useEffect(()=>{
  const transport=new GameTransport(()=>owner,()=>restoreGameSession(owner));let alive=true;
  const check=()=>{void transport.read('/api/game/capabilities').then(reply=>{if(alive&&reply.status===200)setEnabled(decodeCapabilities(reply.body).clientEnabled);}).catch(()=>{});};
  check();window.addEventListener('online',check);
  return()=>{alive=false;transport.detach();window.removeEventListener('online',check);};
 },[owner]);
 useEffect(()=>{setOpen(false);},[owner]);
 if(!enabled)return null;
 const copy=adventureCopy[locale];
 return <><button type="button" className="play-game-row" ref={button} onClick={()=>setOpen(true)}>
  <span className="play-game-icon" aria-hidden="true">📖</span><span className="play-game-copy"><strong>{copy.title}</strong><small>{copy.blurb}</small></span><span aria-hidden="true">→</span>
 </button>{open?<AdventurePreparation key={owner} owner={owner} locale={locale} onClose={()=>{setOpen(false);requestAnimationFrame(()=>button.current?.focus());}}/>:null}</>;
}
