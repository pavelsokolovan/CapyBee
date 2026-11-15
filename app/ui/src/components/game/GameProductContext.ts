import {createContext} from 'react';
import type {GameDayEntry} from '../../game/dailyGameCells';
export const GameProductContext=createContext<{
 games:GameDayEntry[]; pendingCell?:boolean; refresh:()=>void; openMission:(id:string)=>void;
}|null>(null);
