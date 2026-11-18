import {useEffect,useState} from 'react';
import './appUpdateNotice.css';
const copy={
 en:{message:'An app update is ready. Reload to use the latest version. Unsent text will be cleared.',reload:'Reload app',later:'Later'},
 pl:{message:'Aktualizacja jest gotowa. Odśwież aplikację, aby używać najnowszej wersji. Niewysłany tekst zostanie usunięty.',reload:'Odśwież aplikację',later:'Później'},
} as const;
/** A new worker does not replace JavaScript already running in an installed PWA. */
export function AppUpdateNotice({reload=()=>window.location.reload()}:{reload?:()=>void}){
 const [available,setAvailable]=useState(false);
 const locale=navigator.language.toLowerCase().startsWith('pl')?'pl':'en';
 useEffect(()=>{
  if(!('serviceWorker' in navigator))return;
  const workers=navigator.serviceWorker;let controlled=!!workers.controller;
  const changed=()=>{if(controlled)setAvailable(true);controlled=!!workers.controller;};
  const check=()=>{if(navigator.onLine&&document.visibilityState==='visible')void workers.getRegistration().then(r=>r?.update()).catch(()=>{});};
  workers.addEventListener('controllerchange',changed);document.addEventListener('visibilitychange',check);window.addEventListener('online',check);
  check();
  return()=>{workers.removeEventListener('controllerchange',changed);document.removeEventListener('visibilitychange',check);window.removeEventListener('online',check);};
 },[]);
 return available?<aside className="app-update-notice" lang={locale} aria-label={copy[locale].reload}>
  <p role="status">{copy[locale].message}</p><button type="button" onClick={reload}>{copy[locale].reload}</button> <button type="button" onClick={()=>setAvailable(false)}>{copy[locale].later}</button>
 </aside>:null;
}
