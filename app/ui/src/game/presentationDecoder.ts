import {catalogVersion, expressions, resolveAsset} from '../assets/game/catalog';
import type {GamePresentation} from './gameApi';

type Data = Record<string, unknown>;
const invalid = () => {throw new Error('PRESENTATION_INVALID');};
function object(value: unknown): Data {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Data : invalid();
}
function list(value: unknown, max: number): unknown[] {return Array.isArray(value) && value.length <= max ? value : invalid();}
function id(value: unknown): string {
  return typeof value === 'string' && value.length <= 128 && /^[A-Za-z0-9][A-Za-z0-9_.:-]*$/.test(value) ? value : invalid();
}
function optionalText(value: unknown): string | null {
  if (value == null) return null;
  return typeof value === 'string' && Array.from(value).length <= 512 ? value : invalid();
}
function unique(values: string[]) {if (new Set(values).size !== values.length) invalid();}

/** Failure affects only illustration metadata. Story/actions retain their independent validation. */
export function decodePresentation(value: unknown, publicCharacters: ReadonlySet<string>, publicPlaces: ReadonlySet<string>): GamePresentation | null {
  if (value == null) return null;
  try {
    const p = object(value);
    if (p.schemaVersion !== 1 || !Number.isSafeInteger(p.catalogVersion) || (p.catalogVersion as number) < 1 || (p.catalogVersion as number) > 1_000_000) return null;
    const knownCatalog = p.catalogVersion === catalogVersion;
    const characters = list(p.characters, 32).map(item => {
      const c = object(item), characterId = id(c.id);
      if (typeof c.recruited !== 'boolean') invalid();
      return {id: characterId, assetId: resolveAsset('character', knownCatalog ? c.assetId : undefined)!,
        trait: optionalText(c.trait), need: optionalText(c.need),
        expression: expressions.find(e => e === c.expression) ?? 'neutral', recruited: c.recruited as boolean};
    });
    unique(characters.map(c => c.id));
    const discovered = characters.filter(c => publicCharacters.has(c.id));
    const places = list(p.places, 128).map(item => {
      const place = object(item);
      return {placeId: id(place.placeId), terrainAssetId: resolveAsset('terrain', knownCatalog ? place.terrainAssetId : undefined)!};
    });
    unique(places.map(p => p.placeId));
    const party = list(p.party, 32).map(id); unique(party);
    const joined = new Set(discovered.filter(c => c.recruited).map(c => c.id));
    return {schemaVersion: 1, catalogVersion: p.catalogVersion as number,
      playerAssetId: p.playerAssetId == null ? null : resolveAsset('character', knownCatalog ? p.playerAssetId : undefined),
      backdropAssetId: p.backdropAssetId == null ? null : resolveAsset('backdrop', knownCatalog ? p.backdropAssetId : undefined),
      characters: discovered, places: places.filter(p => publicPlaces.has(p.placeId)), party: party.filter(id => joined.has(id))};
  } catch {return null;}
}
