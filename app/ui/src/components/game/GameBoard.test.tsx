import {act} from 'react';
import {createRoot, type Root} from 'react-dom/client';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {GamePlaces} from './GamePlaces';
import type {GameViewV2} from '../../game/gameApi';
import {companionView} from '../../game/fixtures/companionViews';
import {decodeSummary} from '../../game/gameDecoders';
import fixtures from '../../game/fixtures/contracts.json';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
let host:HTMLDivElement,root:Root;
beforeEach(()=>{vi.spyOn(HTMLElement.prototype,'getBoundingClientRect').mockReturnValue(new DOMRect(0,0,720,600));host=document.createElement('div');document.body.append(host);root=createRoot(host);});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();vi.restoreAllMocks();});
function view():GameViewV2 {
 const v=companionView();v.places={currentPlaceId:'camp',layoutKind:'HEX',visitedPlaceIds:['camp'],availableMoves:['river'],
  places:[{id:'camp',label:'Camp',x:0,y:0,moveBehavior:'ENTER_SCENE'},{id:'river',label:'River',x:1,y:0,moveBehavior:'ENTER_SCENE'}],links:[{from:'camp',to:'river',bidirectional:true}]};
 v.presentation!.places=[{placeId:'camp',terrainAssetId:'terrain.hive'},{placeId:'river',terrainAssetId:'terrain.water'}] as never;
 return v;
}
const button=(name:string)=>[...host.querySelectorAll('button')].find(b=>(b.getAttribute('aria-label')??b.textContent)===name)!;
it('shows exactly one board with one normalized player, with list parity and read-only inspection',async()=>{
 const v=view(),submit=vi.fn();v.presentation!.playerAssetId='character.luna-moth';
 await act(async()=>root.render(<GamePlaces places={v.places!} presentation={v.presentation} locale="en" locked={false} submit={submit}/>));
 expect(host.querySelector('details')).toBeNull();expect(host.querySelectorAll('.game-board')).toHaveLength(1);
 expect(host.querySelectorAll('.game-board-player')).toHaveLength(1);expect(host.querySelector('.game-tile-marker')).toBeNull();
 expect(host.querySelector('.game-board-player')?.getAttribute('data-current-place')).toBe('camp');
 expect(host.textContent).toContain('Going here opens its moment.');
 const contents=()=>[...host.querySelectorAll('[data-place-id]')].map(n=>[n.querySelector('strong')?.textContent,...[...n.querySelectorAll('button')].map(b=>b.getAttribute('aria-label')??b.textContent)]);
 const labels=contents();
 await act(async()=>button('Look: River').click());expect(submit).not.toHaveBeenCalled();
 await act(async()=>button('Back to places').click());expect(document.activeElement).toBe(button('Look: River'));
 await act(async()=>button('Show place list').click());
 expect(host.querySelector('.game-hex-map')).toBeNull();expect(host.querySelector('.game-board-player')).toBeNull();
 expect(contents()).toEqual(labels);
 expect(document.activeElement).toBe(button('Look: River'));
 await act(async()=>button('Visit: River').click());expect(submit).toHaveBeenCalledExactlyOnceWith({type:'MOVE',placeId:'river'});
});
it('uses public hint precedence and generic wording for unknown behavior, retaining text when art is absent',async()=>{
 const v=view();v.places!.movementHint='Finish this moment.';v.places!.places[1].moveHint='Prepare path.';v.places!.availableMoves=[];
 const render=async(locked=false)=>act(async()=>root.render(<GamePlaces places={v.places!} presentation={v.presentation} locale="pl" locked={locked} submit={vi.fn()}/>));
 await render();expect(host.querySelectorAll('[data-place-id]')[1].textContent).toContain('Finish this moment.');
 await render(true);expect(host.querySelectorAll('[data-place-id]')[1].textContent).toContain('Poczekaj');
 v.places!.movementHint=null;await render();expect(host.querySelectorAll('[data-place-id]')[1].textContent).toContain('Prepare path.');
 v.places!.places[1].moveBehavior='FUTURE';v.presentation=null;await render();expect(button('Przejdź do: River')).toBeDefined();expect(host.querySelector('svg')).toBeNull();
});
it('decodes optional entry fields without rejecting story for bad hints or unknown behavior',()=>{
 const raw=structuredClone(fixtures.placeEntry);expect(decodeSummary(raw).view!.places!.movementHint).toBe('Choose or pause.');
 (raw.view.places as unknown as {movementHint:unknown}).movementHint={secret:'bad'};
 raw.view.places.places[1].moveBehavior='FUTURE';(raw.view.places.places[1] as unknown as {moveHint:unknown}).moveHint=['bad'];
 const decoded=decodeSummary(raw).view!.places!;expect(decoded.movementHint).toBeNull();expect(decoded.places[1].moveHint).toBeNull();expect(decoded.places[1].moveBehavior).toBeNull();
});
it('keeps long Polish labels readable in narrow list fallback and resets marker/announcements on run replacement',async()=>{
 const v=view(),submit=vi.fn();v.places!.places[1].label='Długa nazwa spokojnego miejsca '.repeat(12);
 vi.spyOn(HTMLElement.prototype,'getBoundingClientRect').mockReturnValue(new DOMRect(0,0,160,600));
 await act(async()=>root.render(<GamePlaces places={v.places!} presentation={v.presentation} runId="one" revision={0} locale="pl" locked={false} submit={submit}/>));
 expect(host.querySelector('.game-hex-map')).toBeNull();expect(host.textContent).toContain(v.places!.places[1].label);
 expect(host.querySelector('[aria-current=location]')?.textContent).toContain('Obecne miejsce');
 vi.spyOn(HTMLElement.prototype,'getBoundingClientRect').mockReturnValue(new DOMRect(0,0,720,600));
 await act(async()=>root.render(<GamePlaces places={{...v.places!,currentPlaceId:'river'}} presentation={v.presentation} runId="two" revision={12} locale="pl" locked={false} submit={submit}/>));
 expect(host.querySelector('.game-board-announcement')?.textContent).toBe('');expect(submit).not.toHaveBeenCalled();
});
