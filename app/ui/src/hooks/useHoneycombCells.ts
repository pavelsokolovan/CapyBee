import { useMemo } from 'react';

export type HoneycombCellType = 'mission' | 'memory' | 'checkin' | 'friendship' | 'empty';
export type HoneycombWorld = 'old_world' | 'new_world' | null;

export interface HoneycombCellData {
  id: string;
  type: HoneycombCellType;
  world: HoneycombWorld;
  title: string;
  date: string;
  empty: boolean;
  timestamp: number;
}

interface CheckInLike {
  id: string;
  mood: string;
  createdAt: string;
}

interface MissionCompletionLike {
  id: string;
  title: string;
  completedAt: string;
  worldType?: 'old_world' | 'new_world';
}

interface FriendshipLike {
  id: string;
  personLabel: string;
  createdAt: string;
}

interface MemoryLike {
  id: string;
  worldType: 'old_world' | 'new_world';
  title?: string;
  textContent?: string;
  createdAt: string;
}

export interface UseHoneycombCellsInput {
  checkIns: CheckInLike[];
  missions: MissionCompletionLike[];
  friendships: FriendshipLike[];
  memories: MemoryLike[];
  locale: 'en' | 'pl';
  worldFilter?: 'old_world' | 'new_world';
}

const dateFormatterByLocale = {
  en: new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: '2-digit' }),
  pl: new Intl.DateTimeFormat('pl-PL', { day: '2-digit', month: '2-digit' })
};

function toTimestamp(input: string): number {
  const parsed = Date.parse(input);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatHoneycombDate(input: string, locale: 'en' | 'pl'): string {
  const parsed = toTimestamp(input);
  if (parsed === 0) return '';
  return dateFormatterByLocale[locale].format(new Date(parsed)).replace('/', '.');
}

/** Returns all filled cells sorted chronologically (oldest first).
 *  No empty padding — HoneycombMap manages display rows. */
export function useHoneycombCells({
  checkIns,
  missions,
  friendships,
  memories,
  locale,
  worldFilter
}: UseHoneycombCellsInput): HoneycombCellData[] {
  return useMemo(() => {
    const normalized: HoneycombCellData[] = [
      ...checkIns.map((entry) => ({
        id: entry.id,
        type: 'checkin' as const,
        world: null,
        title: entry.mood,
        date: formatHoneycombDate(entry.createdAt, locale),
        empty: false,
        timestamp: toTimestamp(entry.createdAt)
      })),
      ...missions.map((entry) => ({
        id: entry.id,
        type: 'mission' as const,
        world: (entry.worldType ?? 'new_world') as HoneycombWorld,
        title: entry.title,
        date: formatHoneycombDate(entry.completedAt, locale),
        empty: false,
        timestamp: toTimestamp(entry.completedAt)
      })),
      ...memories.map((entry) => ({
        id: entry.id,
        type: 'memory' as const,
        world: entry.worldType as HoneycombWorld,
        title: entry.title?.trim() || entry.textContent?.trim() || 'Memory',
        date: formatHoneycombDate(entry.createdAt, locale),
        empty: false,
        timestamp: toTimestamp(entry.createdAt)
      })),
      ...friendships.map((entry) => ({
        id: entry.id,
        type: 'friendship' as const,
        world: 'new_world' as const,
        title: entry.personLabel,
        date: formatHoneycombDate(entry.createdAt, locale),
        empty: false,
        timestamp: toTimestamp(entry.createdAt)
      }))
    ].sort((a, b) => a.timestamp - b.timestamp);

    if (worldFilter) {
      return normalized.filter(
        (cell) => cell.type === 'checkin' || cell.world === worldFilter
      );
    }

    return normalized;
  }, [checkIns, missions, friendships, memories, locale, worldFilter]);
}
