import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {describe, expect, it} from 'vitest';
import {catalog, catalogVersion} from '../assets/game/catalog';
import {decodeView, decodeSummary} from './gameDecoders';

const art = () => ({schemaVersion: 1, catalogVersion: 1, playerAssetId: 'character.capy', backdropAssetId: 'backdrop.quiet-meadow',
  characters: [{id: 'actor:mela-bee', assetId: 'character.mela-bee', trait: 'Cierpliwa', need: 'Potrzebuje ciszy.', expression: 'attentive', recruited: true}],
  places: [{placeId: 'meadow', terrainAssetId: 'terrain.meadow'}], party: ['actor:mela-bee']});
function wire(presentation: unknown = art()) {
  return {blocks: [{kind: 'SPEECH', speaker: 'actor:mela-bee', text: 'A quiet moment.', refs: []}], options: [{id: 'rest', label: 'Rest', enabled: true}],
    status: [], objectives: [], ended: false, canAdvance: false, characters: [{id: 'actor:mela-bee', slotId: 'mela-bee', name: 'Mela', description: 'A bee.', inCurrentScene: true}],
    places: {currentPlaceId: 'meadow', layoutKind: 'HEX', places: [{id: 'meadow', label: 'Meadow', x: 0, y: 0}], links: [], availableMoves: [], visitedPlaceIds: ['meadow']}, presentation};
}
describe('public presentation decoding', () => {
  it('retains accepted public traits, needs and membership with local typed asset IDs', () => {
    const view = decodeView(wire(), 2);
    expect(view.presentation).toEqual(art()); expect(view.options[0].id).toBe('rest');
    expect(view.places?.places[0].x).toBe(0);
  });
  it('supports old views and ignores presentation on schema 1', () => {
    const old = wire(); delete (old as {presentation?: unknown}).presentation;
    expect(decodeView(old, 2).presentation).toBeUndefined();
    expect(decodeView(wire(null), 2).presentation).toBeNull();
    const v1 = {...wire(), blocks: [{kind: 'NARRATION', text: 'Still playable', tags: []}]};
    expect(decodeView(v1, 1).presentation).toBeUndefined();
  });
  it('whitelists fields and filters undiscovered metadata/places and inconsistent party IDs', () => {
    const p = {...art(), secretFlag: 'never retain', characters: [art().characters[0], {...art().characters[0], id: 'hidden', assetId: 'character.luna-moth'}],
      places: [...art().places, {placeId: 'hidden-place', terrainAssetId: 'terrain.water'}], party: ['actor:mela-bee', 'hidden', 'not-a-character']};
    const decoded = decodeView(wire(p), 2).presentation!;
    expect(decoded.characters).toHaveLength(1); expect(decoded.places).toHaveLength(1); expect(decoded.party).toEqual(['actor:mela-bee']);
    expect(JSON.stringify(decoded)).not.toMatch(/hidden|never retain|secretFlag/);
    const noRecruit = art(); noRecruit.characters[0].recruited = false;
    expect(decodeView(wire(noRecruit), 2).presentation?.party).toEqual([]);
  });
  it('uses local fallbacks for hostile, unknown, missing and wrong-family artwork without changing public text', () => {
    for (const bad of [undefined, null, 'not-in-catalog', '../image.svg', '<svg onload=alert(1)>', 'https://example.invalid/a.svg', 'backdrop.reed-bank', {}]) {
      const p = {...art(), playerAssetId: bad, characters: [{...art().characters[0], assetId: bad, expression: 'bad-expression'}],
        places: [{placeId: 'meadow', terrainAssetId: bad}]};
      const decoded = decodeView(wire(p), 2).presentation!;
      expect(decoded.characters[0]).toMatchObject({assetId: 'character.abstract-guide', expression: 'neutral', trait: 'Cierpliwa'});
      expect(decoded.places[0].terrainAssetId).toBe('terrain.neutral'); expect(decoded.party).toEqual(['actor:mela-bee']);
    }
    const wrong = {...art(), backdropAssetId: 'character.capy'};
    expect(decodeView(wire(wrong), 2).presentation?.backdropAssetId).toBe('backdrop.neutral');
  });
  it('preserves content with neutral art for a future catalog version', () => {
    const p = {...art(), catalogVersion: 2};
    const decoded = decodeView(wire(p), 2).presentation!;
    expect(decoded.catalogVersion).toBe(2); expect(decoded.characters[0].assetId).toBe('character.abstract-guide');
    expect(decoded.backdropAssetId).toBe('backdrop.neutral'); expect(decoded.party).toEqual(p.party);
    expect(decoded.characters[0].need).toBe('Potrzebuje ciszy.');
  });
  it('discards malformed/unsupported/overlarge metadata independently of story and legal actions', () => {
    for (const p of [[], true, 'raw SVG', {schemaVersion: 2}, {...art(), catalogVersion: -1}, {...art(), catalogVersion: 1.5},
      {...art(), characters: new Array(33).fill(art().characters[0])}, {...art(), places: new Array(129).fill(art().places[0])},
      {...art(), party: new Array(33).fill('actor:mela-bee')}, {...art(), characters: [art().characters[0], art().characters[0]]},
      {...art(), places: [art().places[0], art().places[0]]}, {...art(), party: ['actor:mela-bee', 'actor:mela-bee']},
      {...art(), characters: [{...art().characters[0], id: '<img>'}]}, {...art(), characters: [{...art().characters[0], recruited: 'true'}]},
      {...art(), characters: [{...art().characters[0], trait: 'x'.repeat(513)}]}, {...art(), characters: [{...art().characters[0], need: {text: 'unsafe'}}]},
      {...art(), places: [null]}, {...art(), characters: null}, {...art(), party: null}]) {
      const view = decodeView(wire(p), 2); expect(view.presentation).toBeNull(); expect(view.options).toHaveLength(1); expect(view.blocks).toHaveLength(1);
    }
    expect(() => decodeView({...wire(), options: 'invalid'}, 2)).toThrow('GAME_CONTRACT_UNSUPPORTED');
  });
  it('keeps text as plain data and strips extra payloads at every presentation level', () => {
    const p = art(); p.characters[0].need = '<img src=x onerror=alert(1)> https://example.invalid';
    const decoded = decodeView(wire({...p, characters: [{...p.characters[0], svg: '<svg/>'}], places: [{...p.places[0], url: 'untrusted'}]}), 2).presentation!;
    expect(decoded.characters[0].need).toBe(p.characters[0].need);
    expect(JSON.stringify(decoded)).not.toContain('"svg"'); expect(JSON.stringify(decoded)).not.toContain('"url"');
  });
});

