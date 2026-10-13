package com.capybee.engine.api;

import java.util.List;

public record PlacesView(String currentPlaceId, List<PlaceView> places, List<PlaceLinkView> links,
                         List<String> availableMoves, List<String> visitedPlaceIds, String layoutKind) {
    public PlacesView {
        places = List.copyOf(places);
        links = List.copyOf(links);
        availableMoves = List.copyOf(availableMoves);
        visitedPlaceIds = List.copyOf(visitedPlaceIds);
    }
}
