import type {GameRunSummaryDto, GameViewV2} from '../gameApi';
import {companionView} from './companionViews';

export function illustratedRun(): GameRunSummaryDto & {view: GameViewV2} {
  const view = companionView();
  view.places = {
    currentPlaceId: 'camp', layoutKind: 'HEX', visitedPlaceIds: ['camp'], availableMoves: ['river'],
    places: [
      {id: 'camp', label: 'Obóz', x: 0, y: 0},
      {id: 'river', label: 'Rzeka', x: 1, y: 0, moveBehavior: 'ENTER_SCENE'},
    ],
    links: [{from: 'camp', to: 'river', bidirectional: true}],
  };
  return {
    runId: 'illustrated-run', source: {kind: 'PACK', packId: 'fixture-art', packVersion: '1.0.0'},
    packId: 'fixture-art', packVersion: '1.0.0', status: 'ACTIVE', revision: 0,
    locale: 'pl', viewSchema: 2, view, updatedAt: '2026-11-16T00:00:00Z',
  };
}
