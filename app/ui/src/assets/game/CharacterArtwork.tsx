import type {CharacterId, CharacterVariant, Expression} from './catalog';
import {palette as p} from './catalog';

function CapyFace({expression, marker}: {expression: Expression; marker: boolean}) {
  return <g stroke={p.ink} strokeWidth="2.5" strokeLinecap="round" fill="none">
    {expression === 'pleased' ? <path d="M52 68q4-5 8 0m25 0q4-5 8 0"/> :
      <g fill={p.ink} stroke="none"><ellipse cx="56" cy="67" rx="3" ry={expression === 'attentive' ? 4 : 3}/><ellipse cx="89" cy="67" rx="3" ry={expression === 'attentive' ? 4 : 3}/></g>}
    {expression === 'attentive' && <path d="m51 57 9-2m24 0 9 2"/>}
    <rect x="57" y="77" width="67" height="29" rx="12" fill="#DAB28A"/>
    <ellipse cx="108" cy="86" rx="7" ry="4" fill={p.ink} stroke="none"/>
    {!marker && <path d="M109 91v6q-7 5-14 0M45 48l3 3m57-3 3-3"/>}
  </g>;
}
function MothWing() {
  return <path d="M72 73C45 21 12 30 18 77q1 24 44 34-42 13-27 38 19 25 43-17Z"/>;
}

function Face({expression, muzzle = false}: {expression: Expression; muzzle?: boolean}) {
  return <g stroke={p.ink} strokeWidth="2.5" strokeLinecap="round" fill="none">
    {expression === 'pleased' ? <><path d="M56 72q4-5 8 0M88 72q4-5 8 0" /></> :
      <g fill={p.ink} stroke="none"><ellipse cx="60" cy="72" rx="3" ry={expression === 'attentive' ? 4 : 3}/><ellipse cx="92" cy="72" rx="3" ry={expression === 'attentive' ? 4 : 3}/></g>}
    {expression === 'attentive' && <path d="m55 61 9-2m24 0 9 2"/>}
    {muzzle ? <><ellipse cx="76" cy="90" rx="22" ry="16" fill="#DAB28A"/><path d="m72 85 8 0m-4 1v7m-7 3q7 5 14 0"/></> :
      <path d={expression === 'pleased' ? 'M69 87q7 8 14 0' : 'M71 88q5 3 10 0'}/>}
  </g>;
}

/** Fixed SVG geometry: no paths, colors or styling accepted from narrative content. */
export function CharacterArtwork({id, variant, expression}: {id: CharacterId; variant: CharacterVariant; expression: Expression}) {
  const portrait = variant === 'portrait', marker = variant === 'marker';
  const effectiveExpression = marker ? 'neutral' : expression;
  return <g stroke={p.ink} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round">
    {portrait && <circle cx="80" cy="78" r="67" fill={p.cream} stroke="none"/>}
    {id === 'character.capy' && <>
      {!portrait && <g fill={p.earth}>
        <ellipse cx="80" cy="125" rx="47" ry="36"/>
        <rect x="43" y="142" width="20" height="23" rx="9"/><rect x="96" y="142" width="20" height="23" rx="9"/>
      </g>}
      <g fill={p.earth}><circle cx="43" cy="44" r="12"/><circle cx="108" cy="44" r="12"/>
        <rect x="33" y="43" width="88" height="67" rx="27"/></g>
      <path d="M44 102q31 16 65 0l-1 13q-34 15-65-2Z" fill={p.honey}/>
      {!portrait && <path d="m97 115 16 6-8 25-15-5Z" fill={p.honey}/>}
      <CapyFace expression={effectiveExpression} marker={marker}/>
    </>}
    {id === 'character.borys-beaver' && <>
      {!portrait && <><path d="M100 128q45-41 45-6t-38 33Z" fill="#A67C57"/><path d="m117 122 16 14m-8-21 14 14m-24 7 13-15" fill="none" stroke={p.muted} strokeWidth="2"/>
        <ellipse cx="75" cy="129" rx="40" ry="33" fill="#AD7C55"/><path d="M46 151v11h18m28-10v10h18" fill="none"/></>}
      <g fill="#AD7C55"><circle cx="43" cy="46" r="13"/><circle cx="109" cy="46" r="13"/><rect x="32" y="44" width="89" height="67" rx="29"/></g>
      <Face expression={effectiveExpression} muzzle/>
      <path d="M69 103h14v10H69Z" fill={p.paper}/><path d="M76 104v8" strokeWidth="1.5"/>
    </>}
    {id === 'character.mela-bee' && <>
      <g fill="#E6EEDE"><ellipse cx="43" cy="96" rx="23" ry="35" transform="rotate(-24 43 96)"/><ellipse cx="114" cy="96" rx="23" ry="35" transform="rotate(24 114 96)"/></g>
      {!portrait && <><ellipse cx="78" cy="126" rx="34" ry="35" fill={p.honey}/><path d="M47 121q30 15 62 0m-59 22q28 12 56 0" stroke={p.ink} strokeWidth="10" fill="none"/>
        <path d="m64 157-3 7m31-7 3 7" fill="none"/></>}
      <path d="m62 44-9-21m39 21 10-21" fill="none"/><g fill={p.ink}><circle cx="51" cy="21" r="4"/><circle cx="104" cy="21" r="4"/></g>
      <ellipse cx="77" cy="73" rx="37" ry="34" fill={p.honey}/><Face expression={effectiveExpression}/>
    </>}
    {id === 'character.luna-moth' && <>
      <g fill={p.lilac}><MothWing/><g transform="translate(156 0) scale(-1 1)"><MothWing/></g></g>
      <g stroke="none" fill="#E5DDED"><path d="M58 74 28 52q-7 24 24 36Z"/><path d="m100 74 28-22q7 24-24 36Z"/></g>
      {!portrait && <ellipse cx="78" cy="122" rx="17" ry="33" fill="#D6C697"/>}
      <path d="m65 43-8-18m34 18 8-18" fill="none"/><ellipse cx="77" cy="72" rx="30" ry="29" fill="#E3D4AC"/>
      <Face expression={effectiveExpression}/>
    </>}
    {id === 'character.abstract-guide' && <>
      <path d={portrait ? 'M35 94Q20 44 60 40q12-22 32 0 42 0 30 54-35 26-87 0Z' : 'M31 132Q18 63 58 51q14-22 34 0 47 7 34 81-15 33-48 23-31 9-47-23Z'} fill={p.sage}/>
      <Face expression={effectiveExpression}/>
      {!portrait && <path d="M55 135q22 8 45 0" fill="none" stroke={p.muted} strokeWidth="2"/>}
    </>}
  </g>;
}
