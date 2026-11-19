import {anonymousNotePackId, anonymousNoteTitle} from './anonymousNote';
import type {GameCapabilities, GameRunSummaryDto, GameView} from './gameApi';
import {hiveExpeditionPackId, hiveExpeditionTitle} from './hiveExpedition';

export function hasActiveAdventure(run: GameRunSummaryDto, caps: GameCapabilities) {
  return run.status === 'ACTIVE' && !!run.view && !run.view.ended
    && caps.supportedViewSchemas.includes(run.viewSchema);
}

export function adventureTitle(run: GameRunSummaryDto) {
  if (run.packId === anonymousNotePackId) return anonymousNoteTitle;
  const story = run.view && 'story' in run.view ? run.view.story : null;
  return story?.title ?? (run.packId === hiveExpeditionPackId ? hiveExpeditionTitle : null);
}

export function adventureLocation(view: GameView | null | undefined) {
  const places = view?.places;
  return places?.places.find(place => place.id === places.currentPlaceId)?.label;
}

export type SavedAdventure = {
  runId: string;
  locale: string;
  title: string | null;
  location: string | undefined;
};

export function savedAdventure(run: GameRunSummaryDto): SavedAdventure {
  return {runId: run.runId, locale: run.locale, title: adventureTitle(run), location: adventureLocation(run.view)};
}
