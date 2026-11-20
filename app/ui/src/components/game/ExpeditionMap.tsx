import {useId, useLayoutEffect, useRef, useState, type ReactNode, type RefObject} from 'react';
import type {GameCommand, GamePresentation, PlacesView} from '../../game/gameApi';
import {GameTileArt} from './art/GameArt';
import {boardCopy} from './boardCopy';
import {hexLayout} from './GamePlaces';
import {runCopy} from './runCopy';
import './expeditionMap.css';

type Props = {
  places: PlacesView;
  presentation?: GamePresentation | null;
  locale: 'en' | 'pl';
  dialog: RefObject<HTMLDialogElement | null>;
  onClose: () => void;
  selectedId: string | null;
  onSelect: (id: string) => void;
  locked: boolean;
  pending: boolean;
  textMode: boolean;
  completedPlaces: readonly string[];
  animatedFills: readonly string[];
  status: ReactNode;
  submit: (command: GameCommand) => void;
};

export function ExpeditionMap({places, presentation, locale, dialog, onClose, selectedId, onSelect,
  locked, pending, textMode, status, submit, completedPlaces, animatedFills}: Props) {
  const titleId = useId(), copy = runCopy[locale], board = boardCopy[locale];
  const scroll = useRef<HTMLDivElement>(null);
  const layout = hexLayout(places), columns = layout?.columns;
  const [graphicalSpace, setGraphicalSpace] = useState(false);
  useLayoutEffect(() => {
    const node = scroll.current;
    if (!node || !columns) return;
    const colors = window.matchMedia('(forced-colors: active)');
    const measure = () => {
      const style = getComputedStyle(node);
      const width = node.clientWidth - parseFloat(style.paddingLeft || '0') - parseFloat(style.paddingRight || '0');
      const tileWidth = (width - (columns - 1) * 2) * 2 / columns + 2;
      setGraphicalSpace(!colors.matches && tileWidth >= 92);
    };
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(node);
    colors.addEventListener('change', measure);
    return () => {observer?.disconnect();colors.removeEventListener('change', measure);};
  }, [columns]);
  const graphical = graphicalSpace && !!presentation?.places.length && !!layout && !textMode;
  const selected = places.places.find(place => place.id === selectedId)
    ?? places.places.find(place => place.id === places.currentPlaceId) ?? places.places[0];
  if (!selected) return null;
  const visited = (id: string) => places.visitedPlaceIds.includes(id);
  const reachable = (id: string) => id !== places.currentPlaceId && places.availableMoves.includes(id);
  const current = selected.id === places.currentPlaceId, legal = reachable(selected.id);
  const prerequisite = !current && !legal ? selected.moveHint : null;
  const detailState = current ? board.youAreHere : prerequisite
    || (visited(selected.id) ? copy.visited : legal ? copy.moveAvailable : board.noRoute);

  return <dialog className="game-map-sheet" ref={dialog} aria-labelledby={titleId}
    onKeyDown={event=>event.stopPropagation()}
    onCancel={event=>{event.preventDefault();onClose();}}
    onClick={event=>{if (event.target === event.currentTarget) onClose();}}>
    <div className="game-map-sheet-content game-art">
      <div className="game-map-heading" lang={locale}>
        <h3 id={titleId} tabIndex={-1}>{board.title}</h3>
        <button type="button" onClick={onClose}>{board.close}</button>
      </div>
      {status}
      <div className="game-map-scroll" ref={scroll}>
        {places.movementHint ? <p className="game-map-hint">{places.movementHint}</p> : null}
        <ul className={graphical ? 'game-map-grid' : 'game-map-list'} aria-label={copy.places}
          style={graphical ? {gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`} : undefined}>
          {places.places.map((place, index) => {
            const here = place.id === places.currentPlaceId, canGo = reachable(place.id);
            const wasVisited = !here && visited(place.id), picked = place.id === selected.id;
            const completed = completedPlaces.includes(place.id);
            const terrain = presentation?.places.find(asset => asset.placeId === place.id)?.terrainAssetId;
            const state = here ? 'current' : canGo ? 'available' : 'unavailable';
            return <li key={`${place.id}-${index}`} style={graphical ? layout.cells[index] : undefined}>
              <button type="button" className="game-map-tile" data-place-id={place.id} data-travel-state={state}
                data-new-completion={completed&&animatedFills.includes(place.id)?true:undefined}
                aria-current={here ? 'location' : undefined} aria-pressed={picked}
                disabled={pending} onClick={()=>onSelect(place.id)}>
                {graphical ? <GameTileArt assetId={terrain} playerAssetId={presentation.playerAssetId}
                  completed={completed} states={[state,...(wasVisited?['visited' as const]:[]),...(picked?['selected' as const]:[])]}/> : null}
                <strong>{place.label}</strong>
                <small className={graphical && !here ? 'game-visually-hidden' : undefined} lang={locale}>
                  {completed ? `${board.completed}. ` : null}{here ? board.youAreHere : <>{wasVisited ? `${copy.visited}. ` : null}{canGo ? copy.moveAvailable : board.noRoute}</>}
                </small>
              </button>
            </li>;
          })}
        </ul>
      </div>
      <div className="game-map-card" aria-live="polite">
        <h4>{selected.label}</h4>
        <p className="game-map-state" lang={prerequisite ? undefined : locale}>{detailState}</p>
        {completedPlaces.includes(selected.id)?<p className="game-map-completed" lang={locale}>{board.completed}</p>:null}
        <p lang={selected.description ? undefined : locale}>{selected.description ?? copy.noDescription}</p>
        {legal ? <>
          {locked ? <p lang={locale}>{copy.mapLocked}</p> : null}
          <button type="button" disabled={locked} lang={locale} aria-label={`${board.enter}: ${selected.label}`}
            onClick={()=>{if (!locked) submit({type:'MOVE',placeId:selected.id});}}>{board.enter}</button>
        </> : null}
      </div>
    </div>
  </dialog>;
}
