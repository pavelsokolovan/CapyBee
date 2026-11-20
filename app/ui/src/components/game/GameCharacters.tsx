import {useEffect, useId, useRef, useState} from 'react';
import type {CharacterExpression} from '../../assets/game/catalog';
import type {GameView} from '../../game/gameApi';
import {gameCompanions, type PresentedCharacter} from '../../game/gameCompanions';
import {GameCharacter} from './art/GameArt';
import {companionCopy} from './companionCopy';
import './gameCharacters.css';

type Props = {view: GameView; locale: 'en' | 'pl'};

function CharacterCard({character: c, locale, history = false, expression}: {expression?:CharacterExpression; character: PresentedCharacter; locale: 'en' | 'pl'; history?: boolean}) {
  const copy = companionCopy[locale], Heading = history ? 'h4' : 'h5';
  return <article className={`game-character-card${history ? ' game-character-card-history' : ''}`} data-character-id={c.id}>
    <span className="game-character-portrait"><GameCharacter assetId={c.assetId} variant="portrait" expression={expression??c.expression} decorative/></span>
    <div className="game-character-copy"><Heading>{c.name}</Heading>
      {c.joined ? <p className="game-character-joined" lang={locale}>{copy.joined}</p> : null}
      {c.trait || c.need ? <dl>
        {c.trait ? <><dt lang={locale}>{copy.trait}</dt><dd>{c.trait}</dd></> : null}
        {c.need ? <><dt lang={locale}>{c.joined ? copy.helped : copy.need}</dt><dd>{c.need}</dd></> : null}
      </dl> : !history ? <p>{c.description}</p> : null}
      {history ? <><p>{c.description}</p>{c.inCurrentScene ? <p className="game-character-here" lang={locale}>{copy.here}</p> : null}</> : null}
    </div>
  </article>;
}

export function GameCurrentCharacters({view, locale, collapsible = false, capyExpression}: Props & {collapsible?: boolean; capyExpression?:CharacterExpression}) {
  const data = gameCompanions(view), titleId = useId();
  if (!data?.current.length) return null;
  const title = companionCopy[locale].current;
  const list = <ul className="game-character-list">{data.current.map(c =>
    <li key={c.id}><CharacterCard character={c} locale={locale} expression={c.assetId==='character.capy'?capyExpression:undefined}/></li>)}</ul>;
  const className = 'game-art game-current-characters';
  return collapsible ? <details className={className}>
    <summary lang={locale}>{title}</summary>{list}
  </details> : <section className={className} aria-labelledby={titleId}>
    <h4 id={titleId} lang={locale}>{title}</h4>{list}
  </section>;
}

export function GameCharacterHistory({view, locale}: Props) {
  const data = gameCompanions(view), copy = companionCopy[locale];
  const cast = data?.discovered ?? ('characters' in view ? view.characters : []);
  if (!cast.length) return null;
  const known = new Map(data?.known.map(c => [c.id, c]));
  return <details className={data ? 'game-art game-character-history' : undefined}>
    <summary lang={locale}>{copy.history}</summary>
    {cast.map(c => {
      const character = known.get(c.id);
      return character ? <CharacterCard key={c.id} character={character} locale={locale} history/> :
        <div key={c.id}><strong>{c.name}</strong><p>{c.description}</p>{c.inCurrentScene ? <p lang={locale}>{copy.here}</p> : null}</div>;
    })}
  </details>;
}

/** The caller enables announcements only for eligible accepted updates, never initial hydration. */
export function GameParty({view, locale, runId, revision, announce = false, ready = true, hideEmpty = false}: Props & {
  runId: string; revision: number; announce?: boolean; ready?: boolean; hideEmpty?: boolean;
}) {
  const data = gameCompanions(view), party = data?.party ?? [], titleId = useId(), copy = companionCopy[locale];
  const previous = useRef<{runId: string; revision: number; ids: Set<string>; ready: boolean; available: boolean} | null>(null);
  const [addedNames, setAddedNames] = useState<string[]>([]);
  const identity = JSON.stringify(party.map(c => [c.id, c.name]));
  const available = data !== null;
  useEffect(() => {
    const members = JSON.parse(identity) as Array<[string, string]>, before = previous.current;
    const next = {runId, revision, ids: new Set(members.map(([id]) => id)), ready, available};
    if (!before || before.runId !== runId) {previous.current = next; setAddedNames([]); return;}
    if (revision < before.revision) return;
    if (revision > before.revision) setAddedNames(announce && ready && before.ready && available && before.available ? members.filter(([id]) => !before.ids.has(id)).map(([,name]) => name) : []);
    previous.current = next;
  }, [runId, revision, identity, announce, ready, available]);
  if (!data) return null;
  const announcement = <p className="game-party-announcement" role="status" aria-live="polite" aria-atomic="true" lang={locale}>
    {addedNames.length ? copy.added(addedNames.join(', ')) : ''}
  </p>;
  return <>{!hideEmpty || party.length ? <section className="game-art game-party" aria-labelledby={titleId}>
    <h4 id={titleId} lang={locale}>{copy.party}</h4>
    {party.length ? <ul className="game-party-members">{party.map(c => <li key={c.id} data-character-id={c.id}>
      <span className="game-party-portrait"><GameCharacter assetId={c.assetId} variant="portrait" expression={c.expression} decorative/></span>
      <strong>{c.name}</strong>
    </li>)}</ul> : <p className="game-party-empty" lang={locale}>{copy.empty}</p>}
    {!hideEmpty ? announcement : null}
  </section> : null}{hideEmpty ? announcement : null}</>;
}
