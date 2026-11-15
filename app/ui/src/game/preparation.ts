import type { PackPreparationRequest, DynamicStartRequest, GameCapabilities, GamePackInfoDto, GameStartRequest, GeneratorProfile, SetupInput } from './gameApi';

export type Adventure = {kind:'DYNAMIC'; profile:GeneratorProfile} | {kind:'PACK'; pack:GamePackInfoDto};
export function transientPack(game:Adventure):boolean {return game.kind==='PACK' && game.pack.setup?.transientTextSupported===true && typeof game.pack.setup.preparationFingerprint==='string';}
export type Answers = Record<string, string[]>;
export function inputsOf(game:Adventure):SetupInput[] { return game.kind==='DYNAMIC' ? game.profile.inputs : game.pack.setup?.inputs ?? []; }
export function identityOf(game:Adventure) { return game.kind==='DYNAMIC' ? game.profile.id : game.pack.id; }
export function titleOf(game:Adventure) { return game.kind==='DYNAMIC' ? game.profile.title : game.pack.setup?.title ?? ''; }
export function localesOf(game:Adventure) { return game.kind==='DYNAMIC' ? game.profile.supportedLocales : game.pack.locales; }
export function effective(input:SetupInput, answers:Answers) { return answers[input.id] ?? input.defaults; }
export function codePoints(value:string) { return Array.from(value).length; }
export function compatible(game:Adventure):boolean {
  if(game.kind==='PACK' && game.pack.setup?.schemaVersion!==1 || game.kind==='DYNAMIC' && game.profile.setupSchemaVersion!==1)return false;
  const inputs=inputsOf(game), ids=new Set(inputs.map(i=>i.id));
  if(ids.size!==inputs.length)return false;
  const steps=game.kind==='DYNAMIC'?game.profile.steps:game.pack.setup?.steps??[];
  if(inputs.some(i=>!steps.some(s=>s.inputIds.includes(i.id))) || steps.some(s=>s.inputIds.some(id=>!ids.has(id))))return false;
  return inputs.every(i=>['ENUM','ENUM_SET','FREE_TEXT'].includes(i.kind) && typeof i.label==='string'
    && Number.isSafeInteger(i.maxItems) && i.maxItems>=1 && i.maxItems<=4
    && (i.kind!=='FREE_TEXT' || (!i.required && i.id==='story-wish' && game.kind==='DYNAMIC' && i.maxCodePoints!=null && i.maxCodePoints>0 && i.maxCodePoints<=1000)
      || game.kind==='PACK' && !i.required)
    && i.defaults.every(v=>i.options.some(o=>o.id===v)))
    && (game.kind==='PACK' || game.profile.steps.every(s=>s.inputIds.every(id=>ids.has(id))));
}
export function validation(game:Adventure, answers:Answers, wish:string):string[] {
  if(!compatible(game))return ['metadata'];
  const errors=inputsOf(game).filter(i=>{
    if(i.kind==='FREE_TEXT')return (game.kind==='DYNAMIC'||transientPack(game)) && codePoints(wish)>(i.maxCodePoints ?? 0);
    const values=effective(i,answers);
    return i.required && values.length===0 || values.length>i.maxItems || new Set(values).size!==values.length || values.some(v=>!i.options.some(o=>o.id===v));
  }).map(i=>i.id);
  return errors;
}
export function initialAnswers(game:Adventure, shellLocale:string):Answers {
  const locale=localesOf(game).includes(shellLocale) ? shellLocale : game.kind==='DYNAMIC' ? game.profile.defaultLocale : game.pack.defaultLocale;
  return {'game-locale':[locale]};
}
/** Public preset state only. Never pass private text to a persistence helper. */
export function compatibleAnswers(game:Adventure, answers:Answers):Answers {
  const result:Answers={};
  for(const i of inputsOf(game))if(i.kind!=='FREE_TEXT' && answers[i.id])result[i.id]=answers[i.id].filter(v=>i.options.some(o=>o.id===v)).slice(0,i.maxItems);
  if(answers['game-locale']?.some(v=>localesOf(game).includes(v)))result['game-locale']=answers['game-locale'];
  return result;
}
export function startBody(game:Adventure, answers:Answers, wish:string, caps:GameCapabilities,
  id:string, generated:boolean, replacement?:string):DynamicStartRequest|GameStartRequest|PackPreparationRequest {
  if(validation(game,answers,wish).length)throw new Error('PREPARATION_INVALID');
  const locale=answers['game-locale']?.[0];
  if(!localesOf(game).includes(locale))throw new Error('PREPARATION_INVALID');
  if(game.kind==='PACK') {
    if(generated && transientPack(game)){
      const selections:Answers={};for(const i of inputsOf(game))if(i.kind!=='FREE_TEXT')selections[i.id]=effective(i,answers);
      return {requestId:id,packId:game.pack.id,packVersion:game.pack.version,packFingerprint:game.pack.setup!.preparationFingerprint!,locale,selections,
        ...(wish.trim()?{suggestion:wish}:{}),disclosureRevision:caps.disclosureRevision,confirmed:true,...(replacement?{expectedCurrentRunId:replacement}:{})};
    }
    const startInputs:Record<string,string>={};
    for(const i of inputsOf(game))if(i.kind!=='FREE_TEXT')startInputs[i.id]=effective(i,answers).join(',');
    return {id,packId:game.pack.id,locale,generated,startInputs};
  }
  const values=(key:string)=>{const input=inputsOf(game).find(i=>i.id===key);if(!input)throw new Error('PREPARATION_INVALID');return effective(input,answers)[0];};
  const selections:Answers={};
  for(const i of inputsOf(game))if(!['length','challenge','game-locale','story-wish'].includes(i.id) && answers[i.id]!==undefined)selections[i.id]=answers[i.id];
  return {requestId:id,profileId:game.profile.id,profileVersion:game.profile.version,profileFingerprint:game.profile.fingerprint,
    locale,lengthPreset:values('length'),challengePreset:values('challenge'),selections,
    ...(wish.trim() ? {suggestion:wish} : {}),disclosureRevision:caps.disclosureRevision,confirmed:true,
    ...(replacement ? {expectedCurrentRunId:replacement} : {})};
}
