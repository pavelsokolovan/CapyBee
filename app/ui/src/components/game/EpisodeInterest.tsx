import {useId, useState} from 'react';

export function EpisodeInterest() {
  const [interested, setInterested] = useState(false);
  const noteId = useId();

  return <div className="game-episode-interest">
    <button type="button" aria-pressed={interested} aria-describedby={noteId}
      onClick={() => setInterested(value => !value)}>
      Chcę kolejny odcinek
    </button>
    <p id={noteId}>Twój wybór zostaje na tym ekranie.</p>
    <p role="status">{interested ? 'Dzięki! Zaznaczono zainteresowanie.' : ''}</p>
  </div>;
}
