import {object,integer,text} from './gameDecoders';
export interface GameCompletion {
 runId:string; revision:number; completedAt:string; suggestionKey:string; suggestionVersion:number;
 mission:{id:string;code:string;title:string;timeHint:string}|null; missionAccepted:boolean; cellAdded:boolean;
}
export function decodeCompletion(value:unknown):GameCompletion {
 const v=object(value);text(v.runId);integer(v.revision);text(v.completedAt);text(v.suggestionKey);integer(v.suggestionVersion);
 if(!Number.isFinite(Date.parse(String(v.completedAt)))||v.suggestionVersion===0||typeof v.missionAccepted!=='boolean'||typeof v.cellAdded!=='boolean')throw new Error('GAME_COMPLETION_UNSUPPORTED');
 if(v.mission!=null){const m=object(v.mission);for(const key of ['id','code','title','timeHint'])text(m[key]);}
 return v as unknown as GameCompletion;
}
