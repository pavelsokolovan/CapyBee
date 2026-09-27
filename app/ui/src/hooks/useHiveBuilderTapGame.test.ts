import { describe, expect, it } from 'vitest';
import {
  advance,
  createHiveBuilderTapSequence,
  evaluateHiveBuilderTap,
  getHiveBuilderTapLayout,
  startHiveBuilderTap,
  type HiveBuilderTapTile
} from './useHiveBuilderTapGame';

describe('useHiveBuilderTapGame', () => {
  it('starts each round with a single random tile sequence', () => {
    const previousRandom = Math.random;
    Math.random = () => 0.2;

    try {
      const sequence = startHiveBuilderTap();
      expect(sequence).toHaveLength(1);
      expect(sequence[0]).toMatch(/^hex-[0-6]$/);
    } finally {
      Math.random = previousRandom;
    }
  });

  it('extends an existing sequence by one new random tile', () => {
    const previousRandom = Math.random;
    Math.random = () => 0.7;

    try {
      const sequence = createHiveBuilderTapSequence(2, ['hex-1', 'hex-3']);
      expect(sequence).toHaveLength(3);
      expect(sequence.slice(0, 2)).toEqual(['hex-1', 'hex-3']);
      expect(sequence[2]).not.toBe('hex-1');
      expect(sequence[2]).not.toBe('hex-3');
      expect(sequence[2]).toMatch(/^hex-[0-6]$/);
    } finally {
      Math.random = previousRandom;
    }
  });

  it('appends the next tile number when the round advances', () => {
    expect(advance(['hex-0', 'hex-1'])).toEqual(['hex-0', 'hex-1', 'hex-2']);
  });

  it('accepts the expected tap and advances the index', () => {
    const sequence = ['hex-0', 'hex-2', 'hex-1'];
    const next = evaluateHiveBuilderTap(sequence, 0, 'hex-0');

    expect(next.correct).toBe(true);
    expect(next.nextIndex).toBe(1);
    expect(next.isRoundComplete).toBe(false);
  });

  it('rejects a wrong tap and resets to zero', () => {
    const sequence = ['hex-0', 'hex-2'];
    const next = evaluateHiveBuilderTap(sequence, 1, 'hex-0');

    expect(next.correct).toBe(false);
    expect(next.nextIndex).toBe(0);
    expect(next.isRoundComplete).toBe(false);
  });

  it('returns a growing hex layout up to the requested size', () => {
    const layout = getHiveBuilderTapLayout(7);
    expect(layout.length).toBe(7);
    expect(layout.every((tile: HiveBuilderTapTile) => tile.id.startsWith('hex-'))).toBe(true);
  });
});
