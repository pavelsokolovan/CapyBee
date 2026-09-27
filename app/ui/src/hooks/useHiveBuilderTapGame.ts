export type HiveBuilderTapTile = {
  id: string;
  x: number;
  y: number;
  active: boolean;
};

export type HiveBuilderTapSequencePhase = 'showing-sequence' | 'waiting-for-input';

export interface HiveBuilderTapProgress {
  correct: boolean;
  nextIndex: number;
  isRoundComplete: boolean;
  longestSequence: number;
}

export function createHiveBuilderTapSequence(
  length: number,
  previousSequence?: string[]
): string[] {
  const maxTileCount = 7;
  const tileIds = Array.from({ length: maxTileCount }, (_, index) => `hex-${index}`);

  if (previousSequence && previousSequence.length > 0) {
    const usedTiles = new Set(previousSequence);
    const availableTiles = tileIds.filter((id) => !usedTiles.has(id));

    if (availableTiles.length === 0) {
      return previousSequence;
    }

    const randomTile = availableTiles[Math.floor(Math.random() * availableTiles.length)];
    return [...previousSequence, randomTile];
  }

  const firstTile = tileIds[Math.floor(Math.random() * tileIds.length)];
  return length > 1 ? [firstTile] : [firstTile];
}

export function startHiveBuilderTap(): string[] {
  return createHiveBuilderTapSequence(1);
}

export function advance(sequence: string[]): string[] {
  return [...sequence, `hex-${sequence.length}`];
}

export function advanceHiveBuilderTapSequence(sequence: string[]): string[] {
  return advance(sequence);
}

export function getHiveBuilderTapLayout(length: number): HiveBuilderTapTile[] {
  const positions: { x: number; y: number }[] = [
    { x: 0, y: 0 },
    { x: 1, y: 0 },
    { x: -1, y: 1 },
    { x: 0, y: 1 },
    { x: 1, y: 1 },
    { x: -1, y: 2 },
    { x: 0, y: 2 }
  ];

  return Array.from({ length }, (_, index) => ({
    id: `hex-${index}`,
    x: positions[index]?.x ?? 0,
    y: positions[index]?.y ?? 0,
    active: false
  }));
}

export function evaluateHiveBuilderTap(
  sequence: string[],
  currentIndex: number,
  tappedId: string
): HiveBuilderTapProgress {
  const expectedId = sequence[currentIndex];
  const correct = expectedId === tappedId;

  if (!correct) {
    return {
      correct: false,
      nextIndex: 0,
      isRoundComplete: false,
      longestSequence: 0
    };
  }

  const nextIndex = currentIndex + 1;
  const isRoundComplete = nextIndex >= sequence.length;

  return {
    correct: true,
    nextIndex,
    isRoundComplete,
    longestSequence: nextIndex
  };
}
