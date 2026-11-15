package com.capybee.engine.api;

import com.capybee.engine.content.PresentationDefinition;
import java.util.*;

/** Pure projection of accepted mechanics and entered scenes; never a disclosure of future content. */
final class PresentationProjector {
    private PresentationProjector() {}
    record Projection(List<CharacterItem> characters, PresentationView presentation) {
        Projection {characters = List.copyOf(characters);}
    }
    static Projection project(PresentationDefinition definition, String currentScene, List<String> visited,
                              Map<String, Boolean> flags, Map<String, String> strings, List<CharacterItem> existing,
                              Set<String> publicPlaces) {
        if (definition == null) return new Projection(existing, null);
        var cast = new LinkedHashMap<String, CharacterItem>(); existing.forEach(c -> cast.put(c.id(), c));
        var art = new ArrayList<PresentationView.CharacterArt>(); var party = new ArrayList<String>();
        for (var c : definition.characters()) {
            boolean here = c.sceneIds().contains(currentScene);
            if (!here && c.sceneIds().stream().noneMatch(visited::contains)) continue;
            var accepted = existing.stream().filter(member -> c.slotId() != null ? c.slotId().equals(member.slotId()) : c.id().equals(member.id())).findFirst().orElse(null);
            String publicId = accepted == null ? c.id() : accepted.id();
            cast.putIfAbsent(publicId, new CharacterItem(publicId, publicId, strings.get(c.nameKey()), strings.get(c.descriptionKey()), here));
            // Preserve accepted generated names/descriptions but keep scene presence tied to this accepted scene.
            var member = cast.get(publicId);
            cast.put(publicId, new CharacterItem(member.id(), member.slotId(), member.name(), member.description(), here));
            boolean recruited = c.recruitedFlag() != null && Boolean.TRUE.equals(flags.get(c.recruitedFlag()));
            art.add(new PresentationView.CharacterArt(publicId, c.assetId(), c.traitKey() == null ? null : strings.get(c.traitKey()),
                    c.needKey() == null ? null : strings.get(c.needKey()), "neutral", recruited));
            if (recruited) party.add(publicId);
        }
        String backdrop = definition.scenes().stream().filter(s -> s.sceneId().equals(currentScene))
                .map(PresentationDefinition.SceneArt::backdropAssetId).findFirst().orElse(null);
        var places = definition.places().stream().filter(p -> publicPlaces.contains(p.placeId()))
                .map(p -> new PresentationView.PlaceArt(p.placeId(), p.terrainAssetId())).toList();
        return new Projection(new ArrayList<>(cast.values()), new PresentationView(1, definition.catalogVersion(),
                definition.playerAssetId(), backdrop, places, art, party));
    }
}
