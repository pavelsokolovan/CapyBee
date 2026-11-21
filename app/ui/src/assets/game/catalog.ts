/** Serializable, browser-independent catalog. IDs are append-only within v1. */
export const catalogVersion = 1 as const;
export const expressions = ['neutral', 'attentive', 'pleased'] as const;
export type Expression = typeof expressions[number];
const capyExpressions = [...expressions, 'playful'] as const;
export type CharacterExpression = typeof capyExpressions[number];
export type Orientation = 'left' | 'right';
const provenance = 'Original vector artwork authored for CapyBee, Spec 30.1; no third-party artwork incorporated.';
const caseProvenance = 'Original vector artwork authored for CapyBee, Spec 45.3.';
const character = (marker = false, origin = provenance) => ({
  kind: 'character' as const, variants: marker ? ['portrait', 'figure', 'marker'] as const : ['portrait', 'figure'] as const,
  expressions, dimensions: {portrait: [120, 120], figure: [160, 180], marker: [160, 180]},
  safeArea: 'Body inset 8%; portrait face centered within 80%; marker center anchor.',
  fallback: 'character.abstract-guide', source: 'CharacterArtwork.tsx', provenance: origin,
});
const terrain = () => ({kind: 'terrain' as const, variants: ['default'] as const, dimensions: [120, 140],
  safeArea: 'Central 48% reserved for marker.', fallback: 'terrain.neutral', source: 'EnvironmentArtwork.tsx', provenance});
const backdrop = (origin = provenance) => ({kind: 'backdrop' as const, variants: ['default'] as const, dimensions: [640, 360],
  safeArea: 'Landmarks in central 70% / upper 65%; sparse center and lower half.',
  fallback: 'backdrop.neutral', source: 'EnvironmentArtwork.tsx', provenance: origin});
const motif = () => ({kind: 'motif' as const, variants: ['default'] as const, dimensions: [100, 100],
  safeArea: 'Inset 8%.', fallback: null, source: 'MotifArtwork.tsx', provenance});

export const catalog = {
  'character.capy': {...character(true), expressions: capyExpressions},
  'character.mela-bee': character(),
  'character.borys-beaver': character(),
  'character.luna-moth': character(),
  'character.abstract-guide': character(),
  'character.case-detective': character(false, caseProvenance), 'character.case-client': character(false, caseProvenance),
  'character.case-copper': character(false, caseProvenance), 'character.case-indigo': character(false, caseProvenance), 'character.case-olive': character(false, caseProvenance),
  'terrain.meadow': terrain(), 'terrain.water': terrain(), 'terrain.willow': terrain(),
  'terrain.workshop': terrain(), 'terrain.lantern': terrain(), 'terrain.crossing': terrain(), 'terrain.neutral': terrain(),
  'backdrop.hive-clearing': backdrop(), 'backdrop.lantern-grove': backdrop(), 'backdrop.quiet-meadow': backdrop(),
  'backdrop.reed-bank': backdrop(), 'backdrop.willow-glen': backdrop(), 'backdrop.hive-workshop': backdrop(),
  'backdrop.small-crossing': backdrop(), 'backdrop.neutral': backdrop(),
  'backdrop.note-clearing': backdrop(), 'backdrop.note-meadow': backdrop(),
  'backdrop.case-study': backdrop(caseProvenance),
  'motif.lantern': motif(), 'motif.reed': motif(), 'motif.rest-leaf': motif(),
} as const;
export type AssetId = keyof typeof catalog;
export type AssetKind = typeof catalog[AssetId]['kind'];
export type IdOf<K extends AssetKind> = {[I in AssetId]: typeof catalog[I]['kind'] extends K ? I : never}[AssetId];
export type CharacterId = IdOf<'character'>;
export type TerrainId = IdOf<'terrain'>;
export type BackdropId = IdOf<'backdrop'>;
export type MotifId = IdOf<'motif'>;
export type CharacterVariant = typeof catalog[CharacterId]['variants'][number];

export const fallbackIds = {character: 'character.abstract-guide', terrain: 'terrain.neutral', backdrop: 'backdrop.neutral', motif: null} as const;
/** Validation boundary also accepts malformed/older content without path interpolation. */
export function resolveAsset<K extends AssetKind>(kind: K, candidate: unknown): IdOf<K> | null {
  if (typeof candidate === 'string' && Object.hasOwn(catalog, candidate) && catalog[candidate as AssetId].kind === kind) {
    return candidate as IdOf<K>;
  }
  return fallbackIds[kind] as IdOf<K> | null;
}
export const hexPoints = '60,2 118,36 118,104 60,138 2,104 2,36';
export const palette = {paper: '#FFFDF7', cream: '#FDF6E3', ink: '#2E2413', muted: '#6C5A3A', honey: '#F2B233',
  focus: '#765321', sage: '#A8BEA0', water: '#B5D5D8', lilac: '#C9BED9', earth: '#C79A70'} as const;
