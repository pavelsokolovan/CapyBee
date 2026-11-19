import {activateGameOwner,ownerAllowed,watchGameOwner} from './gameOwner';
import { decodeOperation, decodeSummary, decodeCapabilities, decodeProfile, decodePack, decodeView, object, integer, text } from './gameDecoders';
import type { GameCommandRequest } from './gameApi';
export type OperationMarker = {owner: string; id: string; path: string; runId?: string; expectedRevision?: number; createdAt: number};
export type GameReply = {status: number; body: unknown; retryAfter?: string | null};
export function retryDelay(value: unknown, header: string | null, attempt=0, now=Date.now()): number {
  const seconds=header == null ? NaN : Number(header);
  const delay=header == null ? NaN : Number.isFinite(seconds) ? seconds*1000 : Date.parse(header)-now;
  return Math.min(60000, Math.max(1000, typeof value === 'number' && Number.isFinite(value) ? value : Number.isFinite(delay) ? delay : 1000*2**Math.min(attempt,6)));
}
/** One foreground transport; components provide existing auth restoration and owner identity. */
export class GameTransport {
  private generation=0;
  private language="en";
  setLanguage(locale:string) {this.language=locale;}
  private restoring?: Promise<void>;
  private active=new Map<string,Promise<GameReply>>();
  private polls=new Set<string>();
  private aborts=new Set<AbortController>();
  private revisions=new Map<string,{revision:number; content:string}>();
  constructor(private owner:()=>string|null, private restore:()=>Promise<void>) {}
  detach() { this.generation++;this.aborts.forEach(a=>a.abort());this.aborts.clear();this.active.clear(); }
  async read(path:string,{restoreSession=true}:{restoreSession?:boolean}={}):Promise<GameReply> {
    return this.request(path,'GET',undefined,restoreSession);
  }
  private async request(path:string,method:string,body?:unknown,restoreSession=true):Promise<GameReply> {
    if(!/^\/api\/game\/[A-Za-z0-9/_-]+$/.test(path))throw new Error('GAME_PATH_INVALID');
    const owner=this.owner(), generation=this.generation;
    if(!owner || !ownerAllowed(owner))throw new Error('GAME_OWNER_CHANGED');
    if(!navigator.onLine)throw new Error('GAME_OFFLINE');
    const controller=new AbortController(); this.aborts.add(controller);
    const unwatch=watchGameOwner(()=>{if(!ownerAllowed(owner))controller.abort();});
    const timer=setTimeout(()=>controller.abort(),25000);
    try {
      let res=await fetch(path,{method,credentials:'include',cache:'no-store',signal:controller.signal,
        headers:{'X-Game-Owner':owner,'Accept-Language':this.language,...(body === undefined ? {} : {'Content-Type':'application/json'})},
        ...(body === undefined ? {} : {body:JSON.stringify(body)})});
      if(res.status === 401 && restoreSession) {
        this.restoring ??= this.restore().finally(()=>{this.restoring=undefined;});
        await this.restoring;
        // A restoration never silently replays a mutation.
        if(this.owner() !== owner || !ownerAllowed(owner))throw new Error('GAME_OWNER_CHANGED');
        if(method !== 'GET')throw new Error('GAME_RECONCILE_REQUIRED');
        res=await fetch(path,{credentials:'include',cache:'no-store',signal:controller.signal,headers:{'X-Game-Owner':owner,'Accept-Language':this.language}});
      }
      if(generation !== this.generation || owner !== this.owner() || !ownerAllowed(owner))throw new Error('GAME_OWNER_CHANGED');
      if(!res.headers.get('content-type')?.includes('application/json'))throw new Error('GAME_CONTRACT_UNSUPPORTED');
      const raw=await res.text();if(raw.length>1048576)throw new Error('GAME_CONTRACT_UNSUPPORTED');
      const value:unknown=JSON.parse(raw);
      if((value as {error?:{code?:string}})?.error?.code==='GAME_OWNER_CHANGED'){activateGameOwner(null);window.dispatchEvent(new Event('game-session-changed'));throw new Error('GAME_OWNER_CHANGED');}
      if(generation !== this.generation || owner !== this.owner() || !ownerAllowed(owner))throw new Error('GAME_OWNER_CHANGED');
      const reply={status:res.status,body:value,retryAfter:res.headers.get('retry-after')};
      if(res.ok && path.endsWith('/capabilities'))return {...reply,body:decodeCapabilities(value)};
      if(res.ok && path.endsWith('/packs')) {
        if(!Array.isArray(value) || value.length>64)throw new Error('GAME_CONTRACT_UNSUPPORTED');
        return {...reply,body:value.map(decodePack)};
      }
      if(res.ok && path.endsWith('/generator-profiles')) {
        if(!Array.isArray(value) || value.length>64)throw new Error('GAME_CONTRACT_UNSUPPORTED');
        return {...reply,body:value.map(decodeProfile)};
      }
      if(res.ok && /\/generator-profiles\//.test(path))return {...reply,body:decodeProfile(value)};
      return {...reply,body:decodeGameResponse(reply)};
    } finally {unwatch();clearTimeout(timer);this.aborts.delete(controller);}
  }
  /** Starts and text never enter IndexedDB; only this body-free marker survives. */
  transient(marker:OperationMarker, method:'POST'|'DELETE', body?:unknown):Promise<GameReply> {
    if(marker.owner !== this.owner() || !ownerAllowed(marker.owner))return Promise.reject(new Error('GAME_OWNER_CHANGED'));
    const key=marker.owner+':'+marker.id;
    const existing=this.active.get(key);if(existing)return existing;
    if(sessionStorage.getItem('game-operation:'+key))return this.read(`${marker.path}/${marker.id}`);
    sessionStorage.setItem('game-operation:'+key,JSON.stringify({owner:marker.owner,id:marker.id,path:marker.path,
      runId:marker.runId,expectedRevision:marker.expectedRevision,createdAt:marker.createdAt}));
    const pending=this.request(marker.path,method,body).finally(()=>this.active.delete(key));
    this.active.set(key,pending);return pending;
  }
  async cancel(marker:OperationMarker):Promise<GameReply> {
    if(marker.owner!==this.owner())throw new Error('GAME_OWNER_CHANGED');
    return this.request(`${marker.path}/${marker.id}`,'DELETE');
  }
  adopt(value:unknown) {
    const run=decodeSummary(value), old=this.revisions.get(run.runId);
    if(old !== undefined && run.revision < old.revision || old !== undefined && run.revision === old.revision && JSON.stringify(run) !== old.content)throw new Error('GAME_STALE_RESPONSE');
    this.revisions.set(run.runId,{revision:run.revision,content:JSON.stringify(run)});return run;
  }
  async poll(path:string, signal:AbortSignal, accept:(reply:GameReply)=>void, keepMissing=false):Promise<void> {
    if(this.polls.has(path))return;
    this.polls.add(path);
    try {
    const generation=this.generation, owner=this.owner();let visibleMs=0,attempt=0;
    while(!signal.aborted && generation===this.generation && owner===this.owner() && visibleMs<300000){
      const started=Date.now();let delay=1000;
      if(document.visibilityState!=='hidden' && navigator.onLine) {
        try {const reply=await this.read(path);if(signal.aborted)return;
          const body=object(reply.body);
          const fatal=body.error != null && ['RUN_UNRECOVERABLE','RUN_QUARANTINED','RUN_ARCHIVED','RUN_TRASHED'].includes(String(object(body.error).code));
          if ((reply.status === 408 || reply.status === 429 || reply.status >= 500) && body.status !== 'FAILED' && !fatal) {
            delay=retryDelay(body.retryAfterMs,reply.retryAfter ?? null,++attempt);
          } else {
          accept(reply);
          if(!(keepMissing&&reply.status===404) && reply.status !== 202 && body.status !== 'PROCESSING' && body.status !== 'PREPARING')return;
          delay=retryDelay(body.retryAfterMs,reply.retryAfter ?? null);attempt=0;
          }
        }catch {delay=retryDelay(undefined,null,++attempt);}
      }
      await new Promise<void>(resolve=>{const timer=setTimeout(done,delay);function done(){clearTimeout(timer);signal.removeEventListener('abort',done);resolve();}signal.addEventListener('abort',done,{once:true});});
      if(document.visibilityState!=='hidden')visibleMs+=Date.now()-started;
    }
    } finally {this.polls.delete(path);}
  }
}
export function decodeGameResponse(reply:GameReply):unknown {
  const value=object(reply.body);
  if(value.error != null) {const error=object(value.error);text(error.code);return {error:{code:error.code}};}
  if(['STARTED','NEEDS_INPUT','FALLBACK_OFFERED','CANCELLED','FAILED'].includes(String(value.status)) && 'retryAfterMs' in value)return decodeOperation(value);
  if(value.outcome != null){
    text(value.commandId);integer(value.revision);integer(value.appliedRevision);
    if(!['APPLIED','REJECTED','REPLAYED'].includes(String(value.outcome)) || typeof value.accepted !== 'boolean')throw new Error('GAME_CONTRACT_UNSUPPORTED');
    return {...value,view:decodeView(value.view,value.viewSchema)};
  }
  if(value.runId != null && value.viewSchema != null)return decodeSummary(value);
  if(reply.status===202){integer(value.retryAfterMs);return value;}
  return value;
}
export function assertDurableCommand(request:GameCommandRequest) {
  integer(request.expectedRevision);text(request.id);
  if(!['CHOOSE','ADVANCE','MOVE','ABANDON'].includes(request.command.type))throw new Error('GAME_TRANSIENT_REQUIRED');
}
