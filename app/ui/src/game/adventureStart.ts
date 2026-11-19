import {anonymousNotePackId} from './anonymousNote';
import type { GameCapabilities, GamePackInfoDto, GameRunSummaryDto, GeneratorProfile } from './gameApi';
import { decodeCapabilities, decodePack, decodeProfile, decodeSummary, object } from './gameDecoders';
import { type GameReply, type OperationMarker } from '../offline/syncEngine';
import { compatible, compatibleAnswers, transientPack, initialAnswers, startBody, type Adventure, type Answers } from './preparation';
import type { AdventureMessage } from './adventureCopy';
export type StartPhase='catalog'|'setup'|'review'|'waiting'|'ready'|'unknown'|'needsInput'|'fallback'|'cancelled'|'failed'|'signin';
export interface StartState {
 phase:StartPhase; loading:boolean; message?:AdventureMessage; caps?:GameCapabilities; games:Adventure[];
 current:GameRunSummaryDto|null; ready:GameRunSummaryDto|null; selected:Adventure|null;
 answers:Answers; step:number; generated:boolean; replacement:boolean; clearIdea:number;
}
export interface StartTransport {
 read(path:string):Promise<GameReply>;
 transient(marker:OperationMarker,method:'POST'|'DELETE',body?:unknown):Promise<GameReply>;
 cancel(marker:OperationMarker):Promise<GameReply>;
 poll(path:string,signal:AbortSignal,accept:(reply:GameReply)=>void):Promise<void>;
 detach():void; setLanguage(locale:string):void;
}
function message(error:unknown):AdventureMessage {
 const code=error instanceof Error ? error.message : String(error);
 if(/OWNER|401|UNAUTHENTICATED|RECONCILE/.test(code))return 'signin';
 if(/OFFLINE/.test(code))return 'offline';
 if(/INPUT|PREPARATION/.test(code))return 'needsInput';
 if(/429|RATE_LIMIT/.test(code))return 'rate';
 if(/CONTRACT|SCHEMA/.test(code))return 'unsupported';
 if(/FINGERPRINT|REQUEST_INVALID/.test(code))return 'stale';
 return 'unavailable';
}
function successful(reply:GameReply):unknown {
 if(reply.status>=400){const value=object(reply.body);throw new Error(String((value.error && object(value.error).code) ?? value.code ?? reply.status));}
 return reply.body;
}
export function storedStart(owner:string):OperationMarker|undefined {
 const found:OperationMarker[]=[];
 for(let i=0;i<sessionStorage.length;i++) {
  const key=sessionStorage.key(i);if(!key?.startsWith(`game-operation:${owner}:`))continue;
  try {const v=JSON.parse(sessionStorage.getItem(key)!);
   if(v.owner===owner && typeof v.id==='string' && /^[a-zA-Z0-9_-]+$/.test(v.id)
      && ['/api/game/runs','/api/game/dynamic-starts','/api/game/pack-starts'].includes(v.path) && typeof v.createdAt==='number')found.push(v);
  } catch { /* An invalid marker cannot authorize a write. */ }
 }
 return found.sort((a,b)=>b.createdAt-a.createdAt)[0];
}
/** No effect or recovery path can submit a start. Only confirm() writes. */
export class AdventureStart {
 private value:StartState={phase:'catalog',loading:false,games:[],current:null,ready:null,selected:null,answers:{},step:0,generated:false,replacement:false,clearIdea:0};
 private listeners=new Set<()=>void>(); private generation=0; private sending=false;
 private pollAbort?:AbortController; private marker?:OperationMarker;
 constructor(private owner:string,private locale:string,private transport:StartTransport,
   private ready:(run:GameRunSummaryDto)=>void=()=>{}) {transport.setLanguage(locale);}
 getSnapshot=()=>this.value;
 subscribe=(listener:()=>void)=>{this.listeners.add(listener);return ()=>{this.listeners.delete(listener);};};
 private update(patch:Partial<StartState>) {this.value={...this.value,...patch};this.listeners.forEach(l=>l());}
 private signIn() {
  this.generation++;this.pollAbort?.abort();this.forget();this.transport.detach();this.sending=false;
  this.update({phase:'signin',message:'signin',loading:false,current:null,ready:null,games:[],selected:null,answers:{},clearIdea:this.value.clearIdea+1});
 }
 private remember(marker:OperationMarker) {this.marker=marker;}
 private forget() {if(this.marker)sessionStorage.removeItem(`game-operation:${this.owner}:${this.marker.id}`);this.marker=undefined;}
 async load(recover=true) {
  const epoch=++this.generation;this.update({loading:true,message:undefined});
  try {
   const caps=decodeCapabilities(successful(await this.transport.read('/api/game/capabilities')));
   if(epoch!==this.generation)return;
   this.update({caps});
   if(!caps.gameEnabled){this.update({loading:false,message:'disabled',games:[]});return;}
   const calls=await Promise.allSettled([
    this.transport.read('/api/game/runs/current'),this.transport.read('/api/game/packs'),this.transport.read('/api/game/generator-profiles')]);
   if(epoch!==this.generation)return;
   const patch:Partial<StartState>={loading:false}; const games:Adventure[]=[];
   const [current,packs,profiles]=calls;
   if(current.status==='fulfilled'){
    if(current.value.status===200)patch.current=decodeSummary(current.value.body);
    else if(current.value.status===404)patch.current=null;
    else patch.message=current.value.status===401?'signin':'unavailable';
   }else patch.message=message(current.reason);
   if(packs.status==='fulfilled' && packs.value.status===200 && caps.packStartEnabled && Array.isArray(packs.value.body))
    for(const pack of packs.value.body.slice(0,64))games.push({kind:'PACK',pack:pack as GamePackInfoDto});
   else if(packs.status==='rejected' || packs.status==='fulfilled' && packs.value.status>=500)patch.message='unavailable';
   if(profiles.status==='fulfilled' && profiles.value.status===200 && caps.dynamicStartEnabled && Array.isArray(profiles.value.body))
    for(const profile of profiles.value.body)games.push({kind:'DYNAMIC',profile:decodeProfile(profile)});
   else if(profiles.status==='rejected' || profiles.status==='fulfilled' && profiles.value.status>=500)patch.message='unavailable';
   patch.games=games;this.update(patch);
   const marker=recover ? storedStart(this.owner) : undefined;
   if(marker){this.remember(marker);await this.check();}
  } catch(error){if(epoch===this.generation){if(message(error)==='signin')this.signIn();else this.update({loading:false,message:message(error)});}}
 }
 select(game:Adventure) {
  if(this.sending || this.value.phase==='waiting' || !compatible(game))return;
  if(this.value.current?.status==='PREPARING'){this.update({message:'waitHint'});return;}
  if(game.kind==='PACK' && !transientPack(game) && this.value.current && ['ACTIVE','PREPARING'].includes(this.value.current.status)){this.update({message:'packConflict'});return;}
  const direct = game.kind==='PACK' && game.pack.id===anonymousNotePackId;
  this.generation++;this.pollAbort?.abort();this.forget();this.update({selected:game,answers:initialAnswers(game,this.locale),generated:false,replacement:false,step:0,phase:direct?'review':'setup',message:undefined,clearIdea:this.value.clearIdea+1});
  if(direct)void this.confirm('');
 }
 answer(id:string,values:string[]) {if(this.sending)return;this.update({answers:{...this.value.answers,[id]:values},phase:'setup'});}
 generated(value:boolean) {if(!this.sending)this.update({generated:value,phase:'setup'});}
 replacement(value:boolean){if(!this.sending)this.update({replacement:value});}
 step(index:number){if(!['setup','review'].includes(this.value.phase)){this.generation++;this.pollAbort?.abort();this.sending=false;}this.update({step:Math.max(0,index),phase:'setup',message:undefined});}
 review(){if(this.value.selected)this.update({phase:'review',message:undefined});}
 back(){if(this.value.phase==='review')this.update({phase:'setup'});else if(this.value.phase==='setup' && this.value.step>0)this.step(this.value.step-1);else this.catalog();}
 catalog(){this.generation++;this.pollAbort?.abort();this.sending=false;this.update({phase:'catalog',selected:null,message:undefined,clearIdea:this.value.clearIdea+1});}
 async shellLocale(locale:string) {
  this.locale=locale;this.transport.setLanguage(locale);
  const selected=this.value.selected;if(!selected)return;
  try {
   const epoch=this.generation;
   if(selected.kind==='DYNAMIC'){
    const profile=decodeProfile(successful(await this.transport.read(`/api/game/generator-profiles/${selected.profile.id}`)));
    if(epoch!==this.generation || locale!==this.locale || this.value.selected?.kind!=='DYNAMIC' || this.value.selected.profile.id!==selected.profile.id)return;
    const game:Adventure={kind:'DYNAMIC',profile}; const changed=profile.fingerprint!==selected.profile.fingerprint;
    this.update({selected:game,answers:compatibleAnswers(game,this.value.answers),...(changed?{phase:'setup' as const,message:'stale' as const,clearIdea:this.value.clearIdea+1}:{})});
   }
  }catch(error){this.update({message:message(error)});}
 }
 async confirm(wish:string) {
  if(this.sending || this.value.phase!=='review' || !navigator.onLine)return;
  const selected=this.value.selected,caps=this.value.caps;if(!selected||!caps)return;
  if(!caps.viewFeatures.includes('canAdvance') || !caps.supportedViewSchemas.includes(2)){this.update({message:'unsupported'});return;}
  if((selected.kind==='DYNAMIC'||transientPack(selected)&&this.value.generated) && this.value.current?.status==='ACTIVE' && !this.value.replacement){this.update({message:'confirmReplace'});return;}
  const epoch=this.generation;this.sending=true;this.update({loading:true});
  try {
   // Refresh public identity/disclosure before allocating a paid-attempt ID.
   const fresh=decodeCapabilities(successful(await this.transport.read('/api/game/capabilities')));
   if(epoch!==this.generation)return;
   if(!fresh.clientEnabled || !fresh.gameEnabled || (selected.kind==='DYNAMIC' ? !fresh.dynamicStartEnabled : !fresh.packStartEnabled || this.value.generated && !fresh.packGenerationEnabled))throw new Error('GENERATION_UNAVAILABLE');
   if(selected.kind==='DYNAMIC') {
    const profile=decodeProfile(successful(await this.transport.read(`/api/game/generator-profiles/${selected.profile.id}`)));
    if(epoch!==this.generation)return;
    if(profile.fingerprint!==selected.profile.fingerprint || fresh.disclosureRevision!==caps.disclosureRevision) {
     const game:Adventure={kind:'DYNAMIC',profile};this.update({caps:fresh,selected:game,answers:compatibleAnswers(game,this.value.answers),phase:'setup',message:'stale',clearIdea:this.value.clearIdea+1});return;
    }
   } else {
    const list=successful(await this.transport.read('/api/game/packs'));
    if(epoch!==this.generation)return;
    const pack=Array.isArray(list)?list.map(decodePack).find(p=>p.id===selected.pack.id):undefined;
    if(!pack)throw new Error('REQUEST_INVALID');
    if(pack.version!==selected.pack.version || pack.setup?.preparationFingerprint!==selected.pack.setup?.preparationFingerprint || pack.setup?.transientTextSupported!==selected.pack.setup?.transientTextSupported || fresh.disclosureRevision!==caps.disclosureRevision){
     const game:Adventure={kind:'PACK',pack};this.update({caps:fresh,selected:game,answers:compatibleAnswers(game,this.value.answers),generated:pack.generated&&pack.setup?.transientTextSupported?this.value.generated:false,phase:'setup',message:'stale',clearIdea:this.value.clearIdea+1});return;
    }
   }
   this.forget();
   const id=crypto.randomUUID();
   const body=startBody(selected,this.value.answers,wish,fresh,id,this.value.generated,
     this.value.replacement ? this.value.current?.runId : undefined);
   const marker={owner:this.owner,id,path:selected.kind==='DYNAMIC'?'/api/game/dynamic-starts':transientPack(selected)&&this.value.generated?'/api/game/pack-starts':'/api/game/runs',createdAt:Date.now()};
   this.remember(marker);this.update({phase:'waiting',message:undefined,clearIdea:this.value.clearIdea+1});
   try {const reply=await this.transport.transient(marker,'POST',body);if(epoch===this.generation){await this.accept(reply);if(this.getSnapshot().phase==='waiting')this.watch(marker);}}
   catch {if(epoch===this.generation)await this.check();}
  }catch(error){if(epoch===this.generation){if(message(error)==='signin')this.signIn();else this.update({message:message(error),phase:'setup',clearIdea:this.value.clearIdea+1});}}
  finally {if(epoch===this.generation){this.sending=false;this.update({loading:false});}}
 }
 private handoff(value:unknown) {
  const run=decodeSummary(value);this.forget();this.pollAbort?.abort();
  this.update({phase:'ready',ready:run,current:run,clearIdea:this.value.clearIdea+1});this.ready(run);
 }
 private async accept(reply:GameReply) {
  const epoch=this.generation;
  const value=object(reply.body);
  if(value.status==='STARTED'){this.handoff(value.run);return;}
  if(value.runId && value.viewSchema){const run=decodeSummary(value);
   if(run.status==='ACTIVE' && run.view){this.handoff(run);return;}
   if(run.status==='PREPARING'){this.update({phase:'waiting'});return;}
   this.forget();this.update({phase:'failed',current:run,message:'failed'});return;
  }
  if(value.runId && this.marker?.path==='/api/game/runs'){
   this.marker={...this.marker,runId:String(value.runId)};await this.check();return;
  }
  if(value.status==='PROCESSING' || reply.status===202){this.update({phase:'waiting'});return;}
  if(value.status==='CANCELLED') {
   const marker=this.marker; await this.refreshCurrent();if(epoch!==this.generation)return;
   // Never adopt an unrelated current run as this attempt's successful result.
   if(marker?.runId && this.value.current?.runId===marker.runId){this.handoff(this.value.current);return;}
   this.forget();this.update({phase:'cancelled',message:'cancelled'});return;
  }
  if(['NEEDS_INPUT','FALLBACK_OFFERED','FAILED'].includes(String(value.status))){
   const phase=value.status==='NEEDS_INPUT'?'needsInput':value.status==='FALLBACK_OFFERED'?'fallback':'failed';
   this.forget();await this.refreshCurrent();if(epoch!==this.generation)return;this.update({phase,message:value.code==='INPUT_SUPPORT'?'support':value.code==='INPUT_EMERGENCY'?'emergency':phase,clearIdea:this.value.clearIdea+1});return;
  }
  if(reply.status===404){await this.refreshCurrent();if(epoch!==this.generation)return;this.update({phase:'unknown',message:'unknown',clearIdea:this.value.clearIdea+1});return;}
  if(reply.status>=400){const code=String(value.error && object(value.error).code || value.code || reply.status);
   this.forget();await this.refreshCurrent();if(epoch!==this.generation)return;this.update({phase:'failed',message:message(new Error(code)),clearIdea:this.value.clearIdea+1});return;}
  this.update({phase:'unknown',message:'unknown'});
 }
 private async refreshCurrent() {const epoch=this.generation;try{const reply=await this.transport.read('/api/game/runs/current');if(epoch===this.generation&&reply.status===200)this.update({current:decodeSummary(reply.body)});}catch{/* Preserve the last accepted run. */}}
 async check() {
  const marker=this.marker;if(!marker)return;
  const epoch=this.generation;
  try {const reply=await this.transport.read(marker.path==='/api/game/runs'?`/api/game/runs/${marker.runId??marker.id}`:`${marker.path}/${marker.id}`);
   if(epoch!==this.generation)return;await this.accept(reply);
   if(this.getSnapshot().phase==='waiting')this.watch(marker);
  }catch(error){if(epoch===this.generation){if(message(error)==='signin')this.signIn();else this.update({phase:'unknown',message:'unknown'});}}
 }
 private watch(marker:OperationMarker) {
  if(this.pollAbort && !this.pollAbort.signal.aborted)return;
  const abort=new AbortController();this.pollAbort=abort;const epoch=this.generation;
  const path=marker.path==='/api/game/runs'?`/api/game/runs/${marker.runId??marker.id}`:`${marker.path}/${marker.id}`;
  void this.transport.poll(path,abort.signal,reply=>{if(epoch===this.generation)void this.accept(reply).catch(()=>this.update({phase:'unknown',message:'unknown'}));}).finally(()=>{
   if(!abort.signal.aborted && epoch===this.generation && this.value.phase==='waiting')this.update({phase:'unknown',message:'unknown'});
   if(this.pollAbort===abort)this.pollAbort=undefined;
  });
 }
 async cancel() {const marker=this.marker;if(!marker||!['/api/game/dynamic-starts','/api/game/pack-starts'].includes(marker.path))return;
  this.pollAbort?.abort();const epoch=this.generation;
  try{const reply=await this.transport.cancel(marker);if(epoch===this.generation)await this.accept(reply);}catch{await this.check();}}
 canCancel(){return !!this.marker&&['/api/game/dynamic-starts','/api/game/pack-starts'].includes(this.marker.path);}
 async resume() {const epoch=this.generation,current=this.value.current;if(!current)return;
  try{const reply=await this.transport.read(`/api/game/runs/${current.runId}`);if(epoch!==this.generation)return;if(reply.status===200){const run=decodeSummary(reply.body);if(run.status==='PREPARING'){
    this.remember({owner:this.owner,id:run.runId,runId:run.runId,path:'/api/game/runs',createdAt:Date.now()});await this.check();
   }else this.handoff(run);}else if(reply.status===401)this.signIn();else this.update({message:'unknown'});}catch(error){if(epoch===this.generation){if(message(error)==='signin')this.signIn();else this.update({message:message(error)});}}}
 dispose(){this.generation++;this.pollAbort?.abort();this.transport.detach();this.sending=false;}
}
