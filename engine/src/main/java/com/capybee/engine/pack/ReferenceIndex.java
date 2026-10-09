package com.capybee.engine.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReferenceIndex {
    private record Key(String kind, String id) {}
    private record Location(Key key, String file, String pointer) {}
    private final Map<Key, Location> definitions = new LinkedHashMap<>();
    private final List<Location> duplicates = new ArrayList<>();
    private final List<Location> references = new ArrayList<>();

    public void define(String kind, String id, String file, String pointer) {
        Location location = new Location(new Key(kind, id), file, pointer);
        if (definitions.putIfAbsent(location.key(), location) != null) duplicates.add(location);
    }

    public void reference(String kind, String id, String file, String pointer) {
        references.add(new Location(new Key(kind, id), file, pointer));
    }

    public List<PackIssue> check() {
        List<PackIssue> issues = new ArrayList<>();
        for (Location location : duplicates) issues.add(issue("PACK_DUPLICATE_ID", location));
        for (Location location : references) {
            if (!definitions.containsKey(location.key())) issues.add(issue("PACK_DANGLING_REF", location));
        }
        return List.copyOf(issues);
    }

    private PackIssue issue(String code, Location location) {
        return new PackIssue(PackIssue.Severity.ERROR, code, location.file(), location.pointer(),
                location.key().kind() + ":" + location.key().id());
    }
}