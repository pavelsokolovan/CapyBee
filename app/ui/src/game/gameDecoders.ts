import type { GameRunSummaryDto, GameSource, GameView, DynamicOperation, GameCapabilities, GeneratorProfile, GamePackInfoDto } from './gameApi';
import {decodePresentation} from './presentationDecoder';
export class GameContractError extends Error { constructor() { super('GAME_CONTRACT_UNSUPPORTED'); } }
type ObjectValue = Record<string, unknown>;
export function object(value: unknown): ObjectValue {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new GameContractError();
  return value as ObjectValue;
}
export function text(value: unknown): string {
  if (typeof value !== 'string' || value.length > 65536) throw new GameContractError();
  return value;
}
export function integer(value: unknown): number {
  if (!Number.isSafeInteger(value) || (value as number) < 0) throw new GameContractError();
  return value as number;
}
function bool(value: unknown) { if (typeof value !== 'boolean') throw new GameContractError(); return value; }
function array(value: unknown): unknown[] {
  if (!Array.isArray(value) || value.length > 512) throw new GameContractError(); return value;
}
function strings(value: unknown) { return array(value).map(text); }
function member(value: unknown, choices: string[]) { const s = text(value); if (!choices.includes(s)) throw new GameContractError(); return s; }
const statuses = ['PREPARING','ACTIVE','ENDED','FAILED','SUSPENDED','CANCELLING','CANCELLED'];
export function decodeView(value: unknown, schema: unknown): GameView {
  if (schema !== 1 && schema !== 2) throw new GameContractError();
  const v = object(value); bool(v.ended);
  if(v.endReason != null) member(v.endReason,['COMPLETED','ABANDONED']);
  if(v.outcome != null)text(v.outcome);
  if (v.canAdvance !== undefined) bool(v.canAdvance);
  for (const b of array(v.blocks)) { const x=object(b); text(x.kind); text(x.text); if(x.speaker != null)text(x.speaker);if(x.actorId != null)text(x.actorId); strings(schema === 1 ? x.tags : x.refs); }
  for (const o of array(v.options)) { const x=object(o); text(x.id); text(x.label); bool(x.enabled); if(x.hint != null) text(x.hint); }
  for (const item of array(v.status)) { const x=object(item); text(x.id);text(x.label);text(x.value); }
  for (const item of array(v.objectives)) {const x=object(item);text(x.id);text(x.label);text(x.state);}
  if (v.input != null) { const x=object(v.input); member(x.mode,['NONE','CHOICE','TEXT','CHOICE_OR_TEXT']);integer(x.maxChars); }
  if (schema === 2) {
    for (const item of array(v.characters)) { const x=object(item); for(const k of ['id','slotId','name','description'])text(x[k]);bool(x.inCurrentScene); }
    if(v.story != null) {const x=object(v.story);text(x.title);text(x.premise);text(x.setting);}
    if(v.result != null) {const x=object(v.result);for(const k of ['outcomeId','label','headline','summary'])text(x[k]);member(x.resultClass,['SUCCESS','PARTIAL_SUCCESS','FAILURE']);for(const item of array(x.objectives)){const y=object(item);for(const k of ['id','label','state','note'])text(y[k]);if(y.description != null)text(y.description);}
      for(const item of array(x.facts)){const y=object(item);text(y.id);text(y.label);}
      if(x.reflection != null)text(x.reflection);}
  }
  if(v.places != null) {
    const p=object(v.places); text(p.currentPlaceId);text(p.layoutKind);strings(p.availableMoves);strings(p.visitedPlaceIds);
    for(const item of array(p.places)){const x=object(item);text(x.id);text(x.label);if(x.description!=null)text(x.description);}
    for(const item of array(p.links)){const x=object(item);text(x.from);text(x.to);bool(x.bidirectional);}
  }
  const decoded = {...v};
  if (schema === 1) delete decoded.presentation;
  else if (Object.hasOwn(v, 'presentation')) decoded.presentation = decodePresentation(v.presentation,
    new Set(array(v.characters).map(item => text(object(item).id))),
    new Set(v.places == null ? [] : array(object(v.places).places).map(item => text(object(item).id))));
  // Coordinates are optional presentation hints; malformed hints use the list.
  if(v.places != null) {
    const p=object(v.places);
    return {...decoded, places:{...p, places:array(p.places).map(item=>{
      const place=object(item);
      return {...place, x:Number.isSafeInteger(place.x)?place.x:null, y:Number.isSafeInteger(place.y)?place.y:null};
    })}} as unknown as GameView;
  }
  return decoded as unknown as GameView;
}
export function decodeSummary(value: unknown): GameRunSummaryDto {
  const v=object(value); text(v.runId);member(v.status,statuses);integer(v.revision);text(v.locale);text(v.updatedAt);
  if(v.viewSchema !== 1 && v.viewSchema !== 2) throw new GameContractError();
  let source: GameSource;
  if(v.source == null) { source={kind:'PACK',packId:text(v.packId),packVersion:text(v.packVersion)}; }
  else {
    const x=object(v.source);
    if(x.kind === 'PACK') {
      if(x.profileId != null || x.profileVersion != null || x.gameFingerprint != null)throw new GameContractError();
      source={kind:'PACK',packId:text(x.packId),packVersion:text(x.packVersion)};
      if(v.packId != null && v.packId !== source.packId || v.packVersion != null && v.packVersion !== source.packVersion)throw new GameContractError();
    } else if(x.kind === 'DYNAMIC') {
      if(x.packId != null || x.packVersion != null || v.packId != null || v.packVersion != null)throw new GameContractError();
      source={kind:'DYNAMIC',profileId:text(x.profileId),profileVersion:text(x.profileVersion),gameFingerprint:text(x.gameFingerprint)};
    } else throw new GameContractError();
  }
  return {...v, source, view:v.view == null ? null : decodeView(v.view,v.viewSchema)} as unknown as GameRunSummaryDto;
}
export function decodeOperation(value: unknown): DynamicOperation {
  const v=object(value); member(v.status,['PROCESSING','STARTED','NEEDS_INPUT','FALLBACK_OFFERED','CANCELLED','FAILED']); integer(v.retryAfterMs);
  if(v.code != null)text(v.code);
  if(v.status === 'STARTED' && v.run == null)throw new GameContractError();
  return {...v,run:v.run == null ? null : decodeSummary(v.run)} as unknown as DynamicOperation;
}
export function decodeCapabilities(value: unknown): GameCapabilities {
  const v=object(value);if(v.schemaVersion !== 1)throw new GameContractError();
  for(const k of ['clientEnabled','gameEnabled','packStartEnabled','packGenerationEnabled','dynamicStartEnabled'])bool(v[k]);
  array(v.supportedViewSchemas).forEach(integer);strings(v.viewFeatures);integer(v.disclosureRevision);member(v.dynamicCancelMode,['BEST_EFFORT']);
  return v as unknown as GameCapabilities;
}
export function decodeProfile(value: unknown): GeneratorProfile {
  const v=object(value);if(v.setupSchemaVersion !== 1)throw new GameContractError();
  for(const k of ['id','version','fingerprint','title','description','defaultLocale','metadataLocale'])text(v[k]);
  strings(v.supportedLocales);bool(v.suggestionAllowed);integer(v.maxSuggestionCodePoints);
  for(const item of array(v.steps)){const x=object(item);text(x.id);text(x.title);strings(x.inputIds);}
  for(const item of array(v.inputs)){const x=object(item);for(const k of ['id','label','help'])text(x[k]);member(x.kind,['ENUM','ENUM_SET','FREE_TEXT']);bool(x.required);integer(x.maxItems);if(x.maxCodePoints != null)integer(x.maxCodePoints);strings(x.defaults);for(const o of array(x.options)){const y=object(o);text(y.id);text(y.label);}}
  return v as unknown as GeneratorProfile;
}

export function decodePack(value:unknown):GamePackInfoDto {
 const v=object(value);text(v.id);text(v.version);strings(v.locales);text(v.defaultLocale);bool(v.generated);array(v.startInputs);
 if(v.setup!=null){
  const setup=object(v.setup);if(setup.schemaVersion!==1 || typeof setup.transientTextSupported!=='boolean' || setup.transientTextSupported && typeof setup.preparationFingerprint!=='string')throw new GameContractError();
  text(setup.title);if(setup.description!=null)text(setup.description);text(setup.metadataLocale);
  for(const item of array(setup.steps)){const x=object(item);text(x.id);text(x.title);strings(x.inputIds);}
  for(const item of array(setup.inputs)){const x=object(item);text(x.id);text(x.label);text(x.help);member(x.kind,['ENUM','ENUM_SET','FREE_TEXT']);bool(x.required);integer(x.maxItems);strings(x.defaults);
   for(const option of array(x.options)){const o=object(option);text(o.id);text(o.label);}}
 }
 return v as unknown as GamePackInfoDto;
}
