import type {CharacterId, BackdropId, TerrainId, Expression} from '../assets/game/catalog';
export type GameRunStatus = 'PREPARING' | 'ACTIVE' | 'ENDED' | 'FAILED' | 'SUSPENDED' | 'CANCELLING' | 'CANCELLED';

/** Decoded, optional enrichment; unknown artwork has already resolved to a local fallback. */
export interface GamePresentation {
  schemaVersion: 1; catalogVersion: number; playerAssetId: CharacterId | null; backdropAssetId: BackdropId | null;
  places: Array<{placeId: string; terrainAssetId: TerrainId}>;
  characters: Array<{id: string; assetId: CharacterId; trait: string | null; need: string | null; expression: Expression; recruited: boolean}>;
  party: string[];
}

export interface GameIssueDto {
  severity: 'ERROR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
}

export interface GameViewV1 {
  presentation?: null;
  blocks: Array<{ kind: string; actorId?: string | null; text: string; tags: string[] }>;
  options: Array<{ id: string; label: string; enabled: boolean; hint?: string | null }>;
  input?: { mode: string; maxChars: number; prompt?: string | null } | null;
  status: Array<{ id: string; label: string; value: string }>;
  ended: boolean;
  canAdvance?: boolean;
  endReason?: 'COMPLETED' | 'ABANDONED' | null;
  objectives: Array<{ id: string; label: string; state: string }>;
  outcome?: string | null;
  places?: PlacesView | null;
}

export interface GameViewV2 {
  presentation?: GamePresentation | null;
  blocks: Array<{ kind: string; speaker?: string | null; text: string; refs: string[] }>;
  options: Array<{ id: string; label: string; enabled: boolean; hint?: string | null }>;
  input?: { mode: string; maxChars: number; prompt?: string | null } | null;
  status: Array<{ id: string; label: string; value: string }>;
  ended: boolean;
  canAdvance?: boolean;
  endReason?: 'COMPLETED' | 'ABANDONED' | null;
  objectives: Array<{ id: string; label: string; description?: string | null; state: string }>;
  outcome?: string | null;
  places?: PlacesView | null;
  story?: { title: string; premise: string; setting: string } | null;
  characters: Array<{ id: string; slotId: string; name: string; description: string; inCurrentScene: boolean }>;
  result?: {
    outcomeId: string;
    label: string;
    resultClass: 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILURE';
    headline: string;
    summary: string;
    objectives: Array<{ id: string; label: string; description?: string | null; state: 'ACTIVE' | 'DONE'; note: string }>;
    facts: Array<{ id: string; label: string }>;
    reflection?: string | null;
  } | null;
}

export type GameView = GameViewV1 | GameViewV2;

export interface GameRunSummaryDto {
  runId: string;
  status: GameRunStatus;
  revision: number;
  source: GameSource;
  packId: string | null;
  packVersion: string | null;
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
  setup?: {schemaVersion:1; metadataLocale:string; title:string; description:string|null;
    transientTextSupported:boolean; preparationFingerprint?:string|null; steps:Array<{id:string; title:string; inputIds:string[]}>; inputs:SetupInput[]};
}

export type GameSource = { kind: 'PACK'; packId: string; packVersion: string }
  | { kind: 'DYNAMIC'; profileId: string; profileVersion: string; gameFingerprint: string };
export interface PlacesView {
  currentPlaceId: string; places: Array<{id: string; label: string; description?: string | null; x: number | null; y: number | null}>;
  links: Array<{from: string; to: string; bidirectional: boolean}>;
  availableMoves: string[]; visitedPlaceIds: string[]; layoutKind: string;
}
export interface SetupInput {
  id: string; kind: 'ENUM' | 'ENUM_SET' | 'FREE_TEXT'; label: string; help: string; required: boolean;
  maxItems: number; maxCodePoints: number | null; defaults: string[];
  options: Array<{id: string; label: string}>; notice: string | null;
}
export interface GeneratorProfile {
  id: string; version: string; fingerprint: string; title: string; description: string;
  supportedLocales: string[]; defaultLocale: string; setupSchemaVersion: 1; metadataLocale: string;
  steps: Array<{id: string; title: string; inputIds: string[]}>; inputs: SetupInput[];
  suggestionAllowed: boolean; maxSuggestionCodePoints: number;
  lengthPresets: Array<{id:string;label:string}>; challengePresets: Array<{id:string;label:string}>;
}
export interface DynamicStartRequest {
  requestId: string; profileId: string; profileVersion: string; profileFingerprint: string;
  locale: string; lengthPreset: string; challengePreset: string; selections: Record<string, string[]>;
  suggestion?: string; disclosureRevision: number; confirmed: true; expectedCurrentRunId?: string;
}
export interface PackPreparationRequest {
 requestId:string; packId:string; packVersion:string; packFingerprint:string; locale:string; selections:Record<string,string[]>;
 suggestion?:string; disclosureRevision:number; confirmed:true; expectedCurrentRunId?:string;
}
export interface DynamicOperation {
  status: 'PROCESSING' | 'STARTED' | 'NEEDS_INPUT' | 'FALLBACK_OFFERED' | 'CANCELLED' | 'FAILED';
  retryAfterMs: number; run: GameRunSummaryDto | null; code: string | null;
}
export interface GameCapabilities {
  schemaVersion: 1; clientEnabled: boolean; gameEnabled: boolean; packStartEnabled: boolean;
  packGenerationEnabled: boolean; dynamicStartEnabled: boolean; supportedViewSchemas: number[];
  viewFeatures: string[]; disclosureRevision: number; dynamicCancelMode: 'BEST_EFFORT';
}
export interface GameError { error: {code: string; message: string; details: Record<string, unknown>} }
