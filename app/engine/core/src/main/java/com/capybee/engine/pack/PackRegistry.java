package com.capybee.engine.pack;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PackRegistry {
    private final Map<PackId, Pack> packs;

    public PackRegistry(Collection<Pack> packs) {
        Map<PackId, Pack> indexed = new LinkedHashMap<>();
        for (Pack pack : packs) {
            if (indexed.putIfAbsent(pack.manifest().id(), pack) != null) {
                throw new IllegalArgumentException("Duplicate pack id: " + pack.manifest().id());
            }
        }
        this.packs = Collections.unmodifiableMap(indexed);
    }

    public Optional<Pack> find(PackId id) { return Optional.ofNullable(packs.get(id)); }
    public Pack require(PackId id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown pack id: " + id));
    }
    public Collection<Pack> all() { return packs.values(); }
}