import type {GameRunSummaryDto} from './gameApi';
import {hiveExpeditionPackId} from './hiveExpedition';

export const hiveEncounters = {
 'carry-memory':{place:'old-lantern',backdrop:'backdrop.lantern-grove'},
 'listen-mela':{place:'quiet-meadow',backdrop:'backdrop.quiet-meadow'},
 'help-borys':{place:'reed-bank',backdrop:'backdrop.reed-bank'},
 'mark-luna-rest':{place:'willow-glen',backdrop:'backdrop.willow-glen'},
 'prepare-rail':{place:'hive-workshop',backdrop:'backdrop.hive-workshop'},
} as const;

export function completedHivePlaces(run:GameRunSummaryDto):string[] {
 if(run.packId!==hiveExpeditionPackId||!run.view?.places)return [];
 const {objectives,places}=run.view;
 return Object.entries(hiveEncounters)
  .filter(([objective,{place:id}])=>objectives.some(item=>item.id===objective&&item.state==='DONE')
   &&places.places.some(place=>place.id===id))
  .map(([,entry])=>entry.place);
}
