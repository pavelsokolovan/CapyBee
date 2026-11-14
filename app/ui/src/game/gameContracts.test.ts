import contracts from './fixtures/contracts.json';
import golden from '../../../engine/session/src/test/resources/wire/run-summary.json';
import {describe,it,expect,vi,afterEach} from 'vitest';
import {decodeSummary,decodeOperation,decodeView,decodeProfile} from './gameDecoders';
import {GameTransport,retryDelay,assertDurableCommand} from './gameTransport';
const view={blocks:[],options:[],status:[],objectives:[],characters:[],ended:false,canAdvance:false};
const run={runId:'run',status:'ACTIVE',revision:0,locale:'en',updatedAt:'2026-11-14',viewSchema:2,view,
  source:{kind:'DYNAMIC',profileId:'generic-adventure',profileVersion:'1.0.0',gameFingerprint:'fingerprint'},packId:null,packVersion:null};
afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();sessionStorage.clear();});
describe('public game boundary',()=>{
 it('validates shared production DTO fixtures',()=>{decodeSummary(contracts.pack);decodeSummary(contracts.dynamic);decodeSummary(contracts.packV1);decodeOperation(contracts.started);decodeOperation(contracts.failed);decodeProfile(contracts.profile);});
 it('consumes the production Java session golden fixture',()=>{expect(decodeSummary(golden).source.kind).toBe('PACK');});
 it('accepts source-neutral identity and rejects mixed or unsupported data',()=>{
  expect(decodeSummary(run).source.kind).toBe('DYNAMIC');
  expect(()=>decodeSummary({...run,packId:'fake'})).toThrow();
  expect(()=>decodeSummary({...run,revision:-1})).toThrow();
  expect(()=>decodeView(view,3)).toThrow();
  expect(()=>decodeSummary({...run,view:{...view,options:'bad'}})).toThrow();
  expect(()=>decodeProfile({setupSchemaVersion:9})).toThrow();
  const legacy={...run,source:undefined,packId:'pack',packVersion:'1.0.0',viewSchema:1,view:{...view,blocks:[{kind:'NARRATION',text:'hello',tags:[]}]}};
  expect(decodeSummary(legacy).source.kind).toBe('PACK');
 });
 it('keeps terminal failed operations terminal and validates started runs',()=>{
  expect(decodeOperation({status:'FAILED',retryAfterMs:0,code:'UNAVAILABLE',run:null}).status).toBe('FAILED');
  expect(()=>decodeOperation({status:'STARTED',retryAfterMs:0,run:null})).toThrow();
 });
 it('bounds retry timing and refuses durable text',()=>{
  expect(retryDelay(-1,null)).toBe(1000);expect(retryDelay(900000,null)).toBe(60000);
  expect(retryDelay(undefined,'2')).toBe(2000);
  expect(()=>assertDurableCommand({id:'id',expectedRevision:0,command:{type:'SUBMIT_TEXT',text:'private'}})).toThrow();
 });
 it('does not replay a transient request after remount or store private properties',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockImplementation(async()=>new Response(JSON.stringify({status:'PROCESSING',retryAfterMs:1000}),{status:202,headers:{'content-type':'application/json'}})));
  const marker={owner:'owner',id:'request',path:'/api/game/dynamic-starts',createdAt:0,private:'sentinel'};
  await new GameTransport(()=> 'owner',async()=>{}).transient(marker,'POST',{suggestion:'sentinel'});
  expect(JSON.stringify(sessionStorage)).not.toContain('sentinel');
  await new GameTransport(()=> 'owner',async()=>{}).transient(marker,'POST',{suggestion:'sentinel'});
  expect(vi.mocked(fetch).mock.calls[1][1]?.method).toBe('GET');
 });
 it('does not accept HTML or regress revisions',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('<html>',{headers:{'content-type':'text/html'}})));
  const t=new GameTransport(()=> 'owner',async()=>{});
  await expect(t.read('/api/game/runs/current')).rejects.toThrow();
  t.adopt({...run,revision:2});expect(()=>t.adopt(run)).toThrow();
  expect(()=>t.adopt({...run,revision:2,locale:'pl'})).toThrow();
 });
});

describe('operation recovery',()=>{
 it('ignores a response after the owner changes',async()=>{
  let owner='owner'; let complete!:(value:Response)=>void;
  vi.stubGlobal('fetch',vi.fn(()=>new Promise<Response>(resolve=>{complete=resolve;})));
  const request=new GameTransport(()=>owner,async()=>{}).read('/api/game/runs/current');
  owner='other';complete(new Response(JSON.stringify(run),{headers:{'content-type':'application/json'}}));
  await expect(request).rejects.toThrow('GAME_OWNER_CHANGED');
 });
 it('does not retry a paid attempt on a terminal 503 result',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify(contracts.failed),{status:503,headers:{'content-type':'application/json'}})));
  const t=new GameTransport(()=> 'owner',async()=>{});
  const reply=await t.transient({owner:'owner',id:'id',path:'/api/game/dynamic-starts',createdAt:0},'POST',{});
  expect(reply.status).toBe(503);expect(fetch).toHaveBeenCalledTimes(1);
 });
 it('polls one operation at a time and stops at its terminal result',async()=>{
  vi.useFakeTimers();
  vi.stubGlobal('fetch',vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify(contracts.processing),{status:202,headers:{'content-type':'application/json'}}))
    .mockResolvedValueOnce(new Response(JSON.stringify(contracts.started),{headers:{'content-type':'application/json'}})));
  const t=new GameTransport(()=> 'owner',async()=>{}), abort=new AbortController(), accept=vi.fn();
  const pending=t.poll('/api/game/dynamic-starts/id',abort.signal,accept);
  await t.poll('/api/game/dynamic-starts/id',abort.signal,accept);
  await vi.advanceTimersByTimeAsync(1000);await pending;
  expect(fetch).toHaveBeenCalledTimes(2);expect(accept).toHaveBeenCalledTimes(2);
 });
});
