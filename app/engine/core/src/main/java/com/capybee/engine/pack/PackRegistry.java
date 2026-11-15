package com.capybee.engine.pack;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PackRegistry {
    private final Map<PackId, Pack> packs;
    private final Map<String, Pack> versions;

    public PackRegistry(Collection<Pack> packs) {
        Map<PackId, Pack> indexed = new LinkedHashMap<>();
        Map<String,Pack> retained=new LinkedHashMap<>();
        for (Pack pack : packs) {
            if (retained.putIfAbsent(pack.manifest().id().value()+":"+pack.manifest().version(),pack) != null) {
                throw new IllegalArgumentException("Duplicate pack id: " + pack.manifest().id());
            }
            indexed.merge(pack.manifest().id(),pack,(a,b)->a.manifest().version().compareTo(b.manifest().version())>=0?a:b);
        }
        this.versions=Collections.unmodifiableMap(retained);
        this.packs = Collections.unmodifiableMap(indexed);
    }

    public Optional<Pack> find(PackId id) { return Optional.ofNullable(packs.get(id)); }
    public Optional<Pack> find(PackId id,PackVersion version){return Optional.ofNullable(versions.get(id.value()+":"+version));}
    public Pack require(PackId id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown pack id: " + id));
    }
    public Collection<Pack> all() { return packs.values(); }
}