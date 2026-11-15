import {useEffect,useRef,useState} from 'react';
import {listQueuedActions} from '../offline/queueStore';
import {hasGameCellOnDay} from '../game/dailyGameCells';
import {onSyncStatusChange} from '../offline/syncEngine';
/** Reserve the shared UI offer while an owner-scoped RPG cell choice is unsent.
 * No optimistic RPG result is created and no extra dispatcher is started. */
export function usePendingGameCell(owner:string|null,refresh:()=>Promise<void>,now=new Date()) {
 const day=useRef(now);day.current=now;
 const dayKey=`${now.getFullYear()}-${now.getMonth()}-${now.getDate()}-${now.getTimezoneOffset()}-${Intl.DateTimeFormat().resolvedOptions().timeZone}`;
 const [pending,setPending]=useState(false),refreshRef=useRef(refresh);refreshRef.current=refresh;
 useEffect(()=>{
  let alive=true,wasPending=false,refreshing:Promise<void>|null=null;
  setPending(false);
  const check=async()=>{
   const actions=await listQueuedActions();if(!alive)return;
   const has=!!owner&&actions.some(a=>a.type==='gameCompletion'&&a.owner===owner&&a.payload?.kind==='CELL'&&hasGameCellOnDay([{id:a.clientId,gameKey:'capybee_rpg',completedAt:a.completedAt??new Date(a.createdAt).toString()}],day.current));
   if(!has&&wasPending){refreshing??=refreshRef.current();await refreshing.catch(()=>{});refreshing=null;}
   if(alive){wasPending=has;setPending(has);}
  };
  const refreshPending=()=>void check().catch(()=>{if(alive)setPending(!!owner);});
  refreshPending();const unsubscribe=onSyncStatusChange(refreshPending);
  return()=>{alive=false;unsubscribe();};
 },[owner,dayKey]);
 return pending;
}
