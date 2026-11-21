import {useState} from 'react';
import {catalog, type AssetId, type CharacterId} from '../../../assets/game/catalog';
import {GameBackdrop, GameCharacter, GameMotif, GameTerrain, GameTileArt, type TileState} from './GameArt';
import {galleryCopy} from './galleryCopy';
import './gallery.css';

// Trusted synthetic presentation fixtures; these are never consulted by gameplay.
const settings = [
  {terrain: 'terrain.meadow', backdrop: 'backdrop.hive-clearing'},
  {terrain: 'terrain.lantern', backdrop: 'backdrop.lantern-grove'},
  {terrain: 'terrain.meadow', backdrop: 'backdrop.quiet-meadow'},
  {terrain: 'terrain.water', backdrop: 'backdrop.reed-bank'},
  {terrain: 'terrain.willow', backdrop: 'backdrop.willow-glen'},
  {terrain: 'terrain.workshop', backdrop: 'backdrop.hive-workshop'},
  {terrain: 'terrain.crossing', backdrop: 'backdrop.small-crossing'},
] as const;
const tiles: readonly {states: readonly TileState[]; focus?: boolean}[] = [
  {states: ['current']}, {states: ['visited']}, {states: ['available']}, {states: ['unavailable']},
  {states: ['selected']}, {states: [], focus: true}, {states: ['pending']},
  {states: ['current', 'visited']}, {states: ['available'], focus: true},
  {states: ['selected'], focus: true}, {states: ['current', 'pending']},
];
const characterIds = (Object.keys(catalog) as AssetId[]).filter((id): id is CharacterId => catalog[id].kind === 'character');

