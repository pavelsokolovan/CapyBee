import 'fake-indexeddb/auto';
import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {beforeEach,afterEach,describe,it,expect,vi} from 'vitest';
import {AdventurePreparation} from './AdventurePreparation';
import {AdventureStart,type StartTransport} from '../../game/adventureStart';
import type {GameCapabilities,GeneratorProfile} from '../../game/gameApi';
import contracts from '../../game/fixtures/contracts.json';
import {illustratedRun} from '../../game/fixtures/illustratedRun';
import {adventureCopy} from '../../game/adventureCopy';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
let host:HTMLDivElement,root:Root;
const caps:GameCapabilities={schemaVersion:1,clientEnabled:true,gameEnabled:true,packStartEnabled:true,packGenerationEnabled:true,dynamicStartEnabled:true,supportedViewSchemas:[1,2],viewFeatures:['canAdvance'],disclosureRevision:1,dynamicCancelMode:'BEST_EFFORT'};
function harness(){
 const transport:StartTransport={setLanguage:vi.fn(),detach:vi.fn(),read:vi.fn(async(path:string)=>({status:path.endsWith('/current')?404:200,body:path.endsWith('/capabilities')?caps:path.endsWith('/packs')?[]:path.endsWith('/generator-profiles')?[contracts.profile]:contracts.profile})),transient:vi.fn(async()=>({status:200,body:contracts.started})),cancel:vi.fn(async()=>({status:200,body:{status:'CANCELLED'}})),poll:vi.fn(async()=>{})};
 const ready=vi.fn(),close=vi.fn(),flow=new AdventureStart('owner','en',transport,ready);return{transport,ready,close,flow};
}
async function click(label:string){const button=Array.from(host.querySelectorAll('button')).find(b=>b.textContent?.includes(label));expect(button).toBeTruthy();await act(async()=>button!.click());}
beforeEach(()=>{sessionStorage.clear();host=document.createElement('div');document.body.append(host);root=createRoot(host);});
afterEach(async()=>{await act(()=>root.unmount());host.remove();vi.restoreAllMocks();});
describe('preparation panel',()=>{
 it('resumes a non-hive illustrated run in the scene panel',async()=>{
  const h=harness(),run=illustratedRun();
  vi.mocked(h.transport.read).mockImplementation(async path=>({
   status:200,body:path.endsWith('/capabilities')?caps:run,
  }));
  await act(async()=>root.render(<AdventurePreparation owner="owner" locale="en" onClose={h.close}
   flow={h.flow} resumeRunId={run.runId}/>));
  expect(host.querySelector('.adventure-scene-panel .game-scene-first')).not.toBeNull();
  expect(host.querySelector('.game-expedition-location')?.textContent).toBe('Obóz');
  expect(h.transport.transient).not.toHaveBeenCalled();
 });
 it.each(['en','pl'] as const)('renders metadata controls, masks private review and starts only after confirmation (%s)',async locale=>{
  const h=harness(),copy=adventureCopy[locale];
  await act(async()=>root.render(<AdventurePreparation owner="owner" locale={locale} onClose={h.close} flow={h.flow}/>));
  await click(contracts.profile.title);
  await click(copy.customize);
  expect(host.querySelectorAll('input[type=checkbox]')).toHaveLength(2);
  await click(copy.next);await click(copy.next);await click(copy.next);
  const area=host.querySelector('textarea')!;expect(area).toBeTruthy();
  await act(async()=>{const setter=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')!.set!;setter.call(area,'private-sentinel');area.dispatchEvent(new Event('input',{bubbles:true}));area.dispatchEvent(new Event('change',{bubbles:true}));});
  // React's controlled input is also checked independently through the form event.
  expect(h.transport.transient).not.toHaveBeenCalled();
  await click(copy.review);expect(host.querySelector('textarea')).toBeNull();
  expect(host.textContent).toContain(copy.ideaAdded);
  expect(host.textContent).not.toContain('private-sentinel');expect(JSON.stringify(sessionStorage)).not.toContain('private-sentinel');
  await click(copy.start);expect(h.transport.transient).toHaveBeenCalledWith(expect.anything(),'POST',expect.objectContaining({suggestion:'private-sentinel'}));expect(h.transport.transient).toHaveBeenCalledTimes(1);expect(h.ready).toHaveBeenCalledTimes(1);
  await vi.waitFor(async()=>{await act(async()=>{await new Promise(resolve=>setTimeout(resolve,0));});expect(host.textContent).toContain(copy.ready);});
 });
 it('supports keyboard focus and browser Back without abandoning a run',async()=>{
  const h=harness();await act(async()=>root.render(<AdventurePreparation owner="owner" locale="en" onClose={h.close} flow={h.flow}/>));
  expect(document.activeElement?.id).toBe('adventure-title');
  await act(async()=>window.dispatchEvent(new PopStateEvent('popstate',{state:null})));
  expect(h.close).toHaveBeenCalled();expect(h.transport.transient).not.toHaveBeenCalled();
 });
 it('locks the final action while offline and never starts on reconnection',async()=>{
  const h=harness();await act(async()=>root.render(<AdventurePreparation owner="owner" locale="en" onClose={h.close} flow={h.flow}/>));
  await act(async()=>{h.flow.select({kind:'DYNAMIC',profile:contracts.profile as GeneratorProfile});h.flow.review();});
  vi.spyOn(navigator,'onLine','get').mockReturnValue(false);
  await act(async()=>window.dispatchEvent(new Event('offline')));
  const start=Array.from(host.querySelectorAll('button')).find(b=>b.textContent===adventureCopy.en.start)!;
  expect(start.disabled).toBe(true);
  vi.spyOn(navigator,'onLine','get').mockReturnValue(true);
  await act(async()=>window.dispatchEvent(new Event('online')));
  expect(h.transport.transient).not.toHaveBeenCalled();
 });
});

it('offers a catalog retry only until loading succeeds',async()=>{
 const h=harness(),read=vi.mocked(h.transport.read);
 read.mockResolvedValueOnce({status:503,body:{code:'SERVICE_UNAVAILABLE'}});
 await act(async()=>root.render(<AdventurePreparation owner="owner" locale="en" onClose={h.close} flow={h.flow}/>));
 expect(host.textContent).toContain(adventureCopy.en.unavailable);
 await click(adventureCopy.en.catalogRetry);
 expect(host.textContent).not.toContain(adventureCopy.en.catalogRetry);
 const card=host.querySelector('.adventure-catalog-row');
 expect(card?.querySelector('strong')?.textContent).toBe(contracts.profile.title);
 expect(card?.querySelector('span')?.textContent).toBe(adventureCopy.en.dynamic);
});

it('shows the pack title once in manual setup and starts an authored pack with random presets',async()=>{
 const h=harness(),copy=adventureCopy.en,title='Reviewed game';
 const input=(contracts.profile as GeneratorProfile).inputs.find(i=>i.id==='tone')!;
 const pack={id:'fixture',version:'1.0.0',locales:['en'],defaultLocale:'en',startInputs:[],generated:false,
  setup:{schemaVersion:1,metadataLocale:'en',title,description:null,transientTextSupported:false,
   steps:[{id:'setup',title,inputIds:[input.id]}],inputs:[input]}};
 const fallback=vi.mocked(h.transport.read).getMockImplementation()!;
 vi.mocked(h.transport.read).mockImplementation(path=>path.endsWith('/packs')
  ?Promise.resolve({status:200,body:[pack]}):fallback(path));
 vi.spyOn(Math,'random').mockReturnValue(0);
 await act(async()=>root.render(<AdventurePreparation owner="owner" locale="en" onClose={h.close} flow={h.flow}/>));
 await click(title);
 expect(host.querySelector('fieldset')).toBeNull();
 await click(copy.customize);
 expect(Array.from(host.querySelectorAll('h3,h4')).filter(h=>h.textContent===title)).toHaveLength(1);
 expect(host.querySelector('fieldset')).not.toBeNull();
 const back=Array.from(host.querySelectorAll('button')).find(button=>button.textContent===copy.back)!;
 await act(async()=>back.click());
 await click(copy.quickStart);
 expect(h.transport.transient).toHaveBeenCalledExactlyOnceWith(expect.anything(),'POST',
  expect.objectContaining({packId:pack.id,generated:false,locale:'en',startInputs:{tone:input.options[0].id}}));
});

it('reviews random AI presets before sending a start request',async()=>{
 const h=harness(),copy=adventureCopy.en;
 await act(async()=>root.render(<AdventurePreparation owner="owner" locale="en" onClose={h.close} flow={h.flow}/>));
 await click(contracts.profile.title);
 await click(copy.customize);
 for(let step=0;step<3;step++)await click(copy.next);
 const area=host.querySelector('textarea')!;
 await act(async()=>{
  const setter=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')!.set!;
  setter.call(area,'private-draft');
  area.dispatchEvent(new Event('input',{bubbles:true}));
 });
 expect(area.value).toBe('private-draft');
 await act(async()=>{while(h.flow.getSnapshot().phase==='setup')h.flow.back();});
 await click(copy.quickStart);
 expect(host.textContent).toContain(copy.ideaNone);
 expect(host.textContent).toContain(copy.dynamicDisclosure);
 expect(h.transport.transient).not.toHaveBeenCalled();
 await click(copy.start);
 expect(h.transport.transient).toHaveBeenCalledTimes(1);
 expect(vi.mocked(h.transport.transient).mock.calls[0][2]).not.toHaveProperty('suggestion');
});

it('shows the localized detective card and confirms Quick Start as a dynamic case',async()=>{
 const h=harness(),copy=adventureCopy.pl;
 const detective={...contracts.profile,id:'classic-detective',title:'Klasyczna zagadka detektywistyczna',
  description:'Zbierz dowody i sprawdź alibi.',metadataLocale:'pl'};
 const read=vi.mocked(h.transport.read),fallback=read.getMockImplementation()!;
 read.mockImplementation(path=>{
  if(path.endsWith('/generator-profiles'))return Promise.resolve({status:200,body:[detective]});
  if(path.endsWith('/generator-profiles/classic-detective'))return Promise.resolve({status:200,body:detective});
  return fallback(path);
 });
 await act(async()=>root.render(<AdventurePreparation owner="owner" locale="pl" onClose={h.close} flow={h.flow}/>));
 expect(host.querySelector('.adventure-catalog-row span')?.textContent).toBe(detective.description);
 await click(detective.title);
 await click(copy.quickStart);
 expect(host.textContent).toContain(copy.dynamicDisclosure);
 expect(h.transport.transient).not.toHaveBeenCalled();
 await click(copy.start);
 expect(h.transport.transient).toHaveBeenCalledExactlyOnceWith(
  expect.objectContaining({path:'/api/game/dynamic-starts'}),'POST',
  expect.objectContaining({profileId:detective.id,locale:'pl',confirmed:true}),
 );
});
