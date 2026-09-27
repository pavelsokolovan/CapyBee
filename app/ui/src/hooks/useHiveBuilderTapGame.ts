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

export function createHiveBuilderTapSequence(length: number): string[] {
  const next: string[] = [];
  for (let index = 0; index < length; index += 1) {
    const id = `hex-${index}`;
    next.push(id);
  }
  return next;
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
