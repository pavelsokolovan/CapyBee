import {act} from 'react';
import {createRoot,type Root} from 'react-dom/client';
import {beforeEach,afterEach,it,expect,vi} from 'vitest';
import {GameRun} from './GameRun';
import {GameEncounter} from './GameEncounter';
import {companionView} from '../../game/fixtures/companionViews';
import type {GameRunSummaryDto} from '../../game/gameApi';
import type {RunController,RunState} from '../../game/runController';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
let host:HTMLDivElement,root:Root;
beforeEach(()=>{host=document.createElement('div');document.body.append(host);root=createRoot(host);});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();});
it('keeps the same accessible caption when the backdrop is removed in Text mode',async()=>{
 const view=companionView(),caption=<h4 lang="pl" tabIndex={-1}>Cicha Łąka</h4>;
 const render=(textMode:boolean)=>act(async()=>root.render(<GameEncounter view={view} stage caption={caption} textMode={textMode}>Story</GameEncounter>));
 await render(false);
 const heading=host.querySelector('.game-encounter-backdrop > h4')!;
 expect(heading.closest('[aria-hidden=true]')).toBeNull();
 expect(host.querySelector('.game-encounter-stage')).not.toBeNull();
 await render(true);
 expect(host.querySelector('h4')).toBe(heading);
 expect(host.querySelector('.game-encounter-backdrop,.game-encounter-stage')).toBeNull();
 await render(false);
 expect(host.querySelector('.game-encounter-backdrop > h4')).toBe(heading);
});
it('omits the stage that anchors the bubble tail when no characters are present',async()=>{
 const view=companionView();
 view.characters.forEach(character=>{character.inCurrentScene=false;});
 await act(async()=>root.render(<GameEncounter view={view} stage caption={<h4>Quiet Meadow</h4>}>Story</GameEncounter>));
 expect(host.querySelector('.game-encounter-backdrop > svg')).not.toBeNull();
 expect(host.querySelector('.game-encounter-stage')).toBeNull();
});
it.each(['en','pl'] as const)('composes accepted art and preserves the draft when dismissing the %s scene menu',async locale=>{
 const view=companionView();view.presentation!.backdropAssetId='backdrop.reed-bank';view.input={mode:'CHOICE_OR_TEXT',maxChars:200};
 const run:GameRunSummaryDto={runId:'one',source:{kind:'PACK',packId:'fixture',packVersion:'1.0.0'},packId:'fixture',packVersion:'1.0.0',locale:'pl',status:'ACTIVE',revision:0,updatedAt:'2026-11-16T00:00:00Z',viewSchema:2,view};
 let state:RunState={run,pending:false,message:'saved'};const listeners=new Set<()=>void>();
 const controller={getSnapshot:()=>state,subscribe:(fn:()=>void)=>{listeners.add(fn);return()=>{listeners.delete(fn);};},initialize:vi.fn(async()=>{}),detach:vi.fn(),submit:vi.fn()};
 await act(async()=>root.render(<GameRun run={run} owner="owner" locale={locale} controller={controller as unknown as RunController} onAnother={vi.fn()}/>));
 expect(host.querySelectorAll('.game-encounter-backdrop > svg')).toHaveLength(1);expect(host.querySelectorAll('.game-current-characters')).toHaveLength(1);expect(host.querySelectorAll('fieldset')).toHaveLength(1);
 const area=host.querySelector('textarea')!;const setter=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')!.set!;
 await act(async()=>{setter.call(area,'A quiet fictional wish');area.dispatchEvent(new Event('input',{bubbles:true}));});
 const menu=host.querySelector<HTMLDetailsElement>('.game-scene-menu')!;
 const end=menu.querySelector('button')!;
 expect(menu.querySelectorAll('button')).toHaveLength(1);
 menu.open=true;end.focus();
 await act(async()=>end.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
 expect(menu.open).toBe(false);expect(document.activeElement).toBe(menu.querySelector('summary'));
 expect(host.querySelector('textarea')).toBe(area);expect(area.value).toBe('A quiet fictional wish');expect(controller.submit).not.toHaveBeenCalled();
 await act(async()=>{state={...state,pending:true,message:'waiting'};listeners.forEach(fn=>fn());});
 expect(host.querySelector('.game-block')?.textContent).toContain('We can take our time.');expect(host.querySelector('fieldset')?.disabled).toBe(true);
 expect(host.querySelector('.game-encounter-backdrop svg')?.getAttribute('aria-hidden')).toBe('true');

});
it('retains the legacy text renderer without a presentation promise',async()=>{
 const {GameEncounter}=await import('./GameEncounter');const view=companionView();delete view.presentation;
 await act(async()=>root.render(<GameEncounter view={view}><p>Readable story</p></GameEncounter>));expect(host.querySelector('svg')).toBeNull();expect(host.textContent).toBe('Readable story');
});

it('uses neutral catalog artwork for unknown backdrop IDs without external resources',async()=>{
 const {GameEncounter}=await import('./GameEncounter');const view=companionView();view.presentation!.backdropAssetId='https://invalid.example/art.svg' as never;
 await act(async()=>root.render(<GameEncounter view={view}><p>Accepted story</p></GameEncounter>));
 expect(host.querySelectorAll('svg')).toHaveLength(1);expect(host.querySelector('image,script,img')).toBeNull();expect(host.innerHTML).not.toContain('invalid.example');
});
