import {detectiveProfileId} from './detective';
import {anonymousNotePackId} from './anonymousNote';
import type {GameRunSummaryDto} from './gameApi';
import {hiveExpeditionPackId} from './hiveExpedition';

export function sceneFirstKind(run: GameRunSummaryDto): 'episode' | 'expedition' | 'detective' | null {
  if (run.source.kind === 'DYNAMIC' && run.source.profileId === detectiveProfileId) return 'detective';
  if (run.packId === anonymousNotePackId) return 'episode';
  if (run.packId === hiveExpeditionPackId || run.view?.presentation != null) return 'expedition';
  return null;
}
