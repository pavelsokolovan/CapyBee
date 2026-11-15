package com.capybee.engine.content;

import java.util.List;

/** Trusted optional Pack presentation. Never a source of mechanics or generated markup. */
public record PresentationDefinition(int catalogVersion, String playerAssetId,
                                     List<CharacterDef> characters, List<SceneArt> scenes, List<PlaceArt> places) {
    public PresentationDefinition {
        characters = List.copyOf(characters); scenes = List.copyOf(scenes); places = List.copyOf(places);
    }
    public record CharacterDef(String id, String slotId, String nameKey, String descriptionKey, String traitKey, String needKey,
                               String assetId, List<String> sceneIds, String recruitedFlag) {
        public CharacterDef { sceneIds = List.copyOf(sceneIds); }
    }
    public record SceneArt(String sceneId, String backdropAssetId) {}
    public record PlaceArt(String placeId, String terrainAssetId) {}
}