export default function GameArtGallery() {
  const [locale, setLocale] = useState<'en' | 'pl'>('en');
  const [text, setText] = useState(false), [selected, setSelected] = useState(0), [feedback, setFeedback] = useState<'paused' | 'sample' | null>(null);
  const c = galleryCopy[locale];
  return <main className={`game-art art-gallery${text ? ' art-text-only' : ''}`} lang={locale}>
    <header className="art-header">
      <p className="art-eyebrow">{c.eyebrow}</p>
      <div className="art-toolbar"><label>{c.language} <select value={locale} onChange={e => setLocale(e.target.value as 'en' | 'pl')}>
        <option value="en">English</option><option value="pl">Polski</option>
      </select></label><button type="button" aria-pressed={text} onClick={() => setText(!text)}>{c.text}</button></div>
      <h1>{c.title}</h1><p className="art-intro">{c.intro}</p><p className="art-preview-note">{c.preview}</p>
    </header>
    <div className="art-adventure">
      <section className="art-board-panel" aria-labelledby="art-board-heading">
        <h2 id="art-board-heading">{c.board}</h2><p>{c.boardHint}</p>
        <div className="art-board" data-testid="board">
          {settings.map((setting, i) => <button key={setting.backdrop} type="button" className={`art-place art-place-${i}`}
            aria-pressed={selected === i} onClick={() => setSelected(i)}>
            <GameTileArt assetId={setting.terrain} states={i === 0 ? ['current', 'visited'] : selected === i ? ['available', 'selected'] : ['available']}/>
            <span>{c.places[i]}</span><small>{i === 0 ? `${c.state.current} · ${c.state.visited}` : c.state.available}</small>
          </button>)}
        </div>
        <p className="art-selection" role="status">{c.selected}: {c.places[selected]}</p>
      </section>
      <section className="art-encounter" aria-labelledby="art-encounter-heading" data-testid="encounter">
        <div className="art-scene"><GameBackdrop assetId="backdrop.reed-bank" decorative/>
          <span className="art-scene-capy"><GameCharacter assetId="character.capy" decorative/></span>
          <span className="art-scene-companion"><GameCharacter assetId="character.borys-beaver" orientation="left" expression="attentive" decorative/></span>
        </div>
        <div className="art-dialogue"><h2 id="art-encounter-heading">{c.encounter}</h2>
          <p className="art-speaker">{c.speaker} <span>· {c.trait}</span></p><p>{c.need}</p><p className="art-quote">{c.dialogue}</p>
          <div className="art-choices">{c.choices.map((choice, i) => <button type="button" key={choice}
            onClick={() => setFeedback(i === 2 ? 'paused' : 'sample')}>{choice}</button>)}</div>
          <p role="status">{feedback ? c[feedback] : c.paused}</p>
        </div>
      </section>
    </div>
    <section className="art-party-section" aria-labelledby="art-party-heading" data-testid="party">
      <div><h2 id="art-party-heading">{c.party}</h2><p>{c.partyHint}</p>
        <ul className="art-party">{characterIds.slice(1, 4).map((id, i) => <li key={id}>
          <span className="art-party-portrait"><GameCharacter assetId={id} variant="portrait" expression="pleased" decorative/></span>
          <strong>{c.names[i + 1]}</strong>
        </li>)}</ul>
      </div><p className="art-empty-party">{c.empty}</p>
    </section>
    <section aria-labelledby="art-characters-heading"><h2 id="art-characters-heading">{c.characters}</h2>
      <div className="art-portrait-scales">{([40, 64] as const).map(size => <figure key={size}>
        <div style={{width: size}}><GameCharacter assetId="character.mela-bee" variant="portrait" decorative/></div>
        <figcaption>{c.names[1]} · {size}px</figcaption>
      </figure>)}</div>
      {characterIds.map((id, i) => <section className="art-character-sheet" key={id} data-testid={id}>
        <h3>{c.names[i]}</h3><code aria-hidden="true">{id}</code>
        <div className="art-character-variants">{catalog[id].variants.flatMap(variant => (variant === 'marker' ? ['neutral'] as const : catalog[id].expressions).map(expression =>
          <figure key={`${variant}-${expression}`}><div className={`art-character art-character-${variant}`}>
            <GameCharacter assetId={id} variant={variant} expression={expression} decorative/>
          </div><figcaption>{c.variant[variant]} · {c.expression[expression]}</figcaption></figure>))}</div>
      </section>)}
    </section>
    <section aria-labelledby="art-settings-heading"><h2 id="art-settings-heading">{c.settings}</h2>
      <div className="art-settings">{(Object.keys(catalog) as AssetId[]).filter(id => catalog[id].kind === 'backdrop').map((id, i) =>
        <figure key={id} data-testid={id}><GameBackdrop assetId={id as Parameters<typeof GameBackdrop>[0]['assetId']} decorative/>
          <figcaption>{id==='backdrop.case-study'?c.caseStudy:(c.places[i] ?? c.neutralSetting)}<code aria-hidden="true">{id}</code></figcaption></figure>)}</div>
    </section>
    <section aria-labelledby="art-terrain-heading"><h2 id="art-terrain-heading">{c.terrain}</h2>
      <div className="art-terrain-list">{(Object.keys(catalog) as AssetId[]).filter(id => catalog[id].kind === 'terrain').map((id, i) =>
        <figure key={id}><div className="art-terrain-sample"><GameTerrain assetId={id as Parameters<typeof GameTerrain>[0]['assetId']} decorative/></div><figcaption>{c.terrainNames[i]}<code aria-hidden="true">{id}</code></figcaption></figure>)}</div>
    </section>
    <section aria-labelledby="art-states-heading"><h2 id="art-states-heading">{c.states}</h2>
      <div className="art-states">{tiles.map(({states, focus}, i) => <button key={i} type="button" className={focus ? 'game-focus-sample' : undefined}
        onClick={() => setFeedback('sample')}><span className="art-state-image"><GameTileArt assetId="terrain.meadow" states={states}/></span>
        <span>{[...states.map(s => c.state[s]), ...(focus ? [c.state.focus] : [])].join(' · ')}</span>
      </button>)}</div>
    </section>
    <section aria-labelledby="art-motifs-heading"><h2 id="art-motifs-heading">{c.motifs}</h2>
      <div className="art-motifs">{(Object.keys(catalog) as AssetId[]).filter(id => catalog[id].kind === 'motif').map((id, i) =>
        <figure key={id}><GameMotif assetId={id as Parameters<typeof GameMotif>[0]['assetId']} decorative/><figcaption>{c.motifNames[i]}<code aria-hidden="true">{id}</code></figcaption></figure>)}</div>
    </section>
    <section className="art-fallback" data-testid="fallback"><div className="art-character"><GameCharacter decorative/></div>
      <div><h2>{c.fallback}</h2><p>{c.fallbackText}</p><p>{c.long}</p></div>
    </section>
  </main>;
}
