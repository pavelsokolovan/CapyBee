import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {expect,it,vi} from 'vitest';
const state=vi.hoisted(()=>({actions:[] as unknown[],fail:false,notify:()=>{}}));
vi.mock('../offline/queueStore',()=>({listQueuedActions:async()=>{if(state.fail)throw new Error('storage unavailable');return state.actions;}}));
vi.mock('../offline/syncEngine',()=>({onSyncStatusChange:(notify:()=>void)=>{state.notify=notify;return()=>{};}}));
import {usePendingGameCell} from './usePendingGameCell';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
it('reserves all game offers for a pending owned cell, refreshes after acknowledgement and ignores other owners/Missions',async()=>{
 const host=document.createElement('div'),root=createRoot(host),refresh=vi.fn(async()=>{});
 function Status({owner}:{owner:string}){const pending=usePendingGameCell(owner,refresh);return <p>{String(pending)}</p>;}
 try{
  state.actions=[{type:'gameCompletion',owner:'owner',createdAt:Date.now(),payload:{kind:'CELL'}}];
  await act(async()=>root.render(<Status owner="owner"/>));expect(host.textContent).toBe('true');
  state.actions=[{type:'gameCompletion',owner:'other',createdAt:Date.now(),payload:{kind:'CELL'}},{type:'gameCompletion',owner:'owner',payload:{kind:'MISSION'}}];
  await act(async()=>state.notify());expect(host.textContent).toBe('false');expect(refresh).toHaveBeenCalledTimes(1);
  await act(async()=>root.render(<Status owner="other"/>));expect(host.textContent).toBe('true');
  state.actions=[{clientId:'old',type:'gameCompletion',owner:'other',createdAt:Date.now(),completedAt:new Date(Date.now()-2*86400000).toISOString(),payload:{kind:'CELL'}}];
  await act(async()=>state.notify());expect(host.textContent).toBe('false');
 }finally{await act(async()=>root.unmount());}
});

it('pauses the shared offer if pending storage cannot be read, without an unhandled error',async()=>{
 const host=document.createElement('div'),root=createRoot(host);state.fail=true;
 function Status(){return <p>{String(usePendingGameCell('owner',async()=>{}))}</p>;}
 try{await act(async()=>root.render(<Status/>));expect(host.textContent).toBe('true');}
 finally{await act(async()=>root.unmount());state.fail=false;}
});
