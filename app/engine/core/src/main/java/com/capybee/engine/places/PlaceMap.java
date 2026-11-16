package com.capybee.engine.places;

import java.util.List;

public record PlaceMap(int schemaVersion, String startPlace, PlaceLayoutKind layoutKind,
                       List<PlaceDef> places, List<PlaceLinkDef> links, PlaceNavigation navigation) {
    public PlaceMap(int schemaVersion, String startPlace, PlaceLayoutKind layoutKind, List<PlaceDef> places, List<PlaceLinkDef> links) {
        this(schemaVersion, startPlace, layoutKind, places, links, null);
    }
    public PlaceMap {
        places = List.copyOf(places);
        links = List.copyOf(links);
    }
}
