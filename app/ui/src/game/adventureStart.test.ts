import {beforeEach,afterEach,describe,it,expect,vi} from 'vitest';
import {AdventureStart,storedStart,type StartTransport} from './adventureStart';
import {compatible,effective,initialAnswers,inputsOf,startBody,validation,codePoints} from './preparation';
import type {Adventure} from './preparation';
import type {GameCapabilities,GamePackInfoDto,GeneratorProfile} from './gameApi';
import contracts from './fixtures/contracts.json';
const caps:GameCapabilities={schemaVersion:1,clientEnabled:true,gameEnabled:true,packStartEnabled:true,packGenerationEnabled:true,dynamicStartEnabled:true,supportedViewSchemas:[1,2],viewFeatures:['canAdvance'],disclosureRevision:1,dynamicCancelMode:'BEST_EFFORT'};
const profile=contracts.profile as GeneratorProfile;
const dynamic:Adventure={kind:'DYNAMIC',profile};
const pack:GamePackInfoDto={id:'fixture',version:'1.0.0',locales:['en'],defaultLocale:'en',startInputs:[],generated:true,setup:{schemaVersion:1,metadataLocale:'en',title:'Reviewed game',description:null,transientTextSupported:false,steps:[{id:'setup',title:'Choices',inputIds:['tone','interests']}],inputs:profile.inputs.filter(i=>['tone','interests'].includes(i.id))}};
function harness(){
 const transport:StartTransport={setLanguage:vi.fn(),detach:vi.fn(),read:vi.fn(async(path:string)=>({status:path.endsWith('/current')?404:200,body:path.endsWith('/capabilities')?caps:path.endsWith('/packs')?[pack]:path.endsWith('/generator-profiles')?[profile]:path.includes('/generator-profiles/')?profile:contracts.started})),
 transient:vi.fn(async()=>({status:200,body:contracts.started})),cancel:vi.fn(async()=>({status:200,body:{status:'CANCELLED'}})),poll:vi.fn(async()=>{})};
 const handoff=vi.fn(),flow=new AdventureStart('owner','en',transport,handoff);return{flow,transport,handoff};
}
beforeEach(()=>{sessionStorage.clear();vi.stubGlobal('navigator',{onLine:true});});
afterEach(()=>{vi.unstubAllGlobals();});
describe('metadata preparation',()=>{
 it('preserves defaults, explicit clearing, locale and separate Pack encoding',()=>{
  expect(compatible(dynamic)).toBe(true);expect(initialAnswers(dynamic,'pl')['game-locale']).toEqual(['pl']);
  const tone=inputsOf(dynamic).find(i=>i.id==='tone')!;
  expect(effective(tone,{})).toEqual(['curious']);expect(effective(tone,{tone:[]})).toEqual([]);
  const request=startBody(dynamic,{'game-locale':['en'],tone:[],interests:['animals']},'private',caps,'uuid',false);
  expect(request).toMatchObject({requestId:'uuid',suggestion:'private',confirmed:true,selections:{tone:[],interests:['animals']}});
  expect('length' in ('selections' in request?request.selections:{})).toBe(false);
  expect(startBody({kind:'PACK',pack},{'game-locale':['en'],interests:['animals','nature']},'',caps,'uuid',false)).toMatchObject({id:'uuid',startInputs:{tone:'curious',interests:'animals,nature'},generated:false});
 });
 it('rejects unknown metadata, excessive choices and Unicode code-point overflow',()=>{
  expect(validation(dynamic,{'game-locale':['en'],interests:['animals','nature','bad']},'')).toContain('interests');
  expect(codePoints('😀')).toBe(1);expect(validation(dynamic,{'game-locale':['en']},'😀'.repeat(301))).toContain('story-wish');
  expect(compatible({kind:'PACK',pack:{...pack,setup:undefined}})).toBe(false);
 });
});
describe('confirmed starts',()=>{
 it('never writes on load, selection, editing or remount',async()=>{
  const {flow,transport}=harness();await flow.load();flow.select(dynamic);flow.answer('tone',['cozy']);flow.review();
  expect(transport.transient).not.toHaveBeenCalled();flow.dispose();
  const again=harness();await again.flow.load();expect(again.transport.transient).not.toHaveBeenCalled();
 });
 it('single-flights double taps, masks the draft from state and hands off a saved run',async()=>{
  const {flow,transport,handoff}=harness();await flow.load();flow.select(dynamic);flow.review();
  await Promise.all([flow.confirm('private-sentinel'),flow.confirm('private-sentinel')]);
  expect(transport.transient).toHaveBeenCalledTimes(1);expect(handoff).toHaveBeenCalledTimes(1);
  expect(flow.getSnapshot().phase).toBe('ready');expect(JSON.stringify(flow.getSnapshot())).not.toContain('private-sentinel');
  expect(JSON.stringify(sessionStorage)).not.toContain('private-sentinel');
 });
 it('checks an uncertain response with GET rather than sending again',async()=>{
  const {flow,transport}=harness();vi.mocked(transport.transient).mockRejectedValue(new Error('lost response'));
  await flow.load();flow.select(dynamic);flow.review();await flow.confirm('private');
  expect(transport.transient).toHaveBeenCalledTimes(1);expect(transport.read).toHaveBeenCalledWith(expect.stringMatching(/dynamic-starts\//));
  expect(flow.getSnapshot().phase).toBe('ready');
 });
 it('does not infer a missing operation succeeded from an unrelated current run',async()=>{
  const {flow,transport,handoff}=harness();
  vi.mocked(transport.transient).mockResolvedValue({status:202,body:contracts.processing});
  await flow.load();flow.select(dynamic);flow.review();await flow.confirm('');
  vi.mocked(transport.read).mockImplementation(async path=>({status:path.includes('/dynamic-starts/')?404:200,body:path.endsWith('/current')?contracts.pack:{}}));
  await flow.check();expect(flow.getSnapshot().phase).toBe('unknown');expect(handoff).not.toHaveBeenCalled();
  expect(flow.getSnapshot().current?.source.kind).toBe('PACK');expect(transport.transient).toHaveBeenCalledTimes(1);
 });
 it('recovers a body-free marker after reload with no POST',async()=>{
  sessionStorage.setItem('game-operation:owner:request',JSON.stringify({owner:'owner',id:'request',path:'/api/game/dynamic-starts',createdAt:1}));
  const {flow,transport}=harness();await flow.load();expect(flow.getSnapshot().phase).toBe('ready');expect(transport.transient).not.toHaveBeenCalled();
  expect(storedStart('other')).toBeUndefined();
 });
 it('rechecks identity and invalidates stale review before paying',async()=>{
  const {flow,transport}=harness();await flow.load();flow.select(dynamic);flow.review();
  vi.mocked(transport.read).mockImplementation(async path=>({status:200,body:path.endsWith('/capabilities')?caps:{...profile,fingerprint:'changed'}}));
  await flow.confirm('private');expect(transport.transient).not.toHaveBeenCalled();expect(flow.getSnapshot()).toMatchObject({phase:'setup',message:'stale'});
 });
 it('requires explicit replacement and preserves the old run when generation fails',async()=>{
  const {flow,transport}=harness();
  vi.mocked(transport.read).mockImplementation(async path=>({status:200,body:path.endsWith('/current')?contracts.pack:path.endsWith('/capabilities')?caps:path.endsWith('/packs')?[pack]:path.endsWith('/generator-profiles')?[profile]:profile}));
  await flow.load();flow.select({kind:'PACK',pack});expect(flow.getSnapshot().message).toBe('packConflict');
  flow.select(dynamic);flow.review();await flow.confirm('');expect(transport.transient).not.toHaveBeenCalled();
  flow.replacement(true);vi.mocked(transport.transient).mockResolvedValue({status:503,body:contracts.failed});await flow.confirm('');
  expect(flow.getSnapshot().current?.runId).toBe(contracts.pack.runId);expect(flow.getSnapshot().phase).toBe('failed');
  expect(transport.transient).toHaveBeenCalledWith(expect.anything(),'POST',expect.objectContaining({expectedCurrentRunId:contracts.pack.runId}));
 });
 it.each(['NEEDS_INPUT','FALLBACK_OFFERED','CANCELLED','FAILED'])('handles %s without another attempt',async status=>{
  const {flow,transport}=harness();vi.mocked(transport.transient).mockResolvedValue({status:status==='FAILED'?503:200,body:{status,code:status}});
  await flow.load();flow.select(dynamic);flow.review();await flow.confirm('');expect(flow.getSnapshot().phase).toBe(({NEEDS_INPUT:'needsInput',FALLBACK_OFFERED:'fallback',CANCELLED:'cancelled',FAILED:'failed'} as Record<string,string>)[status]);
  expect(transport.transient).toHaveBeenCalledTimes(1);
 });
 it('keeps current state when discovery fails and no new start is available',async()=>{
  const {flow,transport}=harness();
  vi.mocked(transport.read).mockImplementation(async path=>{if(path.endsWith('/current'))return{status:200,body:contracts.pack};if(path.endsWith('/capabilities'))return{status:200,body:{...caps,dynamicStartEnabled:false,packStartEnabled:false}};throw new Error('offline');});
  await flow.load();expect(flow.getSnapshot().current?.runId).toBe(contracts.pack.runId);expect(flow.getSnapshot().games).toEqual([]);
 });
});

describe('late work and subsequent attempts',()=>{
 it('does not send after the player leaves during identity review',async()=>{
  const {flow,transport}=harness();await flow.load();flow.select(dynamic);flow.review();
  let finish!:(value:{status:number;body:unknown})=>void;
  vi.mocked(transport.read).mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
  const pending=flow.confirm('private');flow.dispose();finish({status:200,body:caps});await pending;
  expect(transport.transient).not.toHaveBeenCalled();
 });
 it('requires review again and can poll a new attempt after an unknown result',async()=>{
  const {flow,transport}=harness();vi.mocked(transport.transient).mockResolvedValue({status:202,body:contracts.processing});
  await flow.load();flow.select(dynamic);flow.review();await flow.confirm('');await Promise.resolve();
  expect(flow.getSnapshot().phase).toBe('unknown');
  flow.step(0);flow.review();await flow.confirm('');await Promise.resolve();
  expect(transport.transient).toHaveBeenCalledTimes(2);expect(transport.poll).toHaveBeenCalledTimes(2);
 });
 it('keeps the chosen game locale when shell metadata changes',async()=>{
  const {flow,transport}=harness();await flow.load();flow.select(dynamic);flow.answer('game-locale',['en']);
  await flow.shellLocale('pl');expect(flow.getSnapshot().answers['game-locale']).toEqual(['en']);expect(transport.setLanguage).toHaveBeenLastCalledWith('pl');
 });
});
