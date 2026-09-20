export type PollenMatchTileKind = 'pair' | 'wildcard';

export interface PollenMatchTile {
  id: string;
  kind: PollenMatchTileKind;
  pairKey: string;
  value: string;
  flipped: boolean;
  matched: boolean;
}

export interface PollenMatchResult {
  durationMs: number;
  flips: number;
  pairsTotal: number;
  completedAt: string;
}

const PAIR_ICONS = ['🌼', '🍯', '🍃', '🌻'];

function shuffle<T>(items: T[]): T[] {
  const next = [...items];
  for (let index = next.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [next[index], next[swapIndex]] = [next[swapIndex], next[index]];
  }
  return next;
}

export function createPollenMatchBoard(): PollenMatchTile[] {
  const pairs = PAIR_ICONS.flatMap((value, index) => {
    const baseId = `${value}-${index}`;
    return [
      { id: `${baseId}-a`, kind: 'pair' as const, pairKey: value, value, flipped: false, matched: false },
      { id: `${baseId}-b`, kind: 'pair' as const, pairKey: value, value, flipped: false, matched: false }
    ];
  });

  const wildcard: PollenMatchTile = {
    id: 'wildcard-center',
    kind: 'wildcard',
    pairKey: 'wildcard',
    value: '🐝',
    flipped: false,
    matched: false
  };

  return shuffle([...pairs, wildcard]);
}

export function getTileKey(tile: PollenMatchTile | string): string {
  if (typeof tile === 'string') return tile;
  return tile.pairKey;
}

export function evaluateTurn(tiles: PollenMatchTile[], firstId: string, secondId: string): PollenMatchTile[] {
  const firstIndex = tiles.findIndex((tile) => tile.id === firstId);
  const secondIndex = tiles.findIndex((tile) => tile.id === secondId);

  if (firstIndex === -1 || secondIndex === -1 || firstIndex === secondIndex) {
    return tiles;
  }

  const next = tiles.map((tile) => ({ ...tile }));
  const first = next[firstIndex];
  const second = next[secondIndex];

  if (!first || !second) {
    return tiles;
  }

  first.flipped = true;
  second.flipped = true;

  if (first.kind === 'pair' && second.kind === 'pair' && first.pairKey === second.pairKey) {
    first.matched = true;
    second.matched = true;
    return next;
  }

  first.flipped = false;
  second.flipped = false;
  return next;
}
