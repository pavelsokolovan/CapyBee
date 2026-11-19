import {useEffect, useRef, useState} from 'react';
import {adventureCopy} from '../../game/adventureCopy';
import {storedStart} from '../../game/adventureStart';
import {decodeCapabilities, decodeSummary} from '../../game/gameDecoders';
import {ownerAllowed, watchGameOwner} from '../../game/gameOwner';
import {GameTransport} from '../../game/gameTransport';
import {restoreGameSession} from '../../game/restoreGameSession';
import {hasActiveAdventure, savedAdventure, type SavedAdventure} from '../../game/savedAdventure';
import {AdventurePreparation} from './AdventurePreparation';

type Entry = {owner: string; enabled: boolean; saved: SavedAdventure | null};
type Opening = {owner: string; resumeRunId?: string};

export function AdventureEntry({owner, locale}: {owner: string; locale: 'en' | 'pl'}) {
  const [entry, setEntry] = useState<Entry | null>(null);
  const [opening, setOpening] = useState<Opening | null>(null);
  const browseButton = useRef<HTMLButtonElement>(null);
  const resumeButton = useRef<HTMLButtonElement>(null);
  const open = opening?.owner === owner && ownerAllowed(owner) ? opening : null;

  useEffect(() => {
    if (open) return;

    const transport = new GameTransport(() => owner, () => restoreGameSession(owner));
    let checkId = 0;

    const hideSaved = () => {
      if (document.activeElement === resumeButton.current) browseButton.current?.focus();
      setEntry(previous => previous?.owner === owner && previous.saved ? {...previous, saved: null} : previous);
    };
    const cancelCheck = () => {
      checkId++;
      transport.detach();
      hideSaved();
    };
    const refresh = async () => {
      const id = ++checkId;
      const latest = () => id === checkId && ownerAllowed(owner) && navigator.onLine;
      try {
        const reply = await transport.read('/api/game/capabilities', {restoreSession: false});
        if (!latest()) return;
        if (reply.status !== 200) {hideSaved(); return;}
        const caps = decodeCapabilities(reply.body);
        setEntry(previous => ({owner, enabled: caps.clientEnabled, saved: previous?.owner === owner ? previous.saved : null}));
        if (!caps.clientEnabled || !caps.gameEnabled || storedStart(owner)) {hideSaved(); return;}
        const current = await transport.read('/api/game/runs/current', {restoreSession: false});
        if (!latest()) return;
        if (current.status === 200) {
          const run = decodeSummary(current.body);
          if (hasActiveAdventure(run, caps) && !storedStart(owner)) {
            setEntry({owner, enabled: true, saved: savedAdventure(run)});
            return;
          }
        }
        hideSaved();
      } catch {
        if (id === checkId) hideSaved();
      }
    };
    const onRevision = (event: Event) => {
      if ((event as CustomEvent<{owner: string}>).detail?.owner === owner) void refresh();
    };
    const unwatch = watchGameOwner(() => {
      if (!ownerAllowed(owner)) {
        cancelCheck();
        setEntry(null);
      }
    });
    void refresh();
    window.addEventListener('online', refresh);
    window.addEventListener('offline', cancelCheck);
    window.addEventListener('focus', refresh);
    window.addEventListener('game-revision', onRevision);
    return () => {
      checkId++;
      transport.detach();
      unwatch();
      window.removeEventListener('online', refresh);
      window.removeEventListener('offline', cancelCheck);
      window.removeEventListener('focus', refresh);
      window.removeEventListener('game-revision', onRevision);
    };
  }, [owner, open]);

  if (entry?.owner !== owner || !entry.enabled || !ownerAllowed(owner)) return null;

  const copy = adventureCopy[locale], saved = entry.saved;
  const close = () => {
    setOpening(null);
    requestAnimationFrame(() => {
      const opener = open?.resumeRunId ? resumeButton.current ?? browseButton.current : browseButton.current;
      opener?.focus();
    });
  };
  return <>
    {saved ? <button type="button" className="play-game-row adventure-resume" ref={resumeButton}
      onClick={() => setOpening({owner, resumeRunId: saved.runId})}>
      <span className="play-game-icon" aria-hidden="true">▶</span>
      <span className="play-game-copy">
        <strong>{copy.resume}</strong>
        <small lang={saved.title ? saved.locale : locale}>{saved.title ?? copy.saved}</small>
        {saved.location ? <small lang={saved.locale}>{saved.location}</small> : null}
      </span>
      <span aria-hidden="true">→</span>
    </button> : null}
    <button type="button" className="play-game-row" ref={browseButton} onClick={() => setOpening({owner})}>
      <span className="play-game-icon" aria-hidden="true">📖</span>
      <span className="play-game-copy"><strong>{copy.title}</strong><small>{copy.blurb}</small></span>
      <span aria-hidden="true">→</span>
    </button>
    {open ? <AdventurePreparation key={owner} owner={owner} locale={locale} resumeRunId={open.resumeRunId} onClose={close}/> : null}
  </>;
}
