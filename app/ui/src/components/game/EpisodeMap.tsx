import type {Ref} from 'react';
import type {GameCommand, PlacesView} from '../../game/gameApi';
import {runCopy} from './runCopy';

export function EpisodeMap({places, locked, submit, details}: {
  places: PlacesView;
  locked: boolean;
  submit: (command: GameCommand) => void;
  details: Ref<HTMLDetailsElement>;
}) {
  const copy = runCopy.pl;

  return <details ref={details} className="game-scene-map game-episode-map">
    <summary>Mapa</summary>
    <ul aria-label="Miejsca na mapie">
      {places.places.map(place => {
        const current = place.id === places.currentPlaceId;
        const reachable = places.availableMoves.includes(place.id);
        return <li key={place.id} aria-current={current ? 'location' : undefined}>
          <strong>{place.label}</strong>
          {current ? <span>Jesteś tutaj</span>
            : reachable ? <button type="button" disabled={locked}
                aria-label={`${copy.move}: ${place.label}`}
                onClick={() => submit({type: 'MOVE', placeId: place.id})}>{copy.move}</button>
            : <span>{place.moveHint ?? places.movementHint ?? 'Nie ma stąd przejścia.'}</span>}
        </li>;
      })}
    </ul>
  </details>;
}
