package com.capybee.engine.content.json;

import com.capybee.engine.content.PresentationDefinition;
import com.capybee.engine.pack.*;
import com.capybee.engine.rules.json.RuleStrings;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/** Closed authored metadata schema; asset vocabulary remains Pack-owned. */
public final class PresentationParser implements SectionValidator {
    private static final String FILE = "presentation.json", ASSETS = "assets/catalog.json";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final Pack pack;
    private final PackValidationContext context;
    private final Map<String, String> assetKinds = new LinkedHashMap<>();
    private final Map<LocaleTag, Map<String, String>> localizedStrings = new HashMap<>();

    public PresentationParser() { this(null, null); }
    private PresentationParser(Pack pack, PackValidationContext context) { this.pack = pack; this.context = context; }
    @Override public void validate(Pack pack, PackValidationContext context) { new PresentationParser(pack, context).read(); }
    public static PresentationDefinition parse(Pack pack) {
        var context = new PackValidationContext();
        var result = new PresentationParser(pack, context).read();
        if (context.hasErrors()) throw new IllegalArgumentException("PRESENTATION_INVALID");
        return result;
    }

    private PresentationDefinition read() {
        if (pack.file(FILE).isEmpty()) return null; // Legacy ASSETS declarations remain valid.
        if (!pack.manifest().features().contains(PackFeature.ASSETS)) error(FILE, "", "PRESENTATION_FEATURE_REQUIRED");
        JsonNode assets = document(ASSETS), root = document(FILE);
        if (assets == null || root == null) return null;
        fields(assets, ASSETS, "", "schemaVersion", "catalogVersion", "assets");
        version(assets, ASSETS); int catalogVersion = positive(assets.get("catalogVersion"), ASSETS, "/catalogVersion");
        for (JsonNode asset : items(assets.get("assets"), ASSETS, "/assets", 256)) {
            fields(asset, ASSETS, "/assets", "id", "kind");
            String id = required(asset, "id", ASSETS, "/assets"), kind = required(asset, "kind", ASSETS, "/assets");
            if (id != null && !id.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) error(ASSETS, "/assets/id", "PRESENTATION_ASSET_INVALID");
            if (kind != null && !Set.of("CHARACTER", "TERRAIN", "BACKDROP").contains(kind)) error(ASSETS, "/assets/kind", "PRESENTATION_ASSET_INVALID");
            if (id != null && kind != null && assetKinds.putIfAbsent(id, kind) != null) error(ASSETS, "/assets/id", "PRESENTATION_DUPLICATE");
        }
        fields(root, FILE, "", "schemaVersion", "playerAssetId", "characters", "scenes", "places"); version(root, FILE);
        String player = optional(root, "playerAssetId", ""); asset(player, "CHARACTER", "/playerAssetId");
        var characters = new ArrayList<PresentationDefinition.CharacterDef>();
        var ids = new HashSet<String>(); var slots = new HashSet<String>();
        for (JsonNode c : items(root.get("characters"), FILE, "/characters", 32)) {
            String pointer = "/characters/" + characters.size();
            fields(c, FILE, pointer, "id", "slotId", "nameKey", "descriptionKey", "traitKey", "needKey", "assetId", "sceneIds", "recruitedFlag");
            String id = required(c, "id", FILE, pointer), name = key(c, "nameKey", pointer, true, 128),
                    description = key(c, "descriptionKey", pointer, true, 1000), trait = key(c, "traitKey", pointer, false, 512),
                    need = key(c, "needKey", pointer, false, 512), art = required(c, "assetId", FILE, pointer), flag = optional(c, "recruitedFlag", pointer);
            String slot = optional(c, "slotId", pointer);
            unique(ids, id, pointer + "/id"); unique(slots, slot, pointer + "/slotId"); asset(art, "CHARACTER", pointer + "/assetId");
            if (slot != null) {
                boolean found = false;
                try {
                    if (pack.manifest().features().contains(PackFeature.GENERATION)) found =
                        com.capybee.engine.generation.json.GenerationCatalogParser.parse(pack).templates().stream()
                            .filter(t -> t.stage() == com.capybee.engine.generation.GenerationCatalog.Stage.CAST)
                            .anyMatch(t -> t.actorSlots().contains(slot));
                } catch (RuntimeException ignored) { /* Other validators also report the invalid catalog. */ }
                if (!found) error(FILE, pointer + "/slotId", "PRESENTATION_SLOT_INVALID");
            }
            if (flag != null) context.references().reference("flag", flag, FILE, pointer + "/recruitedFlag");
            var scenes = new ArrayList<String>(); var seen = new HashSet<String>();
            for (JsonNode s : items(c.get("sceneIds"), FILE, pointer + "/sceneIds", 128)) {
                String scene = scalar(s, FILE, pointer + "/sceneIds");
                unique(seen, scene, pointer + "/sceneIds"); sceneRef(scene, pointer + "/sceneIds"); if (scene != null) scenes.add(scene);
            }
            if (scenes.isEmpty()) error(FILE, pointer + "/sceneIds", "PRESENTATION_FIELD_INVALID");
            characters.add(new PresentationDefinition.CharacterDef(id, slot, name, description, trait, need, art, scenes, flag));
        }
        var scenes = new ArrayList<PresentationDefinition.SceneArt>(); ids.clear();
        for (JsonNode s : items(root.get("scenes"), FILE, "/scenes", 128)) {
            fields(s, FILE, "/scenes", "sceneId", "backdropAssetId");
            String scene = required(s, "sceneId", FILE, "/scenes"), art = required(s, "backdropAssetId", FILE, "/scenes");
            unique(ids, scene, "/scenes/sceneId"); sceneRef(scene, "/scenes/sceneId"); asset(art, "BACKDROP", "/scenes/backdropAssetId");
            scenes.add(new PresentationDefinition.SceneArt(scene, art));
        }
        var places = new ArrayList<PresentationDefinition.PlaceArt>(); ids.clear();
        for (JsonNode p : items(root.get("places"), FILE, "/places", 128)) {
            fields(p, FILE, "/places", "placeId", "terrainAssetId");
            String place = required(p, "placeId", FILE, "/places"), art = required(p, "terrainAssetId", FILE, "/places");
            unique(ids, place, "/places/placeId"); asset(art, "TERRAIN", "/places/terrainAssetId");
            if (place != null) context.references().reference("place", place, FILE, "/places/placeId");
            places.add(new PresentationDefinition.PlaceArt(place, art));
        }
        return new PresentationDefinition(catalogVersion, player, characters, scenes, places);
    }
    private JsonNode document(String file) {
        String raw = pack.file(file).orElse(null);
        if (raw == null || !pack.manifest().files().contains(file)) { error(file, "", "PRESENTATION_FILE_MISSING"); return null; }
        try { var root = JSON.readTree(raw); if (root != null && root.isObject()) return root; }
        catch (RuntimeException ignored) { /* No prose in validation diagnostics. */ }
        error(file, "", "PRESENTATION_FIELD_INVALID"); return null;
    }
    private void version(JsonNode node, String file) {
        if (positive(node.get("schemaVersion"), file, "/schemaVersion") != 1) error(file, "/schemaVersion", "PRESENTATION_SCHEMA_UNSUPPORTED");
    }
    private int positive(JsonNode n, String file, String pointer) {
        if (n != null && n.isIntegralNumber() && n.canConvertToInt() && n.intValue() > 0 && n.intValue() <= 1_000_000) return n.intValue();
        error(file, pointer, "PRESENTATION_FIELD_INVALID"); return 0;
    }
    private void fields(JsonNode node, String file, String pointer, String... allowed) {
        if (node == null || !node.isObject()) { error(file, pointer, "PRESENTATION_FIELD_INVALID"); return; }
        var keys = Set.of(allowed);
        node.properties().forEach(e -> {if (!keys.contains(e.getKey())) error(file, pointer, "PRESENTATION_FIELD_INVALID");});
    }
    private List<JsonNode> items(JsonNode node, String file, String pointer, int max) {
        if (node == null || !node.isArray() || node.size() > max) {error(file, pointer, "PRESENTATION_FIELD_INVALID"); return List.of();}
        var out = new ArrayList<JsonNode>(); node.forEach(out::add); return out;
    }
    private String scalar(JsonNode node, String file, String pointer) {
        if (node != null && node.isString() && node.asString().length() <= 128 && node.asString().matches("[A-Za-z0-9][A-Za-z0-9_.:-]*")) return node.asString();
        error(file, pointer, "PRESENTATION_FIELD_INVALID"); return null;
    }
    private String required(JsonNode node, String field, String file, String pointer) { return scalar(node.get(field), file, pointer + "/" + field); }
    private String optional(JsonNode node, String field, String pointer) { return node.has(field) ? required(node, field, FILE, pointer) : null; }
    private void unique(Set<String> ids, String id, String pointer) {if (id != null && !ids.add(id)) error(FILE, pointer, "PRESENTATION_DUPLICATE");}
    private void asset(String id, String kind, String pointer) {
        if (id != null && !kind.equals(assetKinds.get(id))) error(FILE, pointer, "PRESENTATION_ASSET_INVALID");
    }
    private void sceneRef(String id, String pointer) {
        if (id != null) for (LocaleTag locale : pack.manifest().locales()) context.references().reference("scene:" + locale.value(), id, FILE, pointer);
    }
    private String key(JsonNode node, String field, String pointer, boolean required, int max) {
        String key = required ? required(node, field, FILE, pointer) : optional(node, field, pointer);
        if (key != null) for (LocaleTag locale : pack.manifest().locales()) {
            context.references().reference("stringKey:" + locale.value(), key, FILE, pointer + "/" + field);
            String value = localizedStrings.computeIfAbsent(locale, l -> RuleStrings.load(pack, l)).get(key);
            if (value != null && (value.isBlank() || value.codePointCount(0, value.length()) > max)) error(FILE, pointer + "/" + field, "PRESENTATION_FIELD_INVALID");
        }
        return key;
    }
    private void error(String file, String pointer, String code) {context.error(code, file, pointer, "Invalid presentation metadata");}
}
