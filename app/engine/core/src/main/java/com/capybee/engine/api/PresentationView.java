package com.capybee.engine.api;

import java.util.List;

/** Public, discovered-only enrichment of a text-first View. No private references or rules. */
public record PresentationView(int schemaVersion, int catalogVersion, String playerAssetId, String backdropAssetId,
                               List<PlaceArt> places, List<CharacterArt> characters, List<String> party) {
    public PresentationView {
        places = List.copyOf(places); characters = List.copyOf(characters); party = List.copyOf(party);
    }
    public record PlaceArt(String placeId, String terrainAssetId) {}
    public record CharacterArt(String id, String assetId, String trait, String need, String expression, boolean recruited) {}
}
