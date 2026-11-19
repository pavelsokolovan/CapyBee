import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {renderToStaticMarkup} from 'react-dom/server';
import {readFileSync, statSync, readdirSync} from 'node:fs';
import {resolve} from 'node:path';
import {describe, expect, it} from 'vitest';
import {catalog, catalogVersion, expressions, resolveAsset, type AssetId, type AssetKind} from '../../../assets/game/catalog';
import {GameBackdrop, GameCharacter, GameMotif, GameTerrain, GameTileArt} from './GameArt';
import GameArtGallery from './GameArtGallery';

(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
const artworkPath = resolve(process.cwd(), 'src/assets/game');
const dom = (html: string) => {const node = document.createElement('div'); node.innerHTML = html; return node;};

describe('fixed catalog boundary', () => {
  it('publishes complete, serializable metadata with local sources and bounded artwork', () => {
    expect(catalogVersion).toBe(1);
    const entries = Object.entries(catalog);
    expect(entries).toHaveLength(25);
    expect(new Set(entries.map(([id]) => id)).size).toBe(25);
    const counts: Record<AssetKind, number> = {character: 0, terrain: 0, backdrop: 0, motif: 0};
    for (const [id, entry] of entries) {
      counts[entry.kind]++;
      expect(id.startsWith(`${entry.kind}.`)).toBe(true);
      expect(entry.provenance).toBeTruthy(); expect(entry.safeArea).toBeTruthy();
      expect(entry.source).not.toMatch(/[/\\]/);
      expect(statSync(resolve(artworkPath, entry.source)).size).toBeLessThan(entry.kind === 'character' ? 20 * 1024 : entry.kind === 'backdrop' ? 30 * 1024 : 8 * 1024);
      if (entry.kind === 'character') {
        expect(entry.variants).toContain('portrait'); expect(entry.variants).toContain('figure');
        expect(entry.expressions).toEqual(expressions);
      }
    }
    expect(counts).toEqual({character: 5, terrain: 7, backdrop: 10, motif: 3});
    expect(JSON.parse(JSON.stringify(catalog))).toEqual(catalog);
    const bytes = readdirSync(artworkPath).reduce((sum, file) => sum + statSync(resolve(artworkPath, file)).size, 0);
    expect(bytes).toBeLessThan(500 * 1024);
  });

  it('rejects absent, wrong-family, prototype and hostile references without interpreting paths', () => {
    for (const bad of [undefined, null, '', '__proto__', 'constructor', '../mela.svg', '<svg onload=alert(1)>', 'https://example.invalid/a.svg', {}, 4]) {
      expect(resolveAsset('character', bad)).toBe('character.abstract-guide');
      expect(resolveAsset('terrain', bad)).toBe('terrain.neutral');
      expect(resolveAsset('backdrop', bad)).toBe('backdrop.neutral');
      expect(resolveAsset('motif', bad)).toBeNull();
    }
    for (const id of Object.keys(catalog) as AssetId[]) {
      expect(resolveAsset(catalog[id].kind, id)).toBe(id);
    }
    expect(resolveAsset('character', 'terrain.water')).toBe('character.abstract-guide');
  });

  it('renders every variant/expression and all family fallbacks as fixed SVGs', () => {
    for (const id of Object.keys(catalog) as AssetId[]) {
      const entry = catalog[id];
      if (entry.kind === 'character') {
        for (const variant of entry.variants) for (const expression of expressions) {
          const html = renderToStaticMarkup(<GameCharacter assetId={id as 'character.capy'} variant={variant} expression={expression} decorative/>);
          expect(html).toContain('<svg'); expect(html).toContain('<path');
          expect(html).not.toMatch(/NaN|undefined|foreignObject|<image|<script/);
          expect(Buffer.byteLength(html)).toBeLessThan(20 * 1024);
        }
      } else {
        const html = renderToStaticMarkup(entry.kind === 'terrain' ? <GameTerrain assetId={id as 'terrain.water'} decorative/> :
          entry.kind === 'backdrop' ? <GameBackdrop assetId={id as 'backdrop.neutral'} decorative/> : <GameMotif assetId={id as 'motif.reed'} decorative/>);
        expect(html).toContain('<svg');
        expect(Buffer.byteLength(html)).toBeLessThan(entry.kind === 'backdrop' ? 30 * 1024 : 8 * 1024);
      }
    }
    expect(renderToStaticMarkup(<GameMotif decorative/>)).toBe('');
    expect(renderToStaticMarkup(<GameCharacter decorative/>)).toContain('<svg');
    expect(renderToStaticMarkup(<GameTerrain decorative/>)).toContain('<svg');
    expect(renderToStaticMarkup(<GameBackdrop decorative/>)).toContain('<svg');
    const invalid = renderToStaticMarkup(<GameCharacter assetId={'terrain.water' as never} variant={'invalid' as never} expression={'bad' as never} orientation={'bad' as never} decorative/>);
    expect(invalid).toBe(renderToStaticMarkup(<GameCharacter assetId="character.abstract-guide" decorative/>));
    expect(renderToStaticMarkup(<GameCharacter assetId="character.mela-bee" variant="marker" expression="pleased" decorative/>))
      .toBe(renderToStaticMarkup(<GameCharacter assetId="character.mela-bee" decorative/>));
    expect(renderToStaticMarkup(<GameCharacter orientation="left" decorative/>)).toContain('scale(-1 1)');
  });

  it('uses unique title/clip IDs and keeps decorative art out of the accessibility tree', () => {
    const tree = dom(renderToStaticMarkup(<>
      <GameTerrain assetId="terrain.water" label={'Trzciny <script> & wiatr'}/>
      <GameTerrain assetId="terrain.water" label="Reeds"/>
      <GameCharacter label="Mela" assetId="character.mela-bee"/>
      <GameCharacter decorative/><GameBackdrop decorative/>
    </>));
    const ids = [...tree.querySelectorAll('[id]')].map(n => n.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(tree.querySelectorAll('[role=img]')).toHaveLength(3);
    for (const svg of tree.querySelectorAll('[role=img]')) {
      expect(tree.querySelector(`[id="${svg.getAttribute('aria-labelledby')}"]`)?.textContent).toBeTruthy();
    }
    for (const group of tree.querySelectorAll('[clip-path]')) {
      const id = group.getAttribute('clip-path')!.slice(5, -1);
      expect(ids).toContain(id);
    }
    for (const svg of tree.querySelectorAll('[aria-hidden=true]')) {
      expect(svg.getAttribute('role')).toBeNull(); expect(svg.querySelector('title')).toBeNull();
      expect(svg.getAttribute('focusable')).toBe('false');
    }
    expect(tree.querySelector('script')).toBeNull();
    expect(tree.querySelector('title')?.textContent).toBe('Trzciny <script> & wiatr');
  });

  it('contains no active, embedded, remote or runtime-interpreted SVG content', () => {
    for (const file of readdirSync(artworkPath)) {
      const source = readFileSync(resolve(artworkPath, file), 'utf8');
      expect(source).not.toMatch(/dangerouslySetInnerHTML|<\s*(?:script|foreignObject|image|animate|animateTransform|set|filter)\b|\bon[A-Z]\w*\s*=|\b(?:href|xlinkHref)\s*=|https?:\/\//);
    }
  });

  it('composes state indicators without changing underlying artwork or accepting a command', () => {
    const tree = dom(renderToStaticMarkup(<GameTileArt assetId="terrain.water" states={['current', 'visited', 'pending', 'selected']}/>));
    expect(tree.querySelector('.game-tile-art')?.getAttribute('data-states')).toBe('current visited pending selected');
    expect(tree.querySelector('.game-tile-marker')).not.toBeNull();
    expect(tree.querySelector('.game-tile-visited')?.textContent).toBe('✓');
    expect(tree.querySelectorAll('.game-tile-inset')).toHaveLength(2);
    expect(tree.querySelector('button')).toBeNull();
  });
});

it('keeps fixture content and controls when switching to Polish and text mode', async () => {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  try {
    await act(async () => root.render(<GameArtGallery/>));
    expect(host.querySelectorAll('.art-character-variants figure')).toHaveLength(31);
    expect(host.querySelectorAll('.art-states button')).toHaveLength(11);
    const select = host.querySelector('select')!;
    await act(async () => {select.value = 'pl'; select.dispatchEvent(new Event('change', {bubbles: true}));});
    expect(host.querySelector('main')?.lang).toBe('pl');
    const before = host.querySelector('[data-testid=encounter]')!.textContent;
    await act(async () => host.querySelector<HTMLButtonElement>('.art-toolbar button')!.click());
    expect(host.querySelector('.art-text-only')).not.toBeNull();
    expect(host.querySelector('[data-testid=encounter]')!.textContent).toBe(before);
    await act(async () => host.querySelectorAll<HTMLButtonElement>('.art-choices button')[2].click());
    expect(host.querySelector('.art-dialogue [role=status]')?.textContent).toContain('Możesz odpocząć');
    await act(async () => host.querySelectorAll<HTMLButtonElement>('.art-place')[2].click());
    expect(host.querySelector('.art-selection')?.textContent).toContain('Cicha Łąka');
    expect(host.querySelector('[data-testid=fallback]')?.textContent).toContain('Nieznany gość');
  } finally {await act(async () => root.unmount()); host.remove();}
});
