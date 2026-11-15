import {useEffect,useState} from 'react';
/** A UI clock only; never flushes, grants or persists anything. */
export function useLocalCalendar() {
 const [now,setNow]=useState(()=>new Date());
 useEffect(()=>{
  let timer:ReturnType<typeof setTimeout>;
  const refresh=()=>{
   clearTimeout(timer);const current=new Date();setNow(current);
   const midnight=new Date(current);midnight.setHours(24,0,0,0);
   timer=setTimeout(refresh,Math.min(60000,midnight.getTime()-current.getTime()+1));
  };
  refresh();
  window.addEventListener('focus',refresh);document.addEventListener('visibilitychange',refresh);
  return()=>{clearTimeout(timer);window.removeEventListener('focus',refresh);document.removeEventListener('visibilitychange',refresh);};
 },[]);
 return now;
}
