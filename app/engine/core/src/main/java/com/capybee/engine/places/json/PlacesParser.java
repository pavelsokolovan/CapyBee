package com.capybee.engine.places.json;

import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackFeature;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.places.PlaceDef;
import com.capybee.engine.places.PlaceEntry;
import com.capybee.engine.places.PlaceNavigation;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.json.RulesParser;
import com.capybee.engine.rules.json.RuleStrings;
import com.capybee.engine.places.PlaceLayoutKind;
import com.capybee.engine.places.PlaceLinkDef;
import com.capybee.engine.places.PlaceMap;
import com.capybee.engine.places.PlacePosition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class PlacesParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILE = "places.json";

    private PlacesParser() {}

    public static PlaceMap parse(Pack pack) {
        return parse(pack, null);
    }

    static PlaceMap validate(Pack pack, PackValidationContext context) {
        return parse(pack, context);
    }

    private static PlaceMap parse(Pack pack, PackValidationContext context) {
        boolean enabled = pack.manifest().features().contains(PackFeature.PLACES);
        boolean listed = pack.manifest().files().contains(FILE);
        String json = pack.file(FILE).orElse(null);
        if (!enabled) {
            if (listed || json != null) error(context, "PLACES_FEATURE_REQUIRED", FILE, "",
                    "places.json requires the PLACES feature");
            return null;
        }
        if (json == null) return null;

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception exception) {
            error(context, "PLACES_FIELD_INVALID", FILE, "", "Invalid places JSON");
            return null;
        }
        if (root == null || !root.isObject()) {
            error(context, "PLACES_FIELD_INVALID", FILE, "", "Expected places object");
            return null;
        }
        Integer schemaVersion = integer(root.get("schemaVersion"), context, "/schemaVersion");
        boolean entries = Integer.valueOf(2).equals(schemaVersion);
        unknown(root, context, "", entries ? new String[]{"schemaVersion", "startPlace", "layout", "places", "links", "navigation"} : new String[]{"schemaVersion", "startPlace", "layout", "places", "links"});
        if (schemaVersion != null && schemaVersion != 1 && schemaVersion != 2) invalid(context, "/schemaVersion", "Expected schema version 1 or 2");
        String startPlace = id(root.get("startPlace"), context, "/startPlace");
        if (startPlace != null) reference(context, startPlace, "/startPlace");
        PlaceLayoutKind layoutKind = parseLayout(root.get("layout"), context);
        List<PlaceDef> places = parsePlaces(root.get("places"), pack, context, entries);
        List<PlaceLinkDef> links = parseLinks(root.get("links"), context);
        if (entries && context != null) {
            // Legacy unknown fields remain warnings; schema 2 is a closed mechanics contract.
            rejectUnknownV2(root, context);
        }
        PlaceNavigation navigation = entries ? parseNavigation(root.get("navigation"), pack, context) : null;
        if (startPlace != null) checkReachability(startPlace, places, links, context);
        if (schemaVersion == null || (schemaVersion != 1 && schemaVersion != 2) || startPlace == null || layoutKind == null
                || places.isEmpty() || root.get("links") == null || entries && navigation == null) return null;
        return new PlaceMap(schemaVersion, startPlace, layoutKind, places, links, navigation);
    }

    private static PlaceLayoutKind parseLayout(JsonNode node, PackValidationContext context) {
        if (node == null || !node.isObject()) {
            invalid(context, "/layout", "Expected layout object");
            return null;
        }
        unknown(node, context, "/layout", "kind");
        JsonNode kindNode = node.get("kind");
        if (kindNode == null || !kindNode.isString()) {
            invalid(context, "/layout/kind", "Expected layout kind");
            return null;
        }
        try {
            return PlaceLayoutKind.valueOf(kindNode.asString());
        } catch (IllegalArgumentException exception) {
            invalid(context, "/layout/kind", "Unknown layout kind");
            return null;
        }
    }

    private static List<PlaceDef> parsePlaces(JsonNode node, Pack pack, PackValidationContext context, boolean entries) {
        List<PlaceDef> places = new ArrayList<>();
        if (node == null || !node.isArray() || node.size() < 1 || node.size() > 128) {
            invalid(context, "/places", "Expected 1-128 places");
            return places;
        }
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/places/" + index;
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                invalid(context, pointer, "Expected place object");
                continue;
            }
            unknown(item, context, pointer, entries ? new String[]{"id", "labelKey", "descriptionKey", "position", "entry"} : new String[]{"id", "labelKey", "descriptionKey", "position"});
            PlaceEntry entry = entries ? parseEntry(item.get("entry"), pack, context, pointer + "/entry") : null;
            String id = id(item.get("id"), context, pointer + "/id");
            String labelKey = stringKey(item.get("labelKey"), context, pointer + "/labelKey");
            String descriptionKey = item.has("descriptionKey")
                    ? stringKey(item.get("descriptionKey"), context, pointer + "/descriptionKey") : null;
            if (id != null) define(context, id, pointer + "/id");
            if (labelKey != null) {
                for (LocaleTag locale : pack.manifest().locales()) {
                    stringReference(context, labelKey, locale, pointer + "/labelKey");
                }
            }
            if (descriptionKey != null) {
                for (LocaleTag locale : pack.manifest().locales()) {
                    stringReference(context, descriptionKey, locale, pointer + "/descriptionKey");
                }
            }
            PlacePosition position = parsePosition(item.get("position"), context, pointer + "/position");
            if (id != null && labelKey != null && (!item.has("descriptionKey") || descriptionKey != null)) {
                places.add(new PlaceDef(id, labelKey, descriptionKey, position, entry));
            }
        }
        return places;
    }

    private static void rejectUnknownV2(JsonNode root, PackValidationContext context) {
        closed(root, context, "", "schemaVersion", "startPlace", "layout", "navigation", "places", "links");
        closed(root.path("layout"), context, "/layout", "kind");
        closed(root.path("navigation"), context, "/navigation", "allowedSceneIds", "unavailableHintKey");
        int i = 0;
        for (JsonNode place : root.path("places")) {
            String p = "/places/" + i++;
            closed(place, context, p, "id", "labelKey", "descriptionKey", "position", "entry");
            closed(place.path("entry"), context, p + "/entry", "sceneId", "requires", "unmetHintKey");
            closed(place.path("position"), context, p + "/position", "x", "y");
        }
        i = 0;
        for (JsonNode link : root.path("links")) closed(link, context, "/links/" + i++, "from", "to", "bidirectional");
    }

    private static void closed(JsonNode node, PackValidationContext context, String pointer, String... allowed) {
        for (var field : node.properties()) if (!Set.of(allowed).contains(field.getKey()))
            invalid(context, pointer + "/" + token(field.getKey()), "Unknown mechanics field");
    }

    private static PlaceNavigation parseNavigation(JsonNode node, Pack pack, PackValidationContext context) {
        String pointer = "/navigation";
        if (node == null || !node.isObject()) {invalid(context, pointer, "Expected navigation object"); return null;}
        unknown(node, context, pointer, "allowedSceneIds", "unavailableHintKey");
        JsonNode ids = node.get("allowedSceneIds");
        List<String> scenes = new ArrayList<>();
        if (ids == null || !ids.isArray() || ids.size() > 128) invalid(context, pointer, "Expected at most 128 allowed scene IDs");
        else for (int i = 0; i < ids.size(); i++) {
            String scene = id(ids.get(i), context, pointer + "/allowedSceneIds/" + i);
            if (scene == null) continue;
            if (scenes.contains(scene)) invalid(context, pointer, "Duplicate allowed scene ID");
            else scenes.add(scene);
            sceneReference(pack, context, scene, pointer + "/allowedSceneIds/" + i);
        }
        String hint = localizedKey(node.get("unavailableHintKey"), pack, context, pointer + "/unavailableHintKey");
        return ids == null || !ids.isArray() || ids.size() > 128 || hint == null ? null : new PlaceNavigation(scenes, hint);
    }

    private static PlaceEntry parseEntry(JsonNode node, Pack pack, PackValidationContext context, String pointer) {
        if (node == null || !node.isObject()) {invalid(context, pointer, "Expected entry object"); return null;}
        unknown(node, context, pointer, "sceneId", "requires", "unmetHintKey");
        String scene = id(node.get("sceneId"), context, pointer + "/sceneId");
        if (scene != null) sceneReference(pack, context, scene, pointer + "/sceneId");
        Condition condition = node.has("requires") ? RulesParser.parseCondition(node.get("requires"), pack, context, FILE, pointer + "/requires") : null;
        String hint = node.has("requires") ? localizedKey(node.get("unmetHintKey"), pack, context, pointer + "/unmetHintKey") : null;
        if (!node.has("requires") && node.has("unmetHintKey")) invalid(context, pointer, "Hint requires a condition");
        return scene == null ? null : new PlaceEntry(scene, condition, hint);
    }

    private static String localizedKey(JsonNode node, Pack pack, PackValidationContext context, String pointer) {
        String key = stringKey(node, context, pointer);
        if (key != null) for (LocaleTag locale : pack.manifest().locales()) {
            stringReference(context, key, locale, pointer);
            // Hint text must be present and nonblank in every declared locale.
            String value = RuleStrings.load(pack, locale).get(key);
            if (value == null || value.isBlank()) invalid(context, pointer, "Expected nonblank localized hint");
        }
        return key;
    }

    private static void sceneReference(Pack pack, PackValidationContext context, String scene, String pointer) {
        if (context != null) for (LocaleTag locale : pack.manifest().locales())
            context.references().reference("scene:" + locale.value(), scene, FILE, pointer);
    }

    private static PlacePosition parsePosition(JsonNode node, PackValidationContext context, String pointer) {
        if (node == null) return null;
        if (!node.isObject()) {
            invalid(context, pointer, "Expected position object");
            return null;
        }
        unknown(node, context, pointer, "x", "y");
        if (!node.has("x") && !node.has("y")) {
            invalid(context, pointer, "Position requires both coordinates");
            return null;
        }
        Integer x = coordinate(node.get("x"), context, pointer + "/x");
        Integer y = coordinate(node.get("y"), context, pointer + "/y");
        if ((x == null) != (y == null)) invalid(context, pointer, "Both coordinates are required together");
        return x == null || y == null ? null : new PlacePosition(x, y);
    }

    private static Integer coordinate(JsonNode node, PackValidationContext context, String pointer) {
        if (node == null) return null;
        Integer value = integer(node, context, pointer);
        if (value != null && (value < -1000 || value > 1000)) {
            invalid(context, pointer, "Coordinate must be between -1000 and 1000");
            return null;
        }
        return value;
    }

    private static List<PlaceLinkDef> parseLinks(JsonNode node, PackValidationContext context) {
        List<PlaceLinkDef> links = new ArrayList<>();
        if (node == null || !node.isArray() || node.size() > 512) {
            invalid(context, "/links", "Expected 0-512 links");
            return links;
        }
        Set<String> pairs = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/links/" + index;
            JsonNode item = node.get(index);
            if (item == null || !item.isObject()) {
                invalid(context, pointer, "Expected link object");
                continue;
            }
            unknown(item, context, pointer, "from", "to", "bidirectional");
            String from = id(item.get("from"), context, pointer + "/from");
            String to = id(item.get("to"), context, pointer + "/to");
            JsonNode bidirectionalNode = item.get("bidirectional");
            Boolean bidirectional = bidirectionalNode != null && bidirectionalNode.isBoolean()
                    ? bidirectionalNode.booleanValue() : null;
            if (bidirectional == null) invalid(context, pointer + "/bidirectional", "Expected boolean");
            if (from != null) reference(context, from, pointer + "/from");
            if (to != null) reference(context, to, pointer + "/to");
            if (from != null && to != null) {
                if (from.equals(to)) invalid(context, pointer, "A link cannot connect a place to itself");
                String pair = from + "\u0000" + to;
                if (!pairs.add(pair)) invalid(context, pointer, "Duplicate directed link");
            }
            if (from != null && to != null && bidirectional != null && !from.equals(to)) {
                links.add(new PlaceLinkDef(from, to, bidirectional));
            }
        }
        return links;
    }

    private static void checkReachability(String startPlace, List<PlaceDef> places,
                                          List<PlaceLinkDef> links, PackValidationContext context) {
        Set<String> known = new LinkedHashSet<>();
        places.forEach(place -> known.add(place.id()));
        if (!known.contains(startPlace)) return;
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (PlaceLinkDef link : links) {
            if (!known.contains(link.from()) || !known.contains(link.to())) continue;
            outgoing.computeIfAbsent(link.from(), ignored -> new ArrayList<>()).add(link.to());
            if (link.bidirectional()) outgoing.computeIfAbsent(link.to(), ignored -> new ArrayList<>()).add(link.from());
        }
        Set<String> reached = new HashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.add(startPlace);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (!reached.add(current)) continue;
            pending.addAll(outgoing.getOrDefault(current, List.of()));
        }
        for (PlaceDef place : places) {
            if (!reached.contains(place.id())) {
                warn(context, "PLACE_UNREACHABLE", "/places", "Place is unreachable from start: " + place.id());
            }
        }
    }

    private static String id(JsonNode node, PackValidationContext context, String pointer) {
        if (node == null || !node.isString() || !validId(node.asString())) {
            invalid(context, pointer, "Invalid place id");
            return null;
        }
        return node.asString();
    }

    private static String stringKey(JsonNode node, PackValidationContext context, String pointer) {
        if (node == null || !node.isString() || node.asString().length() > 80
                || !node.asString().matches("[a-z][a-zA-Z0-9._-]*")) {
            invalid(context, pointer, "Invalid string key");
            return null;
        }
        return node.asString();
    }

    private static Integer integer(JsonNode node, PackValidationContext context, String pointer) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            invalid(context, pointer, "Expected signed 32-bit integer");
            return null;
        }
        return node.intValue();
    }

    private static boolean validId(String value) {
        return value.length() <= 48 && value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    }

    private static void unknown(JsonNode node, PackValidationContext context, String pointer, String... fields) {
        if (context == null) return;
        Set<String> allowed = Set.of(fields);
        for (var entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) {
                warn(context, "PACK_UNKNOWN_FIELD", pointer + "/" + token(entry.getKey()), "Unknown places field");
            }
        }
    }

    private static void define(PackValidationContext context, String id, String pointer) {
        if (context != null) context.references().define("place", id, FILE, pointer);
    }

    private static void reference(PackValidationContext context, String id, String pointer) {
        if (context != null) context.references().reference("place", id, FILE, pointer);
    }

    private static void stringReference(PackValidationContext context, String key, LocaleTag locale, String pointer) {
        if (context != null) context.references().reference("stringKey:" + locale.value(), key, FILE, pointer);
    }

    private static void invalid(PackValidationContext context, String pointer, String message) {
        error(context, "PLACES_FIELD_INVALID", FILE, pointer, message);
    }

    private static void error(PackValidationContext context, String code, String file, String pointer, String message) {
        if (context != null) context.error(code, file, pointer, message);
    }

    private static void warn(PackValidationContext context, String code, String pointer, String message) {
        if (context != null) context.warn(code, FILE, pointer, message);
    }

    private static String token(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
