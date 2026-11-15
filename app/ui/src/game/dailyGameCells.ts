import {sameCalendarDay} from '../capybee';
export interface GameDayEntry {id: string; gameKey: string; completedAt: string}
/** Local calendar UI policy shared by every game; this is not a server grant cap. */
export function reconcileGameDays<T extends GameDayEntry>(entries: T[]): T[] {
 const byId=new Map<string,T>();
 for(const entry of entries)if(Number.isFinite(Date.parse(entry.completedAt)))byId.set(entry.id,entry);
 const sorted=[...byId.values()].sort((a,b)=>Date.parse(a.completedAt)-Date.parse(b.completedAt)||a.id.localeCompare(b.id));
 const days=new Set<string>();
 return sorted.filter(entry=>{const date=new Date(entry.completedAt),day=`${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
  if(days.has(day))return false;days.add(day);return true;});
}
export function hasGameCellOnDay(entries: GameDayEntry[], now: Date): boolean {
 return entries.some(entry=>Number.isFinite(Date.parse(entry.completedAt))&&sameCalendarDay(new Date(entry.completedAt),now));
}
