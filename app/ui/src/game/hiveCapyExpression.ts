import type {CharacterExpression} from '../assets/game/catalog';
import type {GameRunSummaryDto} from './gameApi';
import {hiveEncounters} from './hiveCompletion';
import {hiveExpeditionPackId} from './hiveExpedition';

export function hiveCapyExpression({packId,view}:GameRunSummaryDto):CharacterExpression|undefined {
 if(packId!==hiveExpeditionPackId||!view?.presentation)return undefined;
 if(view.ended){
  return view.endReason==='COMPLETED'&&(view.outcome==='new-path'||view.outcome==='two-lights')
   ?'pleased':'neutral';
 }
 const backdrop=view.presentation.backdropAssetId;
 if(backdrop==='backdrop.small-crossing')return 'attentive';
 const objectiveId=Object.entries(hiveEncounters).find(([,item])=>item.backdrop===backdrop)?.[0];
 const state=view.objectives.find(objective=>objective.id===objectiveId)?.state;
 if(state==='DONE')return objectiveId==='help-borys'?'playful':'pleased';
 return state==='ACTIVE'?'attentive':'neutral';
}
