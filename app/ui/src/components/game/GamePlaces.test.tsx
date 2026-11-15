import {act} from 'react';
import {createRoot, type Root} from 'react-dom/client';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {GamePlaces, hexLayout} from './GamePlaces';
import {SceneBlocks, ScenePanels} from './GameScene';
import type {PlacesView} from '../../game/gameApi';
import {decodeSummary} from '../../game/gameDecoders';
import fixtures from '../../game/fixtures/contracts.json';

(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
let host: HTMLDivElement, root: Root;
beforeEach(() => {host = document.createElement('div'); document.body.append(host); root = createRoot(host);});
afterEach(async () => {await act(async () => root.unmount()); host.remove();});
function places(): PlacesView {
 return {layoutKind: 'HEX', currentPlaceId: 'home', visitedPlaceIds: ['home', 'far'], availableMoves: ['far'],
  places: [{id: 'home', label: 'Dom', x: 0, y: 0}, {id: 'near', label: 'Łąka', x: 1, y: 0},
   {id: 'far', label: 'Most', x: 2, y: 0, description: 'Spokojne miejsce.'}],
  links: [{from: 'home', to: 'near', bidirectional: true}]};
}
const buttons = (prefix: string) => [...host.querySelectorAll('button')].filter(b => b.textContent?.startsWith(prefix));
it('keeps list/map parity and submits original IDs regardless of links, adjacency and visited state', async () => {
 const p = places(), submit = vi.fn();
 await act(async () => root.render(<GamePlaces places={p} locale="en" locked={false} submit={submit}/>));
 expect(host.querySelector('.game-hex-map')).not.toBeNull();
 expect(host.querySelector('[aria-current=location]')?.textContent).toContain('◉ Current place');
 expect(host.querySelectorAll('li')[2].textContent).toContain('✓ Visited');
 const before = [...host.querySelectorAll('li')].map(li => li.textContent);
 expect(buttons('Go to:').map(b => b.disabled)).toEqual([true, true, false]);
 await act(async () => buttons('Go to:')[1].click()); expect(submit).not.toHaveBeenCalled();
 await act(async () => buttons('Go to:')[2].click()); expect(submit).toHaveBeenCalledWith({type: 'MOVE', placeId: 'far'});
 await act(async () => buttons('Show place list')[0].click());
 expect(host.querySelector('.game-hex-map')).toBeNull();
 expect([...host.querySelectorAll('li')].map(li => li.textContent)).toEqual(before);
 expect(buttons('Go to:').map(b => b.disabled)).toEqual([true, true, false]);
});
it.each(['LIST', 'GRAPH', 'GRID', 'FREEFORM', 'FUTURE'])('uses the list for unsupported %s layout', kind => {
 const p = places(); p.layoutKind = kind; expect(hexLayout(p)).toBeNull();
});
it('rejects absent, duplicate, fractional, huge, sparse and overlarge layouts', () => {
 for (const change of [
  (p: PlacesView) => {p.places[0].x = null;},
  (p: PlacesView) => {delete (p.places[0] as {y?: number | null}).y;},
  (p: PlacesView) => {p.places[1].x = 0;},
  (p: PlacesView) => {p.places[1].id = 'home';},
  (p: PlacesView) => {p.places[0].x = 0.5;},
  (p: PlacesView) => {p.places[0].x = Infinity;},
  (p: PlacesView) => {p.places[0].x = Number.MAX_SAFE_INTEGER;},
  (p: PlacesView) => {p.places[0].x = -100;},
  (p: PlacesView) => {p.places[0].y = 10;},
  (p: PlacesView) => {p.places = Array.from({length: 25}, (_, i) => ({id: `${i}`, label: `${i}`, x: i, y: 0}));},
  (p: PlacesView) => {p.places = [];}
 ]) {const p = places(); change(p); expect(hexLayout(p)).toBeNull();}
 const p = places(); p.places.forEach(place => {place.x! -= 3; place.y! -= 2;});
 expect(hexLayout(p)?.cells[0]).toEqual({gridColumn: '1 / span 2', gridRow: 1});
});
it('tolerates malformed optional presentation coordinates at the public decoder boundary', async () => {
 const wire = {...structuredClone(fixtures.dynamic), view: {...structuredClone(fixtures.dynamic.view), places: places()}};
 (wire.view.places.places[0] as unknown as {x: unknown}).x = '<script>coordinate</script>';
 const p = decodeSummary(wire).view!.places!;
 expect(p.places[0].x).toBeNull(); expect(hexLayout(p)).toBeNull();
 await act(async () => root.render(<GamePlaces places={p} locale="en" locked={false} submit={vi.fn()}/>));
 expect(host.querySelectorAll('li')).toHaveLength(3);
});
it('inspects public plain text without dispatch and restores focus on Back and Escape', async () => {
 const p = places(), submit = vi.fn(); p.places[0].label = 'Zażółć'.repeat(60); p.places[0].description = '<img src=x onerror=alert(1)> https://example.invalid';
 await act(async () => root.render(<GamePlaces places={p} locale="en" locked={false} submit={submit}/>));
 host.querySelector('details')!.open = true;
 const inspect = buttons('Inspect:')[0]; inspect.focus();
 await act(async () => inspect.click()); expect(document.activeElement).toBe(host.querySelector('h4'));
 expect(host.querySelector('section')?.textContent).toContain(p.places[0].description);
 expect(host.querySelector('img,script,a')).toBeNull(); expect(submit).not.toHaveBeenCalled();
 await act(async () => buttons('Back to places')[0].click()); expect(document.activeElement).toBe(inspect);
 await act(async () => inspect.click());
 await act(async () => host.querySelector('section')!.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true})));
 expect(document.activeElement).toBe(inspect); expect(host.querySelector('section')).toBeNull();
 await act(async () => buttons('Inspect:')[1].click()); expect(host.textContent).toContain('No description available.');
});
it('leaves accepted places visible and inspectable while all moves are pending-locked', async () => {
 const p = places(), submit = vi.fn();
 await act(async () => root.render(<GamePlaces places={p} locale="pl" locked submit={submit}/>));
 expect(buttons('Przejdź do:').every(b => b.disabled)).toBe(true);
 expect(host.textContent).toContain('Poczekaj na zapisaną grę');
 expect(host.querySelector('[aria-current=location]')?.textContent).toContain('Dom');
 await act(async () => buttons('Obejrzyj:')[2].click()); expect(host.querySelector('h4')?.textContent).toBe('Most'); expect(submit).not.toHaveBeenCalled();
});
it('shares public map and encounter rendering for authored and dynamic views, leaving text-only games intact', async () => {
 for (const source of [fixtures.packV1, fixtures.dynamic]) {
  const view = decodeSummary(source).view!; view.places = places();
  if ('characters' in view) view.characters = [{id: 'bee', slotId: 'helper', name: 'Pszczoła', description: 'Cierpliwa pomocniczka szuka drogi.', inCurrentScene: true}];
  await act(async () => root.render(<><SceneBlocks view={view} locale="pl"/><ScenePanels view={view} locale="pl" locked={false} submit={vi.fn()}/></>));
  expect(host.querySelectorAll('.game-hex-map li')).toHaveLength(3);
  if ('characters' in view) expect(host.textContent).toContain('Cierpliwa pomocniczka');
  delete view.places;
  await act(async () => root.render(<><SceneBlocks view={view} locale="pl"/><ScenePanels view={view} locale="pl" locked={false} submit={vi.fn()}/></>));
  expect(host.querySelector('.game-places')).toBeNull(); expect(host.querySelectorAll('.game-block')).toHaveLength(view.blocks.length);
 }
});
