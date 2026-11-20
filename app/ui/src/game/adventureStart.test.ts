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

describe('resume from Play',()=>{
 it('opens the matching accepted run without loading the catalog or issuing a command',async()=>{
  const {flow,transport,handoff}=harness();
  vi.mocked(transport.read).mockImplementation(async path=>({status:200,body:path.endsWith('/current')?contracts.pack:caps}));
  await flow.load(true,contracts.pack.runId);
  expect(flow.getSnapshot()).toMatchObject({phase:'ready',loading:false,ready:{runId:contracts.pack.runId,revision:contracts.pack.revision}});
  expect(vi.mocked(transport.read).mock.calls.flat()).toEqual(['/api/game/capabilities','/api/game/runs/current']);
  expect(handoff).toHaveBeenCalledTimes(1);
  expect(transport.transient).not.toHaveBeenCalled();
 });
 it('shows the catalog when the preview no longer matches the active run',async()=>{
  const {flow,transport,handoff}=harness();
  const newer={...contracts.dynamic,runId:'623fc3d8-69e6-400d-943b-fea37699b793'};
  vi.mocked(transport.read).mockImplementation(async path=>({status:200,
   body:path.endsWith('/current')?newer:path.endsWith('/capabilities')?caps:[]}));
  await flow.load(true,contracts.pack.runId);
  expect(flow.getSnapshot()).toMatchObject({phase:'catalog',message:'resumeChanged',current:{runId:newer.runId}});
  expect(handoff).not.toHaveBeenCalled();
  expect(transport.transient).not.toHaveBeenCalled();
 });
 it('recovers an unresolved start before considering the saved preview',async()=>{
  sessionStorage.setItem('game-operation:owner:request',JSON.stringify({owner:'owner',id:'request',path:'/api/game/dynamic-starts',createdAt:1}));
  const {flow,transport,handoff}=harness();
  vi.mocked(transport.read).mockImplementation(async path=>({status:200,body:path.endsWith('/current')?contracts.pack:
   path.endsWith('/capabilities')?caps:path.endsWith('/request')?contracts.started:[]}));
  await flow.load(true,contracts.pack.runId);
  expect(transport.read).toHaveBeenCalledWith('/api/game/dynamic-starts/request');
  expect(flow.getSnapshot().ready?.runId).toBe(contracts.started.run.runId);
  expect(handoff).toHaveBeenCalledTimes(1);
  expect(transport.transient).not.toHaveBeenCalled();
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
it('clears a previously active current run when the server reports none after an ending',async()=>{
 const h=harness();vi.mocked(h.transport.read).mockImplementation(async(path:string)=>({status:200,body:path.endsWith('/current')?contracts.dynamic:path.endsWith('/capabilities')?caps:path.endsWith('/packs')?[pack]:[profile]}));
 await h.flow.load(false);expect(h.flow.getSnapshot().current?.status).toBe('ACTIVE');
 vi.mocked(h.transport.read).mockImplementation(async(path:string)=>({status:path.endsWith('/current')?404:200,body:path.endsWith('/capabilities')?caps:path.endsWith('/packs')?[pack]:[profile]}));
 await h.flow.load(false);expect(h.flow.getSnapshot().current).toBeNull();h.flow.select({kind:'PACK',pack});expect(h.flow.getSnapshot().phase).toBe('mode');h.flow.dispose();
});

describe('transient Pack preparation',()=>{
 const preparedPack:GamePackInfoDto={...pack,version:'1.1.0',setup:{...pack.setup!,transientTextSupported:true,preparationFingerprint:'reviewed-resources',inputs:[...pack.setup!.inputs,{id:'story-wish',kind:'FREE_TEXT',label:'Fictional wish',help:'Optional',required:false,maxItems:1,maxCodePoints:240,defaults:[],options:[],notice:null}],steps:[{id:'setup',title:'Choices',inputIds:['tone','interests','story-wish']}]}};
 it('uses a reviewed version identity and transient request, never a durable Pack start',()=>{
  const game:Adventure={kind:'PACK',pack:preparedPack};
  expect(startBody(game,{'game-locale':['en']},'private',caps,'uuid',true,'old')).toMatchObject({requestId:'uuid',packVersion:'1.1.0',packFingerprint:'reviewed-resources',suggestion:'private',expectedCurrentRunId:'old',confirmed:true});
  expect(startBody(game,{'game-locale':['en']},'private',caps,'uuid',false)).not.toHaveProperty('suggestion');
  expect(validation(game,{'game-locale':['en']},'😀'.repeat(241))).toContain('story-wish');
 });
 it('recovers an uncertain Pack start without repeating the body, and rechecks changed resources',async()=>{
  const {flow,transport}=harness();
  vi.mocked(transport.read).mockImplementation(async path=>({status:path.endsWith('/current')?404:200,body:path.endsWith('/capabilities')?caps:path.endsWith('/packs')?[preparedPack]:path.endsWith('/generator-profiles')?[]:contracts.started}));
  await flow.load();flow.select({kind:'PACK',pack:preparedPack});flow.generated(true);flow.review();
  vi.mocked(transport.transient).mockRejectedValue(new Error('lost'));await flow.confirm('private-pack-sentinel');
  expect(transport.transient).toHaveBeenCalledTimes(1);expect(transport.transient).toHaveBeenCalledWith(expect.objectContaining({path:'/api/game/pack-starts'}),'POST',expect.anything());
  expect(transport.read).toHaveBeenCalledWith(expect.stringMatching(/pack-starts\//));expect(JSON.stringify(flow.getSnapshot())).not.toContain('private-pack-sentinel');
  const changed={...preparedPack,setup:{...preparedPack.setup!,preparationFingerprint:'changed'}};
  flow.catalog();flow.select({kind:'PACK',pack:preparedPack});flow.generated(true);flow.replacement(true);flow.review();
  vi.mocked(transport.read).mockImplementation(async path=>({status:200,body:path.endsWith('/capabilities')?caps:[changed]}));
  await flow.confirm('private');expect(transport.transient).toHaveBeenCalledTimes(1);expect(flow.getSnapshot()).toMatchObject({phase:'setup',message:'stale'});
 });
 it('recovers Pack operation markers with GET only after reload',async()=>{
  sessionStorage.setItem('game-operation:owner:request',JSON.stringify({owner:'owner',id:'request',path:'/api/game/pack-starts',createdAt:1}));
  const {flow,transport}=harness();await flow.load();expect(flow.getSnapshot().phase).toBe('ready');expect(transport.transient).not.toHaveBeenCalled();
 });
});
