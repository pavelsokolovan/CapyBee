import {expect,it} from 'vitest';
import {hasGameCellOnDay,reconcileGameDays} from './dailyGameCells';
const entry=(id:string,at:string,gameKey='pollen_match')=>({id,completedAt:at,gameKey});
it('uses one local game day across all games and reconciles duplicates deterministically',()=>{
 const first=entry('first',new Date(2026,10,15,0,1).toISOString());
 const second=entry('second',new Date(2026,10,15,23,59).toISOString(),'capybee_rpg');
 const third=entry('third',new Date(2026,10,16,0,0).toISOString(),'hive_builder_tap');
 expect(reconcileGameDays([second,third,first,first,entry('invalid','bad')])).toEqual([first,third]);
 expect(reconcileGameDays([third,first,second])).toEqual([first,third]);
 expect(hasGameCellOnDay([second],new Date(2026,10,15,12))).toBe(true);
 expect(hasGameCellOnDay([second],new Date(2026,10,16,0))).toBe(false);
 expect(hasGameCellOnDay([entry('invalid','bad')],new Date())).toBe(false);
});
it('keeps midnight and repeated DST hours on the local calendar rather than elapsed 24-hour windows',()=>{
 const before=entry('before',new Date(2026,9,25,0,30).toISOString());
 const repeated=entry('repeated',new Date(2026,9,25,2,30).toISOString(),'hive_builder_tap');
 const late=entry('late',new Date(2026,9,25,23,59).toISOString(),'capybee_rpg');
 const next=entry('next',new Date(2026,9,26,0).toISOString());
 expect(reconcileGameDays([before,repeated,late,next])).toEqual([before,next]);
 expect(hasGameCellOnDay([late],new Date(2026,9,26,0))).toBe(false);
});
it('re-evaluates history in a changed timezone, without a persisted day key',()=>{
 // Different explicit offsets describe instants: the host's current local zone
 // always decides whether they share a displayed calendar date.
 const a=entry('a','2026-03-29T00:30:00+01:00'),b=entry('b','2026-03-29T03:30:00+02:00');
 const expected=new Date(a.completedAt).getDate()===new Date(b.completedAt).getDate()?1:2;
 expect(reconcileGameDays([a,b])).toHaveLength(expected);
});