it('keeps shipped presentation assets aligned with the canonical SVG catalog', () => {
  const paths = [
    '../engine/reference-game-pack-versions/capybee-new-hive/1.2.0/assets/catalog.json',
    '../server/src/main/resources/game-packs/capybee-anonymous-note/assets/catalog.json',
  ];
  const shippedAssets = new Map<string, string>();
  for (const path of paths) {
    const shipped = JSON.parse(readFileSync(resolve(process.cwd(), path), 'utf8'));
    expect(shipped.catalogVersion).toBe(catalogVersion);
    for (const {id, kind} of shipped.assets) {
      expect(catalog[id as keyof typeof catalog]?.kind.toUpperCase()).toBe(kind);
      shippedAssets.set(id, kind);
    }
  }
  expect(shippedAssets).toEqual(new Map(Object.entries(catalog)
    .filter(([id,entry]) => entry.kind !== 'motif' && !/^(character|backdrop)\.case-/.test(id))
    .map(([id,entry]) => [id,entry.kind.toUpperCase()])));
});

it('decodes the detective study and its local portraits in the same public contract', () => {
  for (const [id, entry] of Object.entries(catalog)) {
    if (entry.kind !== 'character' || !id.startsWith('character.case-')) continue;
    const presentation = {...art(), backdropAssetId: 'backdrop.case-study',
      characters: [{...art().characters[0], assetId: id, recruited: false}], party: []};
    expect(decodeView(wire(presentation), 2).presentation).toEqual(presentation);
  }
});

it('reads the same public JSON golden that the Java session wire round-trips', () => {
  const golden = JSON.parse(readFileSync(resolve(process.cwd(), '../engine/session/src/test/resources/wire/run-presentation.json'), 'utf8'));
  const summary = decodeSummary(golden);
  expect(summary.view?.presentation?.characters[0]).toMatchObject({id: 'actor:traveler', trait: 'Patient', need: 'A quiet place', assetId: 'character.abstract-guide'});
  expect(summary.view?.presentation?.party).toEqual(['actor:traveler']);
});
