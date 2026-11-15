import {useId} from 'react';
import type {BackdropId, TerrainId} from './catalog';
import {hexPoints, palette as p} from './catalog';
import {MotifArtwork} from './MotifArtwork';

function Tree({willow = false}: {willow?: boolean}) {
  return <g stroke={p.muted} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
    <path d="M47 105V47m0 25L30 53m17 10 18-18" fill="none"/>
    <path d="M11 50Q0 23 28 20 39-2 61 14 89 7 94 36q8 28-26 24-32 14-57-10Z" fill={p.sage}/>
    {willow && <path d="M19 48q-5 25 0 35m15-32q-4 29 0 42m32-41q5 23 0 39m15-39q8 15 3 28" fill="none"/>}
  </g>;
}
function Workshop() {
  return <g stroke={p.muted} strokeWidth="3" strokeLinejoin="round" fill="#D9BD91">
    <path d="M12 50h89v12H12Zm9 12v34m69-34v34"/><path d="m34 47 21-22 15 22Z" fill={p.sage}/>
    <path d="m65 43 28-10m-27 3 26-10" fill="none"/>
  </g>;
}
function Bridge() {
  return <g stroke={p.muted} strokeWidth="3" strokeLinejoin="round" fill="#D9BD91">
    <path d="M8 68Q50 43 101 68l-2 13Q50 60 10 81Z"/><path d="M14 62V41m80 21V41M14 45Q51 24 94 45" fill="none"/>
    <path d="m31 60 3 13m16-18v14m18-13-2 14m17-10-3 13" fill="none" strokeWidth="2"/>
  </g>;
}
function Hive() {
  return <g stroke={p.muted} strokeWidth="3" strokeLinejoin="round" fill="#E6C384">
    <path d="M12 90Q5 62 28 49 21 28 48 25q27-1 28 24 26 10 20 41Z"/>
    <path d="M21 58h64M13 75h82" fill="none"/><path d="M44 90V73q9-15 18 0v17Z" fill="#AC8B5E"/>
  </g>;
}

export function TerrainArtwork({id}: {id: TerrainId}) {
  const clipId = useId();
  return <>
    <defs><clipPath id={clipId}><polygon points={hexPoints}/></clipPath></defs>
    <g clipPath={`url(#${clipId})`}>
      <polygon points={hexPoints} fill={id === 'terrain.water' || id === 'terrain.crossing' ? p.water : '#DCE6CD'}/>
      <path d="M-5 112q36-26 66-5t70 0v40H-5Z" fill="#C4D3B3"/>
      {id === 'terrain.meadow' && <path d="m18 48-3-10m3 10 7-6m77 60 3-11m-3 11-7-5" stroke={p.muted} strokeWidth="2" fill="none"/>}
      {id === 'terrain.water' && <><path d="M5 100q18-8 34 0m45-49q17-7 32 0" stroke="#67868A" strokeWidth="2" fill="none"/><g transform="translate(83 20) scale(.3)"><MotifArtwork id="motif.reed"/></g></>}
      {id === 'terrain.willow' && <g transform="translate(74 14) scale(.42)"><Tree willow/></g>}
      {id === 'terrain.workshop' && <g transform="translate(68 13) scale(.43)"><Workshop/></g>}
      {id === 'terrain.lantern' && <g transform="translate(73 14) scale(.4)"><MotifArtwork id="motif.lantern"/></g>}
      {id === 'terrain.crossing' && <g transform="translate(67 15) scale(.42)"><Bridge/></g>}
    </g>
    <polygon points={hexPoints} fill="none" stroke={p.muted} strokeWidth="3"/>
  </>;
}

export function BackdropArtwork({id}: {id: BackdropId}) {
  const water = id === 'backdrop.reed-bank' || id === 'backdrop.small-crossing';
  return <>
    <g data-layer="background"><path d="M0 0h640v360H0Z" fill={p.cream}/>
      <circle cx="490" cy="65" r="27" fill="#F7E6BB"/>
      <path d="M0 143Q110 80 224 137t196-14q112-49 220 17v220H0Z" fill="#D8E1CC"/>
    </g>
    <g data-layer="middle-distance">
      <path d="M0 195q102-41 206-7t214-6q116-30 220 13v165H0Z" fill="#C5D5BA"/>
      {(id === 'backdrop.lantern-grove' || id === 'backdrop.willow-glen') && <>
        <g transform="translate(100 38) scale(1.4)"><Tree willow={id === 'backdrop.willow-glen'}/></g>
        <g transform="translate(433 48) scale(1.25)"><Tree willow={id === 'backdrop.willow-glen'}/></g>
      </>}
      {id === 'backdrop.hive-clearing' && <g transform="translate(120 66) scale(1.4)"><Hive/></g>}
      {id === 'backdrop.lantern-grove' && <g transform="translate(277 74)"><MotifArtwork id="motif.lantern"/></g>}
      {id === 'backdrop.hive-workshop' && <g transform="translate(263 92) scale(1.2)"><Workshop/></g>}
      {water && <path d="M0 197q130-22 260 0t380-5v168H0Z" fill={p.water}/>}
      {id === 'backdrop.small-crossing' && <g transform="translate(258 112) scale(1.15)"><Bridge/></g>}
      {id === 'backdrop.reed-bank' && <g transform="translate(116 113)"><MotifArtwork id="motif.reed"/></g>}
    </g>
    <g data-layer="ground"><path d="M0 282q128-24 229 2t411-12v88H0Z" fill="#E5E5CB"/>
      <path d="M0 325q134-21 222 3t418-8v40H0Z" fill="#D5DDBF"/>
      {id === 'backdrop.quiet-meadow' && <path d="m69 269-7-20m7 20 13-12m481 12 7-20m-7 20-13-12" stroke={p.muted} strokeWidth="3" fill="none"/>}
    </g>
  </>;
}
