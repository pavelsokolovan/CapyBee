import {GameTransport, type OperationMarker, type GameReply} from './gameTransport';
import {decodeSummary,object} from './gameDecoders';
import type {GameRunSummaryDto,GameCommand} from './gameApi';
export type RunState={run:GameRunSummaryDto;pending:boolean;message:'saved'|'checking'|'rejected'|'changed'|'unavailable';selection?:string};
/** Owns one run's command identity. No command body or story is persisted here. */
export class RunController {
 private state:RunState; private listeners=new Set<()=>void>(); private epoch=0; private poll?:AbortController;
 private marker?:OperationMarker;
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
 detach(){this.epoch++;this.poll?.abort();this.transport.detach();}
 async submit(command:GameCommand){
  if(this.state.pending||this.state.run.status!=='ACTIVE'||this.state.run.view?.ended)return;
  const run=this.state.run,epoch=this.epoch;
  this.marker={owner:this.owner,id:crypto.randomUUID(),runId:run.runId,expectedRevision:run.revision,path:`/api/game/runs/${run.runId}/commands`,createdAt:Date.now()};
  this.update({pending:true,message:'checking',selection:command.type==='CHOOSE'?command.optionId:command.type});
  try{const reply=await this.transport.transient(this.marker,'POST',{id:this.marker.id,expectedRevision:run.revision,command});
   if(epoch!==this.epoch)return;await this.accept(reply,epoch,true);
  }catch{if(epoch===this.epoch)void this.check();}
 }
 private async accept(reply:GameReply,epoch:number,submission=false){
  if(epoch!==this.epoch)return;
  const body=object(reply.body);
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
  try{const reply=await this.transport.read(`/api/game/runs/${this.state.run.runId}`);if(epoch!==this.epoch)return;
   if(reply.status!==200){this.update({message:'unavailable'});return;}
   const run=decodeSummary(reply.body);if(run.runId!==this.state.run.runId)return;
   if(run.revision<Math.max(this.state.run.revision,minimumRevision)){setTimeout(()=>{if(epoch===this.epoch&&this.state.pending)void this.check();},1000);return;}
   if(this.marker)sessionStorage.removeItem('game-operation:'+this.owner+':'+this.marker.id);
   this.marker=undefined;this.update({run,pending:false,message,selection:undefined});
  }catch{if(epoch===this.epoch)this.update({message:'checking'});}
 }
 async check(){
  if(!this.marker||this.poll)return;const epoch=this.epoch,marker=this.marker;
  const signal=new AbortController();this.poll=signal;
  try{await this.transport.poll(`${marker.path}/${marker.id}`,signal.signal,reply=>{void this.accept(reply,epoch);});}
  finally{if(this.poll===signal)this.poll=undefined;}
 }
}
function objectCode(body:Record<string,unknown>):string {return body.error&&typeof body.error==='object'?String((body.error as Record<string,unknown>).code):'';}
