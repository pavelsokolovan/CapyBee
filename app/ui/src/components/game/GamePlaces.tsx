import {useId, useLayoutEffect, useRef, useState} from 'react';
import type {PlacesView, GameCommand} from '../../game/gameApi';
import type {GameViewV2} from '../../game/gameApi';
import {GameCharacter, GameTileArt} from './art/GameArt';
import {boardCopy} from './boardCopy';
import './gameBoard.css';
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

export function GamePlaces({places, locale, locked, submit, presentation, runId = '', revision = 0, pending = false}: {
 places: PlacesView; locale: 'en' | 'pl'; locked: boolean; submit: (command: GameCommand) => void;
 presentation?: GameViewV2['presentation']; runId?: string; revision?: number; pending?: boolean;
}) {
 const copy = runCopy[locale], board = boardCopy[locale], id = useId(), layout = hexLayout(places);
 const illustrated = !!presentation && !!layout;
 const [list, setList] = useState(false), [narrow, setNarrow] = useState(false), [inspection, setInspection] = useState<string | null>(null);
 const [marker, setMarker] = useState<{left:number;top:number;animate:boolean}|null>(null);
 const [announcement, setAnnouncement] = useState('');
 const container = useRef<HTMLElement>(null), map = useRef<HTMLUListElement>(null);
 const opener = useRef<HTMLButtonElement | null>(null), heading = useRef<HTMLHeadingElement>(null), boardHeading = useRef<HTMLHeadingElement>(null);
 const previous = useRef<{runId:string;revision:number;place:string;mode:string;signature:string}|null>(null);
 const focusedPlace = useRef<string|null>(null), switched = useRef(false);
 const graphical = !!layout && !list && !(illustrated && narrow);
 const mode = graphical ? 'map' : 'list';
 const inspectLabel=illustrated ? board.inspect : copy.inspect;
 const signature = JSON.stringify(places.places.map(p=>[p.id,p.x,p.y]));
 const inspected = places.places.find(place => place.id === inspection);
 useLayoutEffect(() => { if (inspection !== null) heading.current?.focus(); }, [inspection]);
 useLayoutEffect(() => {
  if (!illustrated || !container.current || !layout) return;
  const element = container.current;
  const resize = () => {
   const width=element.clientWidth || element.getBoundingClientRect().width;
   const tileWidth=(width-(layout.columns-1)*2)*2/layout.columns+2;
   setNarrow(tileWidth < 92);
  };
  resize();
  if (typeof ResizeObserver === 'undefined') return;
  const observer = new ResizeObserver(resize);observer.observe(element);return()=>observer.disconnect();
 }, [illustrated, signature, layout?.columns]);
 useLayoutEffect(() => {
  const node = map.current, before = previous.current;
  const current = places.places.find(p=>p.id===places.currentPlaceId);
  const old = places.places.find(p=>p.id===before?.place);
  const adjacent = !!current && !!old && Math.max(Math.abs(current.x!-old.x!),Math.abs(current.y!-old.y!),Math.abs(current.x!+current.y!-old.x!-old.y!))===1
   && places.links.some(l=>l.from===old.id&&l.to===current.id||l.bidirectional&&l.to===old.id&&l.from===current.id);
  const animate = !!before && before.runId===runId && revision>before.revision && before.mode===mode && before.signature===signature && adjacent;
  if (before && before.runId===runId && revision>before.revision && before.place!==places.currentPlaceId && !locked)
   setAnnouncement(`${board.arrived}: ${current?.label ?? ''}`);
  else if (!before || before.runId!==runId) setAnnouncement('');
  previous.current={runId,revision,place:places.currentPlaceId,mode,signature};
  const measure = (motion:boolean) => {
   const art = [...(node?.querySelectorAll<HTMLElement>('[data-place-id]')??[])].find(n=>n.dataset.placeId===places.currentPlaceId)?.querySelector('.game-board-art');
   if (!graphical || !illustrated || !art || !node) {setMarker(null);return;}
   const cell=art.getBoundingClientRect(), bounds=node.getBoundingClientRect();
   // Convert viewport pixels back to local CSS units (also works with browser/CSS zoom).
   const scale=node.offsetWidth ? bounds.width/node.offsetWidth : 1;
   setMarker({left:(cell.left-bounds.left+cell.width/2)/scale,top:(cell.top-bounds.top+cell.height/2)/scale,animate:motion});
  };
  measure(animate);
  if (switched.current) {
   const target = [...(node?.querySelectorAll<HTMLElement>('[data-place-id]')??[])].find(n=>n.dataset.placeId===focusedPlace.current);
   target?.querySelector<HTMLButtonElement>('button')?.focus();switched.current=false;
  }
  if (!node || typeof ResizeObserver==='undefined') return;
  let size=`${node.offsetWidth},${node.offsetHeight}`;
  const observer=new ResizeObserver(()=>{const next=`${node.offsetWidth},${node.offsetHeight}`;if(next!==size){size=next;measure(false);}});observer.observe(node);return()=>observer.disconnect();
 }, [places.currentPlaceId, revision, runId, mode, signature, illustrated, board.arrived, locked]);
 const close = () => {setInspection(null); if(opener.current?.isConnected)opener.current.focus();else boardHeading.current?.focus();};
 const content = <>
  <p lang={locale}>{copy.mapHint}</p>
  {layout ? <button type="button" lang={locale} aria-pressed={list} onClick={() => {switched.current=true;setList(!list);}}>{copy.placeList}</button> : null}
  {places.movementHint ? <p>{places.movementHint}</p> : null}
  {illustrated && places.places.some(p=>p.moveBehavior==='ENTER_SCENE') ? <p lang={locale}>{board.entryHint}</p> : null}
  <ul ref={map} aria-label={copy.places} className={`game-place-list${graphical ? ' game-hex-map' : ''}${illustrated ? ' game-board-cells' : ''}`}
    onFocusCapture={event=>{const cell=(event.target as HTMLElement).closest<HTMLElement>('[data-place-id]');if(cell)focusedPlace.current=cell.dataset.placeId??null;}}
    style={graphical ? {gridTemplateColumns: `repeat(${layout!.columns}, minmax(0, 1fr))`} : undefined}>
   {places.places.map((place, index) => {
    const current = place.id === places.currentPlaceId, visited = places.visitedPlaceIds.includes(place.id);
    const legal = places.availableMoves.includes(place.id), hint = `${id}-${index}`;
    const entry = place.moveBehavior === 'ENTER_SCENE';
    const terrain = presentation?.places.find(p=>p.placeId===place.id)?.terrainAssetId;
    return <li key={`${place.id}-${index}`} data-place-id={place.id} className={`game-place${current ? ' game-place-current' : ''}`}
      style={graphical ? layout!.cells[index] : undefined} aria-current={current ? 'location' : undefined}>
     {illustrated && graphical ? <span className="game-board-art"><GameTileArt assetId={terrain} showPlayer={false}
       states={[current?'current':legal?'available':'unavailable',...(visited?['visited' as const]:[]),...(inspection===place.id?['selected' as const]:[]),...(current&&pending?['pending' as const]:[])]}/></span> : null}
     <strong>{place.label}</strong>
     <span className="game-board-place-state">{current ? <p lang={locale}>◉ {copy.current}</p> : null}{visited ? <p lang={locale}>✓ {copy.visited}</p> : null}</span>
     <span className="game-board-controls"><button type="button" lang={locale} aria-label={`${inspectLabel}: ${place.label}`} onClick={event => {opener.current = event.currentTarget; setInspection(place.id);}}>{illustrated && graphical ? inspectLabel : `${inspectLabel}: ${place.label}`}</button>
     <button type="button" lang={locale} disabled={locked || !legal} aria-label={`${entry ? board.enter : copy.move}: ${place.label}`} aria-describedby={hint}
       onClick={() => {if (!locked && places.availableMoves.includes(place.id)) submit({type: 'MOVE', placeId: place.id});}}>{illustrated && graphical ? (entry ? board.enter : copy.move) : `${entry ? board.enter : copy.move}: ${place.label}`}</button></span>
     <p className={illustrated && graphical ? "game-board-cell-hint" : undefined} id={hint}>{locked ? <span lang={locale}>{copy.mapLocked}</span> : (places.movementHint ?? (!legal && place.moveHint)) || <span lang={locale}>{legal ? (entry ? board.entryHint : copy.moveAvailable) : copy.moveUnavailable}</span>}</p>
    </li>;
   })}
   {illustrated && graphical && marker ? <li className="game-board-player" data-current-place={places.currentPlaceId} aria-hidden="true" style={{left:marker.left,top:marker.top,transition:marker.animate?'left 200ms ease, top 200ms ease':'none'}}>
    <GameCharacter assetId={presentation!.playerAssetId} variant="marker" decorative/>
   </li> : null}
  </ul>
  {illustrated && graphical ? <>
   {locked ? <p lang={locale}>{copy.mapLocked}</p> : null}
   {places.places.some(p=>p.moveHint) ? <ul className="game-board-gates">{places.places.filter(p=>p.moveHint).map(p=><li key={p.id}><strong>{p.label}:</strong> {p.moveHint}</li>)}</ul> : null}
  </> : null}
  <p className="game-board-announcement" role="status" aria-live="polite" aria-atomic="true" lang={locale}>{announcement}</p>
  {inspected ? <section className="game-place-inspection" aria-labelledby={`${id}-inspection`}
    onKeyDown={event => {if (event.key === 'Escape') {event.stopPropagation(); close();}}}>
   <h4 id={`${id}-inspection`} tabIndex={-1} ref={heading}>{inspected.label}</h4>
   <p>{inspected.description ?? copy.noDescription}</p>
   <button type="button" lang={locale} onClick={close}>{copy.backToPlaces}</button>
  </section> : null}
 </>;
 return illustrated ? <section ref={container} className="game-art game-places game-board" aria-labelledby={`${id}-board`}>
  <h4 id={`${id}-board`} ref={boardHeading} tabIndex={-1} lang={locale}>{board.title}</h4>{content}
 </section> : <details className="game-places"><summary lang={locale}>{copy.places}</summary>{content}</details>;
}
