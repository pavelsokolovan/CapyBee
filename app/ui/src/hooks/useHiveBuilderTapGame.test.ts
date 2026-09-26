import { describe, expect, it } from 'vitest';
import {
  createHiveBuilderTapSequence,
  evaluateHiveBuilderTap,
  getHiveBuilderTapLayout,
  type HiveBuilderTapTile
} from './useHiveBuilderTapGame';

describe('useHiveBuilderTapGame', () => {
  it('creates a short sequence with the correct tile count', () => {
    const sequence = createHiveBuilderTapSequence(4);
    expect(sequence).toHaveLength(4);
    expect(new Set(sequence).size).toBe(4);
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
