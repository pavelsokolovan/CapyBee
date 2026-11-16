import type {GameViewV2} from '../gameApi';

/** Fictional public views with opaque IDs: the renderer cannot depend on actor/Pack names. */
export function companionView(party: string[] = []): GameViewV2 {
  const characters = [
    {id: 'engine-a', name: 'Mela', description: 'A patient bee.', trait: 'Patient', need: 'A quiet place.', assetId: 'character.mela-bee' as const},
    {id: 'engine-b', name: 'Borys', description: 'A resourceful beaver.', trait: 'Resourceful', need: 'Someone to hold a reed.', assetId: 'character.borys-beaver' as const},
    {id: 'engine-c', name: 'Luna', description: 'An attentive moth.', trait: 'Attentive', need: 'A clear rest sign.', assetId: 'character.luna-moth' as const},
  ];
  return {blocks: [{kind: 'SPEECH', speaker: 'engine-a', text: 'We can take our time.', refs: []}],
    options: [{id: 'public-choice', label: 'Hold the reed', enabled: true}], input: {mode: 'CHOICE', maxChars: 0},
    status: [], objectives: [], ended: false, canAdvance: false,
    characters: characters.map(c => ({id: c.id, slotId: 'opaque-slot-' + c.id, name: c.name, description: c.description, inCurrentScene: true})),
    presentation: {schemaVersion: 1, catalogVersion: 1, playerAssetId: 'character.capy', backdropAssetId: null, places: [], party: [...party],
      characters: characters.map(c => ({id: c.id, assetId: c.assetId, trait: c.trait, need: c.need, expression: 'neutral', recruited: party.includes(c.id)}))},
  };
}
