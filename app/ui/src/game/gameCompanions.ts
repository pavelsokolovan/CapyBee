import type {GameView, GameViewV2} from './gameApi';
import {resolveAsset, type CharacterId, type Expression} from '../assets/game/catalog';

type PublicCharacter = GameViewV2['characters'][number];
export type PresentedCharacter = PublicCharacter & {
  assetId: CharacterId; expression: Expression; trait: string | null; need: string | null; joined: boolean;
};

/** Read-only public projection. This never infers a flag or creates recruitment state. */
export function gameCompanions(view: GameView) {
  if (!('characters' in view) || view.presentation?.schemaVersion !== 1) return null;
  const publicById = new Map<string, PublicCharacter>();
  for (const c of view.characters) if (!publicById.has(c.id)) publicById.set(c.id, c);
  const partyIds = new Set(view.presentation.party), known = new Map<string, PresentedCharacter>();
  for (const art of view.presentation.characters) {
    const character = publicById.get(art.id);
    if (!character || known.has(art.id)) continue;
    known.set(art.id, {...character, assetId: resolveAsset('character', art.assetId)!, expression: art.expression,
      trait: art.trait, need: art.need, joined: art.recruited === true && partyIds.has(art.id)});
  }
  return {
    discovered: [...publicById.values()], known: [...known.values()],
    current: [...known.values()].filter(c => c.inCurrentScene),
    party: [...partyIds].flatMap(id => {const c = known.get(id); return c?.joined ? [c] : [];}),
  };
}
