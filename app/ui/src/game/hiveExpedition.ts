import type {GameRunSummaryDto} from './gameApi';

export const hiveExpeditionPackId = 'capybee-new-hive';
export const hiveExpeditionTitle = 'Nowy ul, dwa światła';

const hiveDestinations = {
 'visit-lantern': 'old-lantern',
 'meet-mela': 'quiet-meadow',
 'meet-borys': 'reed-bank',
 'meet-luna': 'willow-glen',
 'visit-workshop': 'hive-workshop',
} as const;

export function visitedHiveChoices(run:GameRunSummaryDto) {
 const places=run.packId===hiveExpeditionPackId?run.view?.places:null;
 if(!places)return [];
 return Object.entries(hiveDestinations)
  .filter(([,place])=>places.visitedPlaceIds.includes(place))
  .map(([choice])=>choice);
}
