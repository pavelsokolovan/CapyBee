import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import {AppUpdateNotice} from './AppUpdateNotice';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
let host:HTMLDivElement,root:Root,workers:EventTarget & {controller:object|null;getRegistration:ReturnType<typeof vi.fn>};
beforeEach(()=>{host=document.createElement('div');document.body.append(host);root=createRoot(host);workers=Object.assign(new EventTarget(),{controller:{} as object|null,getRegistration:vi.fn().mockResolvedValue({update:vi.fn().mockResolvedValue(undefined)})});const nav=Object.create(navigator);Object.defineProperty(nav,'serviceWorker',{value:workers,configurable:true});vi.stubGlobal('navigator',nav);});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();vi.restoreAllMocks();vi.unstubAllGlobals();});
it('notifies an open controlled app of an update without reloading until explicit action',async()=>{
 const reload=vi.fn();await act(async()=>root.render(<AppUpdateNotice reload={reload}/>));expect(host.textContent).toBe('');
 await act(async()=>workers.dispatchEvent(new Event('controllerchange')));expect(host.textContent).toContain('Unsent text will be cleared');expect(reload).not.toHaveBeenCalled();
 await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent==='Later')!.click());expect(host.querySelector('aside')).toBeNull();expect(reload).not.toHaveBeenCalled();
 await act(async()=>workers.dispatchEvent(new Event('controllerchange')));
 await act(async()=>host.querySelector('button')!.click());expect(reload).toHaveBeenCalledOnce();
});
it('keeps initial installation quiet and removes subscriptions on unmount',async()=>{
 workers.controller=null;await act(async()=>root.render(<AppUpdateNotice/>));workers.controller={};await act(async()=>workers.dispatchEvent(new Event('controllerchange')));expect(host.querySelector('aside')).toBeNull();
 await act(async()=>root.render(<p>Unmounted notice</p>));await act(async()=>workers.dispatchEvent(new Event('controllerchange')));expect(host.textContent).toBe('Unmounted notice');
});
it('checks for updates on return without turning offline failures into an error screen',async()=>{
 workers.getRegistration.mockRejectedValue(new Error('offline'));await act(async()=>root.render(<AppUpdateNotice/>));await act(async()=>document.dispatchEvent(new Event('visibilitychange')));expect(workers.getRegistration).toHaveBeenCalled();expect(host.textContent).toBe('');
});
