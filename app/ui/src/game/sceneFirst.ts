import {anonymousNotePackId} from './anonymousNote';
import type {GameRunSummaryDto} from './gameApi';
import {hiveExpeditionPackId} from './hiveExpedition';

export function sceneFirstKind(run: GameRunSummaryDto): 'episode' | 'expedition' | null {
  if (run.packId === anonymousNotePackId) return 'episode';
  if (run.packId === hiveExpeditionPackId || run.view?.presentation != null) return 'expedition';
  return null;
}
