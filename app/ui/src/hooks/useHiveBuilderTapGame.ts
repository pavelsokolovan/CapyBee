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

export function createHiveBuilderTapSequence(previousSequence: string[] = []): string[] {
  const availableTiles = ['hex-0', 'hex-1', 'hex-2', 'hex-3', 'hex-4', 'hex-5', 'hex-6'];

  if (previousSequence.length === 0) {
    const firstTile = availableTiles[Math.floor(Math.random() * availableTiles.length)];
    console.log('🎲 [Round 1] New sequence:', [firstTile]);
    return [firstTile];
  }

  if (previousSequence.length >= availableTiles.length) {
    return previousSequence;
  }

  const usedTiles = new Set(previousSequence);
  const unusedTiles = availableTiles.filter((id) => !usedTiles.has(id));
  const newTile = unusedTiles[Math.floor(Math.random() * unusedTiles.length)];
  const nextSequence = [...previousSequence, newTile];

  const shuffledSequence = [...nextSequence];
  for (let index = shuffledSequence.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [shuffledSequence[index], shuffledSequence[swapIndex]] = [shuffledSequence[swapIndex], shuffledSequence[index]];
  }

  console.log(`🎲 [Round ${shuffledSequence.length}] Previous: [${previousSequence.join(', ')}] → Added: ${newTile} → Shuffled: [${shuffledSequence.join(', ')}]`);
  return shuffledSequence;
}

export function startHiveBuilderTap(): string[] {
  return createHiveBuilderTapSequence();
}

export function advance(sequence: string[]): string[] {
  return [...sequence, `hex-${sequence.length}`];
}

export function advanceHiveBuilderTapSequence(sequence: string[]): string[] {
  return advance(sequence);
}

export function shouldHighlightHiveBuilderTapTile({
  phase,
  index,
  position,
  activePlaybackIndex
}: {
  phase: HiveBuilderTapSequencePhase;
  index: number;
  position: number;
  activePlaybackIndex: number;
}): boolean {
  if (phase !== 'showing-sequence') {
    return false;
  }

  return index === activePlaybackIndex && position === 0;
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
