import {expect, it} from 'vitest';
import {gameCompanions} from './gameCompanions';
import {companionView} from './fixtures/companionViews';
import {decodeSummary} from './gameDecoders';
import fixtures from './fixtures/contracts.json';

it('joins opaque public IDs, retains accepted names, and respects current-scene and explicit party order', () => {
  const view = companionView(['engine-c', 'engine-a']); view.characters[1].inCurrentScene = false;
  const before = structuredClone(view), data = gameCompanions(view)!;
  expect(data.discovered.map(c => c.name)).toEqual(['Mela', 'Borys', 'Luna']);
  expect(data.current.map(c => c.name)).toEqual(['Mela', 'Luna']);
  expect(data.party.map(c => c.name)).toEqual(['Luna', 'Mela']);
  expect(data.known.map(c => c.assetId)).toEqual(['character.mela-bee', 'character.borys-beaver', 'character.luna-moth']);
  expect(view).toEqual(before);
});

it('never invents membership from a boolean, unknown references or undiscovered metadata', () => {
  const view = companionView(['engine-a', 'engine-a', 'unknown', 'engine-b']);
  view.presentation!.characters[1].recruited = false;
  view.presentation!.characters[2].recruited = true;
  view.presentation!.characters.push({...view.presentation!.characters[0], id: 'hidden'});
  view.presentation!.party.push('hidden');
  expect(gameCompanions(view)!.party.map(c => c.id)).toEqual(['engine-a']);
  expect(gameCompanions(view)!.known.map(c => c.id)).not.toContain('hidden');
  expect(gameCompanions(view)!.known.find(c => c.id === 'engine-c')!.joined).toBe(false);
});

it('deduplicates discovered/metadata IDs conservatively and uses the neutral art fallback', () => {
  const view = companionView(['engine-a']);
  view.characters.push({...view.characters[0], name: 'Untrusted duplicate'});
  view.presentation!.characters[0].assetId = 'terrain.water' as never;
  view.presentation!.characters.push({...view.presentation!.characters[0], recruited: false});
  const data = gameCompanions(view)!;
  expect(data.discovered).toHaveLength(3); expect(data.known).toHaveLength(3); expect(data.party).toHaveLength(1);
  expect(data.party[0].name).toBe('Mela'); expect(data.party[0].assetId).toBe('character.abstract-guide');
});

it('keeps legacy/absent/unsupported presentation on the text-only path and allows partial public metadata', () => {
  expect(gameCompanions(decodeSummary(fixtures.packV1).view!)).toBeNull();
  const view = companionView(); delete view.presentation;
  expect(gameCompanions(view)).toBeNull();
  view.presentation = {...companionView().presentation!, schemaVersion: 2 as never};
  expect(gameCompanions(view)).toBeNull();
  view.presentation = companionView().presentation!; view.presentation.characters = view.presentation.characters.slice(0, 1);
  expect(gameCompanions(view)!.discovered).toHaveLength(3); expect(gameCompanions(view)!.known).toHaveLength(1);
});
