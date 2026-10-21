export type GameRunStatus = 'PREPARING' | 'ACTIVE' | 'ENDED' | 'FAILED';

export interface GameIssueDto {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
}

export interface GameViewV1 {
  blocks: Array<{ kind: string; actorId?: string | null; text: string; tags: string[] }>;
  options: Array<{ id: string; label: string; enabled: boolean; hint?: string | null }>;
  input?: { mode: string; maxChars: number; prompt?: string | null } | null;
  status: Array<{ id: string; label: string; value: string }>;
  ended: boolean;
  endReason?: 'COMPLETED' | 'ABANDONED' | null;
  objectives: Array<{ id: string; label: string; state: string }>;
  outcome?: string | null;
  places?: unknown | null;
}

export interface GameViewV2 {
  blocks: Array<{ kind: string; speaker?: string | null; text: string; refs: string[] }>;
  options: Array<{ id: string; label: string; enabled: boolean; hint?: string | null }>;
  input?: { mode: string; maxChars: number; prompt?: string | null } | null;
  status: Array<{ id: string; label: string; value: string }>;
  ended: boolean;
  endReason?: 'COMPLETED' | 'ABANDONED' | null;
  objectives: Array<{ id: string; label: string; description?: string | null; state: string }>;
  outcome?: string | null;
  places?: unknown | null;
  story?: { title: string; premise: string; setting: string } | null;
  characters: Array<{ id: string; slotId: string; name: string; description: string; inCurrentScene: boolean }>;
}

export type GameView = GameViewV1 | GameViewV2;

export interface GameRunSummaryDto {
  runId: string;
  status: GameRunStatus;
  revision: number;
  packId: string;
  packVersion: string;
  locale: string;
  endReason?: 'COMPLETED' | 'ABANDONED' | null;
  failureCode?: string | null;
  viewSchema: 1 | 2;
  view: GameView | null;
  updatedAt: string;
}

export interface GameStartRequest {
  id: string;
  packId: string;
  locale: string;
  generated?: boolean;
  startInputs?: Record<string, string>;
  seed?: number | null;
}

export type GameCommand =
  | { type: 'CHOOSE'; optionId: string }
  | { type: 'ADVANCE' }
  | { type: 'SUBMIT_TEXT'; text: string }
  | { type: 'MOVE'; placeId: string }
  | { type: 'ABANDON' };

export interface GameCommandRequest {
  id: string;
  expectedRevision: number;
  command: GameCommand;
}

export interface GameCommandOutcomeDto {
  commandId: string;
  outcome: 'APPLIED' | 'REJECTED' | 'REPLAYED';
  accepted: boolean;
  appliedRevision: number;
  revision: number;
  status: GameRunStatus;
  view: GameView;
  viewSchema: 1 | 2;
  events: Array<{ id: string; payload: Record<string, string> }>;
  issues: GameIssueDto[];
}

export interface GameProcessingDto {
  runId?: string;
  commandId?: string;
  status: 'PREPARING' | 'PROCESSING';
  revision: number;
  retryAfterMs: number;
}

export interface GamePackInfoDto {
  id: string;
  version: string;
  locales: string[];
  defaultLocale: string;
  startInputs: unknown[];
  generated: boolean;
}
