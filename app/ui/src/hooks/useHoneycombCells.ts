import {reconcileGameDays} from '../game/dailyGameCells';

export type HoneycombCellType = 'mission' | 'memory' | 'checkin' | 'friendship' | 'game' | 'empty';
export type HoneycombWorld = 'old_world' | 'new_world' | null;

export interface HoneycombCellData {
  id: string;
  type: HoneycombCellType;
  world: HoneycombWorld;
  title: string;
  date: string;
  empty: boolean;
  timestamp: number;
  gameKey?: string;
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

interface GameResultLike {
  id: string;
  gameKey: string;
  completedAt: string;
  durationMs: number;
  flips: number;
  pairsTotal: number;
}

export interface UseHoneycombCellsInput {
  checkIns: CheckInLike[];
  missions: MissionCompletionLike[];
  friendships: FriendshipLike[];
  memories: MemoryLike[];
  games: GameResultLike[];
  locale: 'en' | 'pl';
  worldFilter?: 'old_world' | 'new_world';
}

function toTimestamp(input: string): number {
  const parsed = Date.parse(input);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatHoneycombDate(input: string, locale: 'en' | 'pl'): string {
  const parsed = toTimestamp(input);
  if (parsed === 0) return '';
  return new Intl.DateTimeFormat(locale==='pl'?'pl-PL':'en-GB',{day:'2-digit',month:'2-digit'}).format(new Date(parsed)).replace('/', '.');
}

/** Returns all filled cells sorted chronologically (oldest first).
 *  No empty padding — HoneycombMap manages display rows. */
export function useHoneycombCells({
  checkIns,
  missions,
  friendships,
  memories,
  games,
  locale,
  worldFilter
}: UseHoneycombCellsInput): HoneycombCellData[] {
  const labelByGame = {
    pollen_match: locale === 'pl' ? 'Zbieranie Pyłku' : 'Pollen Match',
    hive_builder_tap: locale === 'pl' ? 'Budowanie ula' : 'Hive Builder',
    capybee_rpg: locale === 'pl' ? 'Przygoda' : 'Adventure'
  } as const;

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
    })),
    ...reconcileGameDays(games).map((entry) => ({
      id: entry.id,
      type: 'game' as const,
      world: null,
      title: labelByGame[entry.gameKey as keyof typeof labelByGame] ?? (locale==='pl'?'Gra':'Game'),
      date: formatHoneycombDate(entry.completedAt, locale),
      empty: false,
      timestamp: toTimestamp(entry.completedAt),
      gameKey: entry.gameKey
    }))
  ].sort((a, b) => a.timestamp - b.timestamp);

  if (worldFilter) {
    return normalized.filter(
      (cell) => cell.type === 'checkin' || cell.world === worldFilter
    );
  }

  return normalized;
}
