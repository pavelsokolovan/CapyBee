import {act} from 'react';
import {createRoot, type Root} from 'react-dom/client';
import {afterEach, beforeEach, expect, it, vi} from 'vitest';
import {GameCurrentCharacters, GameCharacterHistory, GameParty} from './GameCharacters';
import {GameRun} from './GameRun';
import type {GameRunSummaryDto, GameViewV2} from '../../game/gameApi';
import type {RunController, RunState} from '../../game/runController';
import {companionView} from '../../game/fixtures/companionViews';

(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
let host: HTMLDivElement, root: Root;
beforeEach(() => {host = document.createElement('div'); document.body.append(host); root = createRoot(host);});
afterEach(async () => {await act(async () => root.unmount()); host.remove(); vi.restoreAllMocks();});

it('shows each current portrait with name, trait and need while keeping all discovered history', async () => {
  const view = companionView(); view.characters[2].inCurrentScene = false;
  await act(async () => root.render(<><GameCurrentCharacters view={view} locale="en"/><GameCharacterHistory view={view} locale="en"/></>));
  expect(host.querySelectorAll('.game-current-characters .game-character-card')).toHaveLength(2);
  expect(host.querySelectorAll('.game-character-history .game-character-card')).toHaveLength(3);
  expect(host.querySelector('.game-current-characters')?.textContent).toContain('Mela');
  expect(host.querySelector('.game-current-characters')?.textContent).toContain('A quiet place.');
  expect(host.querySelector('.game-current-characters')?.textContent).not.toContain('Luna');
  expect(host.querySelector('summary')?.textContent).toBe('Characters');
  for (const svg of host.querySelectorAll('svg')) {expect(svg.getAttribute('aria-hidden')).toBe('true'); expect(svg.getAttribute('focusable')).toBe('false'); expect(svg.querySelector('title')).toBeNull();}
});

it('uses joined need wording only for explicit party members and wraps the compact list in public order', async () => {
  const view = companionView(['engine-c', 'engine-a']);
  await act(async () => root.render(<><GameParty view={view} locale="en" runId="run" revision={2}/><GameCurrentCharacters view={view} locale="en"/></>));
  expect([...host.querySelectorAll('.game-party-members strong')].map(n => n.textContent)).toEqual(['Luna', 'Mela']);
  expect(host.querySelector('.game-current-characters [data-character-id="engine-a"]')?.textContent).toContain('Helped with');
  expect(host.querySelector('.game-current-characters [data-character-id="engine-b"]')?.textContent).toContain('Needs');
  expect(host.querySelector('.game-party-announcement')?.textContent).toBe('');
  expect(host.querySelectorAll('button')).toHaveLength(0);
});

it('omits unspecified traits/needs, retains descriptions, and renders hostile strings as plain text', async () => {
  const view = companionView();
  view.characters[0].name = '<img src=x onerror=alert(1)>';
  view.presentation!.characters[0].trait = null; view.presentation!.characters[0].need = null;
  view.characters[1].description = '<script>not executable</script>';
  view.presentation!.characters[1].need = 'https://example.invalid <a href=x>plain text</a>';
  view.presentation!.characters[0].assetId = 'not-a-character' as never;
  await act(async () => root.render(<><GameCurrentCharacters view={view} locale="pl"/><GameCharacterHistory view={view} locale="pl"/></>));
  expect(host.textContent).toContain('<img'); expect(host.textContent).toContain('<script>');
  expect(host.querySelector('img,script,a')).toBeNull();
  expect(host.querySelector('.game-current-characters [data-character-id="engine-a"] dl')).toBeNull();
  expect(host.querySelector('.game-current-characters [data-character-id="engine-a"]')?.textContent).toContain('A patient bee.');
  expect(host.querySelector('.game-current-characters h4')?.textContent).toBe('Kto jest tutaj');
  expect(host.textContent).not.toContain('not-a-character');
});

it('keeps older and partial public cast readable without inventing portraits or an empty-party promise', async () => {
  const view = companionView(); delete view.presentation;
  await act(async () => root.render(<><GameParty view={view} locale="en" runId="run" revision={0}/><GameCurrentCharacters view={view} locale="en"/><GameCharacterHistory view={view} locale="en"/></>));
  expect(host.querySelector('svg')).toBeNull(); expect(host.querySelector('.game-party')).toBeNull(); expect(host.textContent).toContain('Mela');
  view.presentation = companionView().presentation!; view.presentation.characters = view.presentation.characters.slice(0, 1);
  await act(async () => root.render(<GameCharacterHistory view={view} locale="en"/>));
  expect(host.querySelectorAll('svg')).toHaveLength(1); expect(host.textContent).toContain('Borys'); expect(host.textContent).toContain('Luna');
});

it('keeps initial/resumed hydration silent, announces one accepted join and resets on run replacement', async () => {
  const render = async (view: GameViewV2, revision: number, runId = 'one', ready = true, announce = true) => act(async () => root.render(<GameParty view={view} locale="en" runId={runId} revision={revision} ready={ready} announce={announce}/>));
  await render(companionView(['engine-a']), 4, 'one', false);
  await render(companionView(['engine-a', 'engine-b']), 5);
  expect(host.querySelector('[role=status]')?.textContent).toBe('');
  await render(companionView(['engine-a', 'engine-b']), 5, 'one', true, false);
  await render(companionView(['engine-a', 'engine-b', 'engine-c']), 6);
  const status = host.querySelector('[role=status]'); expect(status?.textContent).toBe('Luna joined the adventure.');
  await render(companionView(['engine-a', 'engine-b', 'engine-c']), 6);
  expect(host.querySelector('[role=status]')).toBe(status); expect(status?.textContent).toBe('Luna joined the adventure.');
  await render(companionView(['engine-a']), 5); expect(status?.textContent).toBe('Luna joined the adventure.');
  await render(companionView(['engine-a']), 0, 'two'); expect(status?.textContent).toBe('');
  await render(companionView(['engine-a', 'engine-b']), 1, 'two', true, false);
  expect(status?.textContent).toBe('');
});

it('preserves completed public members, clears abandoned metadata and never moves focus', async () => {
  const focus = document.createElement('button'); document.body.append(focus); focus.focus();
  try {
    const view = companionView(['engine-a']); view.ended = true; view.endReason = 'COMPLETED';
    await act(async () => root.render(<GameParty view={view} locale="pl" runId="run" revision={3}/>));
    expect(host.querySelector('.game-party')?.textContent).toContain('Mela'); expect(document.activeElement).toBe(focus);
    view.endReason = 'ABANDONED'; view.presentation = null; view.characters = [];
    await act(async () => root.render(<GameParty view={view} locale="pl" runId="run" revision={4}/>));
    expect(host.querySelector('.game-party')).toBeNull(); expect(document.activeElement).toBe(focus);
  } finally {focus.remove();}
});

it('uses shell-language labels without changing story-language text', async () => {
  await act(async () => root.render(<section lang="pl"><GameCurrentCharacters view={companionView()} locale="en"/></section>));
  expect(host.querySelector('dt')?.getAttribute('lang')).toBe('en');
  expect(host.querySelector('dd')?.closest('[lang]')?.getAttribute('lang')).toBe('pl');
  await act(async () => root.render(<GameParty view={companionView()} locale="pl" runId="run" revision={0}/>));
  expect(host.querySelector('.game-party-empty')?.textContent).toContain('samodzielnie');
});

it('keeps accepted party state through pending, offline and rejected choices, then updates from one accepted view', async () => {
  const run: GameRunSummaryDto = {runId: 'fixture-run', revision: 0, source: {kind: 'PACK', packId: 'generic-pack', packVersion: '1.0.0'}, packId: 'generic-pack', packVersion: '1.0.0',
    status: 'ACTIVE', locale: 'en', updatedAt: '2026-11-16T00:00:00Z', viewSchema: 2, view: companionView()};
  let state: RunState = {run, pending: false, message: 'saved'};
  const listeners = new Set<() => void>();
  const update = (next: RunState) => {state = next; listeners.forEach(fn => fn());};
  const controller = {getSnapshot: () => state, subscribe: (fn: () => void) => {listeners.add(fn); return () => {listeners.delete(fn);};},
    initialize: vi.fn(async () => {}), detach: vi.fn(), submit: vi.fn(async () => update({...state, pending: true, message: 'checking'}))};
  await act(async () => root.render(<GameRun run={run} owner="fixture-owner" locale="en" onAnother={vi.fn()} controller={controller as unknown as RunController}/>));
  const choose = () => host.querySelector<HTMLButtonElement>('fieldset button')!.click();
  await act(async () => choose()); expect(controller.submit).toHaveBeenCalledExactlyOnceWith({type: 'CHOOSE', optionId: 'public-choice'});
  expect(host.querySelectorAll('.game-party-members li')).toHaveLength(0);
  await act(async () => update({...state, message: 'waiting'})); expect(host.querySelectorAll('.game-party-members li')).toHaveLength(0);
  await act(async () => update({...state, pending: false, message: 'rejected'})); expect(host.querySelectorAll('.game-party-members li')).toHaveLength(0);
  await act(async () => choose());
  await act(async () => update({run: {...run, revision: 1, view: companionView(['engine-a'])}, pending: false, message: 'saved'}));
  expect(controller.submit).toHaveBeenCalledTimes(2); expect(host.querySelector('.game-party-members')?.textContent).toBe('Mela');
  expect(host.querySelector('.game-party-announcement')?.textContent).toBe('Mela joined the adventure.');
});
