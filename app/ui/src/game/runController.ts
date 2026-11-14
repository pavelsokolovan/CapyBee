import {enqueueAction,listQueuedActions,removeAction,updateAction} from '../offline/queueStore';
import {onSynced,flushQueue} from '../offline/syncEngine';
import {ownerAllowed,watchGameOwner,notifyGameRevision} from './gameOwner';
import {withGameLock} from './gameLock';
import {GameTransport, type OperationMarker, type GameReply} from './gameTransport';
import {decodeSummary,object} from './gameDecoders';
import type {GameRunSummaryDto,GameCommand} from './gameApi';
export type RunState={run:GameRunSummaryDto;pending:boolean;message:'saved'|'checking'|'rejected'|'changed'|'unavailable'|'waiting'|'offline'|'attention';selection?:string;cancellable?:boolean;retryable?:boolean};
/** Owns one run's command identity. No command body or story is persisted here. */
export class RunController {
 private state:RunState; private listeners=new Set<()=>void>(); private epoch=0; private poll?:AbortController;
 private marker?:OperationMarker;private cleanups:Array<()=>void>=[];private queuedId?:string;
 constructor(run:GameRunSummaryDto,private transport:GameTransport,private owner:string){
  this.state={run,pending:false,message:'saved'};transport.setLanguage(run.locale);
  const prefix='game-operation:'+owner+':';
  for(let i=0;i<sessionStorage.length;i++) {const key=sessionStorage.key(i);if(!key?.startsWith(prefix))continue;
   try{const m=JSON.parse(sessionStorage.getItem(key)! ) as OperationMarker;
    if(m.owner===owner&&m.runId===run.runId&&m.path===`/api/game/runs/${run.runId}/commands`){this.marker=m;this.state={run,pending:true,message:'checking'};break;}}
   catch{/* Ignore unrelated corrupt markers. */}
  }
 }
 getSnapshot=()=>this.state;
 subscribe=(listener:()=>void)=>{this.listeners.add(listener);return()=>{this.listeners.delete(listener);};};
 private update(patch:Partial<RunState>){this.state={...this.state,...patch};this.listeners.forEach(l=>l());}
 async initialize(){
  const epoch=this.epoch;
  this.cleanups.push(watchGameOwner(()=>{if(!ownerAllowed(this.owner)){this.detach();this.update({run:{...this.state.run,view:null},pending:true,message:'unavailable'});}}));
  this.cleanups.push(onSynced(event=>{if(event.action.owner===this.owner&&event.action.runId===this.state.run.runId&&ownerAllowed(this.owner)){
   this.queuedId=undefined;void this.refresh(this.epoch,event.outcome==='stale'?'changed':event.outcome==='terminal'?'unavailable':(event.body as {accepted?:boolean})?.accepted===false?'rejected':'saved',Number((event.body as {revision?:number})?.revision??this.state.run.revision));
  }}));
  const reconcile=()=>{if(!ownerAllowed(this.owner))return;if(this.queuedId)void this.checkQueued();else if(this.marker)void this.check();else if(navigator.onLine)void this.refresh(this.epoch,'saved');else this.update({message:'offline'});};
  const revision=(event:Event)=>{const detail=(event as CustomEvent).detail;if(detail.owner===this.owner&&detail.runId===this.state.run.runId&&detail.revision>this.state.run.revision)reconcile();};
  window.addEventListener('online',reconcile);window.addEventListener('offline',reconcile);window.addEventListener('game-revision',revision);
  const visible=()=>{if(document.visibilityState==='visible')reconcile();};document.addEventListener('visibilitychange',visible);
  this.cleanups.push(()=>{window.removeEventListener('online',reconcile);window.removeEventListener('offline',reconcile);window.removeEventListener('game-revision',revision);document.removeEventListener('visibilitychange',visible);});
  const actions=await listQueuedActions().catch(()=>{this.update({pending:true,message:'attention'});return [];});if(epoch!==this.epoch||!ownerAllowed(this.owner))return;
  const action=actions.find(a=>a.type==='gameCommand'&&a.owner===this.owner&&a.runId===this.state.run.runId);
  if(action){this.queuedId=action.clientId;this.update({pending:true,message:navigator.onLine?'checking':'waiting'});await this.checkQueued();}
  else if(this.marker)void this.check();
 }
 private async checkQueued(){
  const epoch=this.epoch,id=this.queuedId;if(!id)return;
  const action=(await listQueuedActions()).find(a=>a.clientId===id&&a.owner===this.owner);if(epoch!==this.epoch||!ownerAllowed(this.owner)||id!==this.queuedId)return;
  if(!action){this.queuedId=undefined;await this.refresh(epoch,'changed');}
  else {this.update({pending:true,message:action.status==='failed'?'attention':navigator.onLine?'checking':'waiting',cancellable:!action.sent&&!action.poll&&action.status!=='syncing',retryable:action.status==='failed'&&!action.poll});if(action.poll&&navigator.onLine){this.marker={owner:this.owner,id:action.clientId,path:action.path,runId:action.runId,expectedRevision:Number(action.payload?.expectedRevision),createdAt:action.createdAt};void this.pollCommand();}}
 }
 async retryUnsent(){
  if(!this.queuedId)return;
  await withGameLock(this.owner,this.state.run.runId,async()=>{
   const action=(await listQueuedActions()).find(a=>a.clientId===this.queuedId&&a.owner===this.owner);
   if(action&&!action.poll&&action.status==='failed'&&ownerAllowed(this.owner)){action.attempts=0;action.status='pending';action.notBefore=0;await updateAction(action);}
  });
  await this.check();
 }
 async removeUnsent(){
  if(!this.queuedId)return;await withGameLock(this.owner,this.state.run.runId,async()=>{
   const action=(await listQueuedActions()).find(a=>a.clientId===this.queuedId&&a.owner===this.owner);
   if(action&&!action.sent&&!action.poll&&action.status!=='syncing'){await removeAction(action.clientId);this.queuedId=undefined;this.update({pending:false,message:'saved',cancellable:false});}
  });
 }
 detach(){this.cleanups.splice(0).forEach(clean=>clean());this.epoch++;this.poll?.abort();this.transport.detach();}
 async submit(command:GameCommand){
  if(this.state.pending||this.state.run.status!=='ACTIVE'||this.state.run.view?.ended)return;
  if(!ownerAllowed(this.owner))return;
  if(command.type==='SUBMIT_TEXT'&&!navigator.onLine){this.update({message:'offline'});return;}
  const run=this.state.run,epoch=this.epoch;
  if(command.type!=='SUBMIT_TEXT'){
   const id=crypto.randomUUID();this.update({pending:true,message:navigator.onLine?'checking':'waiting',selection:command.type==='CHOOSE'?command.optionId:command.type});
   try{await enqueueAction({clientId:id,owner:this.owner,runId:run.runId,type:'gameCommand',path:`/api/game/runs/${run.runId}/commands`,method:'POST',payload:{expectedRevision:run.revision,command},createdAt:Date.now()});
    if(epoch!==this.epoch||!ownerAllowed(this.owner))return;this.queuedId=id;await flushQueue();await this.checkQueued();
   }catch{if(epoch===this.epoch&&ownerAllowed(this.owner)){this.update({message:'attention'});const existing=(await listQueuedActions().catch(()=>[])).find(a=>a.owner===this.owner&&a.runId===run.runId&&a.type==='gameCommand');if(existing){this.queuedId=existing.clientId;await this.checkQueued();}else await this.refresh(epoch,'changed');}}
   return;
  }
  this.marker={owner:this.owner,id:crypto.randomUUID(),runId:run.runId,expectedRevision:run.revision,path:`/api/game/runs/${run.runId}/commands`,createdAt:Date.now()};
  this.update({pending:true,message:'checking',selection:command.type});
  try{const reply=await withGameLock(this.owner,run.runId,async()=>{
    if(!ownerAllowed(this.owner))throw new Error('GAME_OWNER_CHANGED');
    if((await listQueuedActions()).some(a=>a.owner===this.owner&&a.runId===run.runId))throw new Error('GAME_COMMAND_PENDING');
    return this.transport.transient(this.marker!,'POST',{id:this.marker!.id,expectedRevision:run.revision,command});});
   if(!reply){this.update({message:'attention'});return;}
   if(epoch!==this.epoch||!ownerAllowed(this.owner))return;await this.accept(reply,epoch,true);
  }catch{if(epoch===this.epoch)void this.check();}
 }
 private async accept(reply:GameReply,epoch:number,submission=false){
  if(epoch!==this.epoch||!ownerAllowed(this.owner))return;
  const body=object(reply.body);
  if(reply.status===404&&this.queuedId){this.update({message:'checking'});return;}
  if(['RUN_UNRECOVERABLE','RUN_QUARANTINED','RUN_ARCHIVED','RUN_TRASHED'].includes(objectCode(body))){this.update({message:'unavailable'});return;}
  if(reply.status===202||reply.status===429||reply.status>=500||objectCode(body)==='RUN_BUSY'){void this.check();return;}
  if(body.commandId===this.marker?.id&&['APPLIED','REPLAYED','REJECTED'].includes(String(body.outcome))){
   await this.refresh(epoch,body.outcome==='REJECTED'?'rejected':'saved',Math.max(Number(body.appliedRevision??0),Number(body.revision??0)));return;
  }
  if(['REVISION_CONFLICT','RUN_NOT_ACTIVE'].includes(objectCode(body))) {await this.refresh(epoch,'changed');return;}
  // A definite rejection has no accepted effects; still reconcile the saved view.
  if(reply.status>=400&&reply.status<500){if(submission)await this.refresh(epoch,'unavailable');else this.update({message:'unavailable'});return;}
  this.update({message:'checking'});
 }
 private async refresh(epoch:number,message:RunState['message'],minimumRevision=this.state.run.revision){
  try{const reply=await this.transport.read(`/api/game/runs/${this.state.run.runId}`);if(epoch!==this.epoch||!ownerAllowed(this.owner))return;
   if(reply.status!==200){this.update({message:'unavailable'});return;}
   const run=decodeSummary(reply.body);if(run.runId!==this.state.run.runId)return;
   if(run.revision<Math.max(this.state.run.revision,minimumRevision)){setTimeout(()=>{if(epoch===this.epoch&&this.state.pending)void this.check();},1000);return;}
   if(this.queuedId){const id=this.queuedId;const removed=await withGameLock(this.owner,run.runId,async()=>{if(!ownerAllowed(this.owner))return false;await removeAction(id);return true;});if(!removed){setTimeout(()=>{if(epoch===this.epoch)void this.checkQueued();},1000);return;}this.queuedId=undefined;}
   if(epoch!==this.epoch||!ownerAllowed(this.owner))return;
   if(this.marker)sessionStorage.removeItem('game-operation:'+this.owner+':'+this.marker.id);
   const changed=run.revision>this.state.run.revision;this.marker=undefined;this.update({run,pending:false,message,selection:undefined,cancellable:false,retryable:false});if(changed)notifyGameRevision(this.owner,run.runId,run.revision);
  }catch{if(epoch===this.epoch)this.update({message:'checking'});}
 }
 async check(){
  if(!this.queuedId&&!this.marker&&this.state.pending){const existing=(await listQueuedActions().catch(()=>[])).find(a=>a.owner===this.owner&&a.runId===this.state.run.runId&&a.type==='gameCommand');if(existing)this.queuedId=existing.clientId;else{await this.refresh(this.epoch,'changed');return;}}
  if(this.queuedId){await flushQueue();await this.checkQueued();return;}
  await this.pollCommand();
 }
 private async pollCommand(){
  if(!this.marker||this.poll)return;const epoch=this.epoch,marker=this.marker;
  const signal=new AbortController();this.poll=signal;
  try{await this.transport.poll(`${marker.path}/${marker.id}`,signal.signal,reply=>{void this.accept(reply,epoch);},!!this.queuedId);}
  finally{if(this.poll===signal)this.poll=undefined;}
 }
}
function objectCode(body:Record<string,unknown>):string {return body.error&&typeof body.error==='object'?String((body.error as Record<string,unknown>).code):'';}
