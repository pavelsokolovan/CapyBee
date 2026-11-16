import {useId, type ReactNode} from 'react';
import {catalog, expressions, hexPoints, resolveAsset, type BackdropId, type CharacterId,
  type CharacterVariant, type Expression, type MotifId, type Orientation, type TerrainId} from '../../../assets/game/catalog';
import {CharacterArtwork} from '../../../assets/game/CharacterArtwork';
import {BackdropArtwork, TerrainArtwork} from '../../../assets/game/EnvironmentArtwork';
import {MotifArtwork} from '../../../assets/game/MotifArtwork';
import './gameArt.css';

/** Unique meaning requires a localized name; decorative artwork never enters the accessibility tree. */
type ArtAccessibility = {decorative: true; label?: never} | {decorative?: false; label: string};
function ArtFrame({viewBox, children, ...a11y}: ArtAccessibility & {viewBox: string; children: ReactNode}) {
  const titleId = useId();
  return <svg viewBox={viewBox} preserveAspectRatio="xMidYMid meet" focusable="false"
    aria-hidden={a11y.decorative ? true : undefined} role={a11y.decorative ? undefined : 'img'}
    aria-labelledby={a11y.decorative ? undefined : titleId}>
    {!a11y.decorative && <title id={titleId}>{a11y.label}</title>}{children}
  </svg>;
}

export function GameCharacter({assetId, variant = 'figure', expression = 'neutral', orientation = 'right', ...a11y}:
  ArtAccessibility & {assetId?: CharacterId | null; variant?: CharacterVariant; expression?: Expression; orientation?: Orientation}) {
  const id = resolveAsset('character', assetId)!;
  const supported = catalog[id].variants.some(v => v === variant);
  const safeVariant = supported ? variant : 'figure';
  const safeExpression = supported && expressions.some(e => e === expression) && safeVariant !== 'marker' ? expression : 'neutral';
  return <ArtFrame viewBox={safeVariant === 'portrait' ? '20 18 120 120' : '0 0 160 180'} {...a11y}>
    <g transform={orientation === 'left' ? 'translate(160 0) scale(-1 1)' : undefined}>
      <CharacterArtwork id={id} variant={safeVariant} expression={safeExpression}/>
    </g>
  </ArtFrame>;
}
export function GameTerrain({assetId, ...a11y}: ArtAccessibility & {assetId?: TerrainId | null}) {
  return <ArtFrame viewBox="0 0 120 140" {...a11y}><TerrainArtwork id={resolveAsset('terrain', assetId)!}/></ArtFrame>;
}
export function GameBackdrop({assetId, ...a11y}: ArtAccessibility & {assetId?: BackdropId | null}) {
  return <ArtFrame viewBox="0 0 640 360" {...a11y}><BackdropArtwork id={resolveAsset('backdrop', assetId)!}/></ArtFrame>;
}
export function GameMotif({assetId, ...a11y}: ArtAccessibility & {assetId?: MotifId | null}) {
  const id = resolveAsset('motif', assetId);
  return id ? <ArtFrame viewBox="0 0 100 100" {...a11y}><MotifArtwork id={id}/></ArtFrame> : null;
}

export type TileState = 'current' | 'visited' | 'available' | 'unavailable' | 'selected' | 'pending';
/** Presentation only; the host supplies semantic state text and owns all interaction. */
export function GameTileArt({assetId, states = [], playerAssetId = "character.capy", showPlayer = true}: {assetId?: TerrainId; states?: readonly TileState[]; playerAssetId?: CharacterId | null; showPlayer?: boolean}) {
  const current = states.includes('current');
  return <span className="game-tile-art" data-states={states.join(' ')} aria-hidden="true">
    <GameTerrain assetId={assetId} decorative/>
    <svg className="game-tile-outline" viewBox="0 0 120 140" focusable="false">
      <polygon points={hexPoints} className="game-tile-boundary"/>
      {current && <polygon points={hexPoints} transform="translate(6 7) scale(.9)" className="game-tile-inset"/>}
      {states.includes('selected') && <polygon points={hexPoints} transform="translate(14 16) scale(.77)" className="game-tile-inset"/>}
    </svg>
    {current && showPlayer && <span className="game-tile-marker"><GameCharacter assetId={playerAssetId} variant="marker" decorative/></span>}
    {states.includes('visited') && <span className="game-tile-visited">✓</span>}
  </span>;
}
