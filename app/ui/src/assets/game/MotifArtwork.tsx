import type {MotifId} from './catalog';
import {palette as p} from './catalog';

export function MotifArtwork({id}: {id: MotifId}) {
  return <g stroke={p.ink} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
    {id === 'motif.lantern' && <>
      <circle cx="50" cy="53" r="40" fill="#FAE8B4" stroke="none"/>
      <path d="M39 25v-7q11-15 22 0v7" fill="none"/>
      <path d="m31 31 7-8h24l7 8-5 47H36Z" fill="#B99669"/>
      <path d="M39 35h22l-3 35H42Z" fill="#FFE5A0"/>
      <path d="M50 41v20" stroke="#A67424"/><path d="M33 79h34"/>
    </>}
    {id === 'motif.reed' && <>
      <path d="M38 87 34 22m20 65 6-67m-6 53q18-24 29-21M38 65Q18 42 14 47" fill="none" stroke={p.muted}/>
      <rect x="28" y="12" width="12" height="25" rx="6" fill={p.earth}/><rect x="55" y="10" width="12" height="26" rx="6" fill={p.earth}/>
      <path d="M35 81Q10 66 15 55q20 4 20 26m21-3q26-25 28-16-9 17-28 16" fill={p.sage} stroke="none"/>
    </>}
    {id === 'motif.rest-leaf' && <>
      <path d="M19 76Q10 20 82 19 92 82 25 82Z" fill={p.sage}/>
      <path d="m20 84 51-51m-24 24-2-18m13 7 16 1" fill="none" stroke={p.muted}/>
    </>}
  </g>;
}
