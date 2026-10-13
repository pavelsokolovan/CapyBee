package com.capybee.engine.places;

import java.util.List;

public record PlaceMap(int schemaVersion, String startPlace, PlaceLayoutKind layoutKind,
                       List<PlaceDef> places, List<PlaceLinkDef> links) {
    public PlaceMap {
        places = List.copyOf(places);
        links = List.copyOf(links);
    }
}
