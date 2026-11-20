import type {GameRunSummaryDto} from './gameApi';
import {hiveExpeditionPackId} from './hiveExpedition';

const objectivePlaces = {
 'carry-memory':'old-lantern',
 'listen-mela':'quiet-meadow',
 'help-borys':'reed-bank',
 'mark-luna-rest':'willow-glen',
 'prepare-rail':'hive-workshop',
} as const;

export function completedHivePlaces(run:GameRunSummaryDto):string[] {
 if(run.packId!==hiveExpeditionPackId||!run.view?.places)return [];
 const {objectives,places}=run.view;
 return Object.entries(objectivePlaces)
  .filter(([objective,id])=>objectives.some(item=>item.id===objective&&item.state==='DONE')
   &&places.places.some(place=>place.id===id))
  .map(([,id])=>id);
}
