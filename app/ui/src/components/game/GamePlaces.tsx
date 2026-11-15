import {useId, useLayoutEffect, useRef, useState} from 'react';
import type {PlacesView, GameCommand} from '../../game/gameApi';
import {runCopy} from './runCopy';

// Axial HEX coordinates are presentation only. Bound the board to avoid sparse,
// enormous grids; links and geometric neighbours never authorize commands.
export function hexLayout(view: PlacesView) {
 if (view.layoutKind !== 'HEX' || !view.places.length || view.places.length > 24) return null;
 const positions = view.places.map(p => ({x: p.x, y: p.y}));
 if (positions.some(p => !Number.isSafeInteger(p.x) || !Number.isSafeInteger(p.y)
   || Math.abs(p.x!) > 10000 || Math.abs(p.y!) > 10000)) return null;
 const cells = positions.map(p => ({column: 2 * p.x! + p.y!, row: p.y!}));
 if (new Set(cells.map(p => `${p.column},${p.row}`)).size !== cells.length
   || new Set(view.places.map(p => p.id)).size !== cells.length) return null;
 const left = Math.min(...cells.map(p => p.column)), top = Math.min(...cells.map(p => p.row));
 const columns = Math.max(...cells.map(p => p.column)) - left + 2;
 const rows = Math.max(...cells.map(p => p.row)) - top + 1;
 if (columns > 8 || rows > 6) return null;
 return {columns, cells: cells.map(p => ({gridColumn: `${p.column - left + 1} / span 2`, gridRow: p.row - top + 1}))};
}

export function GamePlaces({places, locale, locked, submit}: {
 places: PlacesView; locale: 'en' | 'pl'; locked: boolean; submit: (command: GameCommand) => void;
}) {
 const copy = runCopy[locale], id = useId(), layout = hexLayout(places);
 const [list, setList] = useState(false), [inspection, setInspection] = useState<string | null>(null);
 const opener = useRef<HTMLButtonElement | null>(null), heading = useRef<HTMLHeadingElement>(null);
 const inspected = places.places.find(place => place.id === inspection);
 useLayoutEffect(() => { if (inspection !== null) heading.current?.focus(); }, [inspection]);
 const close = () => {setInspection(null); opener.current?.focus();};
 return <details className="game-places"><summary>{copy.places}</summary>
  <p>{copy.mapHint}</p>
  {layout ? <button type="button" aria-pressed={list} onClick={() => setList(!list)}>{copy.placeList}</button> : null}
  <ul aria-label={copy.places} className={layout && !list ? 'game-place-list game-hex-map' : 'game-place-list'}
    style={layout && !list ? {gridTemplateColumns: `repeat(${layout.columns}, minmax(0, 1fr))`} : undefined}>
   {places.places.map((place, index) => {
    const current = place.id === places.currentPlaceId, visited = places.visitedPlaceIds.includes(place.id);
    const legal = places.availableMoves.includes(place.id), hint = `${id}-${index}`;
    return <li key={`${place.id}-${index}`} className={`game-place${current ? ' game-place-current' : ''}`}
      style={layout && !list ? layout.cells[index] : undefined} aria-current={current ? 'location' : undefined}>
     <strong>{place.label}</strong>
     {current ? <p>◉ {copy.current}</p> : null}{visited ? <p>✓ {copy.visited}</p> : null}
     <button type="button" onClick={event => {opener.current = event.currentTarget; setInspection(place.id);}}>{copy.inspect}: {place.label}</button>
     <button type="button" disabled={locked || !legal} aria-describedby={hint}
       onClick={() => {if (!locked && places.availableMoves.includes(place.id)) submit({type: 'MOVE', placeId: place.id});}}>{copy.move}: {place.label}</button>
     <p id={hint}>{locked ? copy.mapLocked : legal ? copy.moveAvailable : copy.moveUnavailable}</p>
    </li>;
   })}
  </ul>
  {inspected ? <section className="game-place-inspection" aria-labelledby={`${id}-inspection`}
    onKeyDown={event => {if (event.key === 'Escape') {event.stopPropagation(); close();}}}>
   <h4 id={`${id}-inspection`} tabIndex={-1} ref={heading}>{inspected.label}</h4>
   <p>{inspected.description ?? copy.noDescription}</p>
   <button type="button" onClick={close}>{copy.backToPlaces}</button>
  </section> : null}
 </details>;
}
