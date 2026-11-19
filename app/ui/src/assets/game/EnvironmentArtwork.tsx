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
  if (id === 'backdrop.note-clearing') {
    return <>
      <path d="M0 0H640V360H0Z" fill="#f8d18b"/>
      <circle cx="475" cy="80" r="42" fill="#ffebb0"/>
      <path d="M0 170Q150 78 318 156T640 130V360H0Z" fill="#acba83"/>
      <path d="M0 228Q122 176 341 224T640 202V360H0Z" fill="#718b67"/>
      <path d="M0 314Q113 281 267 315T640 293V360H0Z" fill="#d4b985"/>
      <g transform="translate(77 50) scale(2.15)"><Hive/></g>
      <g transform="translate(438 228) rotate(-12)" stroke="#59442e" strokeWidth="4" strokeLinejoin="round">
        <rect width="83" height="55" rx="5" fill="#fff5d9"/>
        <path d="m2 4 39 28L81 4" fill="none"/>
        <circle cx="41" cy="31" r="7" fill="#ba5140"/>
      </g>
    </>;
  }
  if (id === 'backdrop.note-meadow') {
    return <>
      <path d="M0 0H640V360H0Z" fill="#cfdfed"/>
      <path d="M0 188Q153 87 293 165T640 110V360H0Z" fill="#90b496"/>
      <path d="M0 257Q166 167 322 233T640 190V360H0Z" fill="#5d8d75"/>
      <path d="M0 297Q172 229 327 284T640 254V360H0Z" fill="#8fb18a"/>
      <path d="M168 360Q259 291 341 277Q307 301 316 360Z" fill="#e5cc9f"/>
      {[36, 90, 154, 466, 521, 580, 615].map((x, i) => {
        const y = 248 + (i * 31) % 75;
        return <g key={x} transform={`translate(${x} ${y})`}>
          <path d="M0 2q-4 19 0 30m0-10 10-6" stroke="#34584f" strokeWidth="3" fill="none"/>
          <path d="M0 4C-21 8-17-14-3-7C3-23 19-11 7 0C24 9 2 21 0 4Z" fill={i % 2 ? '#f4c77f' : '#efd3eb'}/>
          <circle r="4" fill="#ab6b47"/>
        </g>;
      })}
    </>;
  }
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
