import 'fake-indexeddb/auto';
import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {beforeEach,afterEach,describe,it,expect,vi} from 'vitest';
import {AdventurePreparation} from './AdventurePreparation';
import {AdventureStart,type StartTransport} from '../../game/adventureStart';
import type {GameCapabilities,GeneratorProfile} from '../../game/gameApi';
import contracts from '../../game/fixtures/contracts.json';
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
 it.each(['en','pl'] as const)('renders metadata controls, masks private review and starts only after confirmation (%s)',async locale=>{
  const h=harness(),copy=adventureCopy[locale];
  await act(async()=>root.render(<AdventurePreparation owner="owner" locale={locale} onClose={h.close} flow={h.flow}/>));
  await click(contracts.profile.title);
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
