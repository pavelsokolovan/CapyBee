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

  it('extends an existing sequence by one new random tile and reshuffles', () => {
    const previousSequence = ['hex-1', 'hex-3'];
    const sequence1 = createHiveBuilderTapSequence(previousSequence);

    expect(sequence1).toHaveLength(3);
    expect(new Set(sequence1).size).toBe(3);
    expect(sequence1).toContain('hex-1');
    expect(sequence1).toContain('hex-3');

    const newTile = sequence1.find((id) => !previousSequence.includes(id));
    expect(newTile).toBeDefined();
  });

  it('produces different sequences on repeated calls (randomization verification)', () => {
    const previousSequence = ['hex-2', 'hex-4'];
    const sequences = [
      createHiveBuilderTapSequence(previousSequence),
      createHiveBuilderTapSequence(previousSequence),
      createHiveBuilderTapSequence(previousSequence),
      createHiveBuilderTapSequence(previousSequence),
      createHiveBuilderTapSequence(previousSequence)
    ];

    const seqStrings = sequences.map((s) => s.join(','));
    const uniqueSeqs = new Set(seqStrings);

    expect(uniqueSeqs.size).toBeGreaterThan(1);
  });

  it('creates a fresh random round when the next sequence grows', () => {
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
