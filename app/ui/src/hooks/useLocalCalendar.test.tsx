import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {expect,it,vi} from 'vitest';
import {useLocalCalendar} from './useLocalCalendar';
(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT:boolean}).IS_REACT_ACT_ENVIRONMENT=true;
it('refreshes exactly at local midnight and rechecks clock/zone on focus',async()=>{
 vi.useFakeTimers();vi.setSystemTime(new Date(2026,10,15,23,59,59));
 const host=document.createElement('div'),root=createRoot(host);
 function Clock(){const now=useLocalCalendar();return <p>{now.getDate()}</p>;}
 try{
  await act(async()=>root.render(<Clock/>));expect(host.textContent).toBe('15');
  await act(async()=>vi.advanceTimersByTime(1002));expect(host.textContent).toBe('16');
  vi.setSystemTime(new Date(2026,10,17,1));await act(async()=>window.dispatchEvent(new Event('focus')));expect(host.textContent).toBe('17');
 }finally{await act(async()=>root.unmount());vi.useRealTimers();}
});
