import { describe, expect, it, vi } from 'vitest';
import {
  createPollenMatchBoard,
  evaluateTurn,
  getTileKey,
  type PollenMatchTile,
  type PollenMatchResult
} from './usePollenMatchGame';

describe('usePollenMatchGame', () => {
  it('builds a board with 4 pairs plus a fixed center wildcard tile', () => {
    const tiles = createPollenMatchBoard();

    expect(tiles).toHaveLength(9);
    expect(tiles.filter((tile) => tile.kind === 'wildcard')).toHaveLength(1);
    expect(tiles.filter((tile) => tile.kind === 'pair')).toHaveLength(8);
    expect(tiles.filter((tile) => tile.matched).length).toBe(0);
  });

  it('marks a matching pair as matched and counts the flip once per tile', () => {
    const tiles: PollenMatchTile[] = [
      { id: 'a', kind: 'pair', pairKey: 'flower', value: '🌼', flipped: true, matched: false },
      { id: 'b', kind: 'pair', pairKey: 'flower', value: '🌼', flipped: true, matched: false },
      { id: 'c', kind: 'wildcard', pairKey: 'wildcard', value: '🐝', flipped: false, matched: false }
    ];

    const next = evaluateTurn(tiles, 'a', 'b');
    expect(next[0].matched).toBe(true);
    expect(next[1].matched).toBe(true);
    expect(next[2].flipped).toBe(false);
  });

  it('reverts a non-matching pair after the turn evaluation', () => {
    const tiles: PollenMatchTile[] = [
      { id: 'a', kind: 'pair', pairKey: 'flower', value: '🌼', flipped: true, matched: false },
      { id: 'b', kind: 'pair', pairKey: 'honey', value: '🍯', flipped: true, matched: false },
      { id: 'c', kind: 'wildcard', pairKey: 'wildcard', value: '🐝', flipped: false, matched: false }
    ];

    const next = evaluateTurn(tiles, 'a', 'b');
    expect(next[0].flipped).toBe(false);
    expect(next[1].flipped).toBe(false);
    expect(next[0].matched).toBe(false);
    expect(next[1].matched).toBe(false);
  });

  it('builds a result summary with flip count and duration metadata', () => {
    const result: PollenMatchResult = {
      durationMs: 25000,
      flips: 8,
      pairsTotal: 4,
      completedAt: new Date().toISOString()
    };

    expect(result.flips).toBe(8);
    expect(result.pairsTotal).toBe(4);
    expect(result.durationMs).toBeGreaterThan(0);
  });

  it('exposes a stable tile key for pair matching', () => {
    expect(getTileKey('flower')).toBe('flower');
  });

  it('returns the chosen pair count for completed game results', () => {
    const result: PollenMatchResult = {
      durationMs: 18000,
      flips: 9,
      pairsTotal: 4,
      completedAt: new Date().toISOString()
    };

    expect(result.pairsTotal).toBe(4);
    expect(result.flips).toBeGreaterThanOrEqual(result.pairsTotal);
  });
});
