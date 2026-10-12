package com.capybee.engine.pack.json;

import com.capybee.engine.pack.*;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

final class ManifestParser {
    private static final String FILE = "pack.json";
    private final PackValidationContext context;

    ManifestParser(PackValidationContext context) { this.context = context; }

    ParsedManifest parse(JsonNode root) {
        if (!object(root, "")) return null;
        unknown(root, "", "schemaVersion", "id", "version", "name", "description", "locales", "defaultLocale",
                "features", "startInputs", "events", "contentPolicy", "files");
        String id = text(root, "id", "", PackId::isValid);
        String version = text(root, "version", "", value -> PackVersion.tryParse(value).isPresent());
        String name = text(root, "name", "", value -> !value.isBlank() && value.length() <= 80);
        String description = null;
        if (root.has("description")) {
            description = text(root, "description", "", value -> value.length() <= 300);
        }
        List<String> localeValues = strings(root.get("locales"), "/locales", LocaleTag::isValid, 1, true);
        String defaultLocale = text(root, "defaultLocale", "", LocaleTag::isValid);
        Set<PackFeature> features = features(root.get("features"));
        List<StartInputDef> inputs = inputs(root.get("startInputs"));
        List<EventDef> events = root.has("events") ? events(root.get("events")) : List.of();
        ContentPolicy policy = policy(root.get("contentPolicy"));
        List<String> files = strings(root.get("files"), "/files", value -> true, 0, false);
        return new ParsedManifest(root.get("schemaVersion").intValue(), id == null ? null : new PackId(id),
            version == null ? null : PackVersion.tryParse(version).orElseThrow(), name, description,
            localeValues.stream().map(LocaleTag::new).toList(), defaultLocale == null ? null : new LocaleTag(defaultLocale),
                features, inputs, events, policy, files);
    }

        record ParsedManifest(int schemaVersion, PackId id, PackVersion version, String name, String description,
                  List<LocaleTag> locales, LocaleTag defaultLocale, Set<PackFeature> features,
                  List<StartInputDef> startInputs, List<EventDef> events,
                  ContentPolicy contentPolicy, List<String> files) {
        ParsedManifest {
            locales = List.copyOf(locales);
            features = Set.copyOf(features);
            startInputs = List.copyOf(startInputs);
            events = List.copyOf(events);
            files = List.copyOf(files);
        }

        PackManifest manifest() {
            return new PackManifest(schemaVersion, id, version, name, description, locales, defaultLocale,
                features, startInputs, events, contentPolicy, files);
        }
        }

    private Set<PackFeature> features(JsonNode node) {
        Set<PackFeature> features = new LinkedHashSet<>();
        if (!array(node, "/features")) return features;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/features/" + index;
            JsonNode value = node.get(index);
            if (!value.isString()) {
                invalid(pointer);
                continue;
            }
            try {
                if (!features.add(PackFeature.valueOf(value.asString()))) invalid(pointer);
            } catch (IllegalArgumentException exception) {
                invalid(pointer);
            }
        }
        return features;
    }

    private List<StartInputDef> inputs(JsonNode node) {
        List<StartInputDef> inputs = new ArrayList<>();
        if (!array(node, "/startInputs")) return inputs;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/startInputs/" + index;
            JsonNode input = node.get(index);
            if (!object(input, pointer)) continue;
            String kind = text(input, "kind", pointer, Set.of("ENUM", "ENUM_SET", "FREE_TEXT")::contains);
            unknown(input, pointer, "id", "kind", "required", "labelKey",
                    "ENUM_SET".equals(kind) ? "maxItems" : "FREE_TEXT".equals(kind) ? "maxChars" : "values",
                    "FREE_TEXT".equals(kind) ? "maxChars" : "values");
            String id = text(input, "id", pointer, PackId::isValid);
            boolean required = bool(input, "required", pointer);
            String label = text(input, "labelKey", pointer, ManifestParser::validKey);
            if ("ENUM".equals(kind) || "ENUM_SET".equals(kind)) {
                List<String> values = strings(input.get("values"), pointer + "/values", PackId::isValid, 2, true);
                if ("ENUM_SET".equals(kind)) {
                    int maxItems = integer(input, "maxItems", pointer, 1, values.size());
                    if (id != null && label != null) inputs.add(new StartInputDef.EnumSetInput(id, required, label, values, maxItems));
                } else if (id != null && label != null) {
                    inputs.add(new StartInputDef.EnumInput(id, required, label, values));
                }
            } else if ("FREE_TEXT".equals(kind)) {
                int maxChars = integer(input, "maxChars", pointer, 1, PackLayout.MAX_FREE_TEXT_CHARS);
                if (id != null && label != null) inputs.add(new StartInputDef.FreeTextInput(id, required, label, maxChars));
            }
        }
        return inputs;
    }

    private List<EventDef> events(JsonNode node) {
        List<EventDef> events = new ArrayList<>();
        if (!array(node, "/events")) return events;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/events/" + index;
            JsonNode event = node.get(index);
            if (!object(event, pointer)) continue;
            unknown(event, pointer, "id", "payload");
            String id = text(event, "id", pointer, value -> value.length() <= 64
                    && value.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+"));
            JsonNode payload = event.get("payload");
            List<EventDef.PayloadField> fields = new ArrayList<>();
            Set<String> names = new HashSet<>();
            if (array(payload, pointer + "/payload")) {
                if (payload.size() > 8) invalid(pointer + "/payload");
                for (int fieldIndex = 0; fieldIndex < payload.size(); fieldIndex++) {
                    JsonNode field = payload.get(fieldIndex);
                    String fieldPointer = pointer + "/payload/" + fieldIndex;
                    if (!object(field, fieldPointer)) continue;
                    unknown(field, fieldPointer, "name", "type");
                    String name = text(field, "name", fieldPointer, PackId::isValid);
                    String type = text(field, "type", fieldPointer, Set.of("STRING", "INT", "BOOL", "REF_ID")::contains);
                    if (name != null && !names.add(name)) invalid(fieldPointer + "/name");
                    if (name != null && type != null) fields.add(new EventDef.PayloadField(name, EventDef.PayloadType.valueOf(type)));
                }
            }
            if (id != null) events.add(new EventDef(id, fields));
        }
        return events;
    }

    private ContentPolicy policy(JsonNode node) {
        if (!object(node, "/contentPolicy")) return null;
        unknown(node, "/contentPolicy", "freeTextAllowed", "audience", "forbiddenTags");
        boolean allowed = bool(node, "freeTextAllowed", "/contentPolicy");
        ContentPolicy.Audience audience = null;
        if (node.has("audience")) {
            JsonNode value = node.get("audience");
            if (object(value, "/contentPolicy/audience")) {
                unknown(value, "/contentPolicy/audience", "minAge", "maxAge");
                int min = integer(value, "minAge", "/contentPolicy/audience", 1, 99);
                int max = integer(value, "maxAge", "/contentPolicy/audience", 1, 99);
                if (min > max) invalid("/contentPolicy/audience/maxAge");
                audience = new ContentPolicy.Audience(min, max);
            }
        }
        List<String> tags = node.has("forbiddenTags")
                ? strings(node.get("forbiddenTags"), "/contentPolicy/forbiddenTags", PackId::isValid, 0, false) : List.of();
        return new ContentPolicy(allowed, audience, tags);
    }

    private List<String> strings(JsonNode node, String pointer, Predicate<String> format, int min, boolean unique) {
        List<String> values = new ArrayList<>();
        if (!array(node, pointer)) return values;
        if (node.size() < min) invalid(pointer);
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode value = node.get(index);
            if (!value.isString() || !format.test(value.asString())) {
                invalid(pointer + "/" + index);
            } else {
                String text = value.asString();
                if (unique && !seen.add(text)) invalid(pointer + "/" + index);
                values.add(text);
            }
        }
        return values;
    }

    private String text(JsonNode node, String field, String pointer, Predicate<String> format) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || !format.test(value.asString())) {
            invalid(pointer + "/" + field);
            return null;
        }
        return value.asString();
    }

    private boolean bool(JsonNode node, String field, String pointer) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            invalid(pointer + "/" + field);
            return false;
        }
        return value.booleanValue();
    }

    private int integer(JsonNode node, String field, String pointer, int min, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < min || value.intValue() > max) {
            invalid(pointer + "/" + field);
            return 0;
        }
        return value.intValue();
    }

    private boolean object(JsonNode node, String pointer) {
        if (node == null || !node.isObject()) {
            invalid(pointer);
            return false;
        }
        return true;
    }

    private boolean array(JsonNode node, String pointer) {
        if (node == null || !node.isArray()) {
            invalid(pointer);
            return false;
        }
        return true;
    }

    private void unknown(JsonNode node, String pointer, String... allowed) {
        Set<String> known = new HashSet<>(List.of(allowed));
        for (var entry : node.properties()) {
            if (!known.contains(entry.getKey())) {
                context.warn("PACK_UNKNOWN_FIELD", FILE, pointer + "/" + JsonDocuments.pointerToken(entry.getKey()),
                        "Unknown manifest field");
            }
        }
    }

    private void invalid(String pointer) {
        context.error("PACK_FIELD_INVALID", FILE, pointer, "Missing, malformed or out-of-range field");
    }

    static boolean validKey(String value) {
        return value.length() <= 80 && value.matches("[a-z][a-zA-Z0-9._-]*");
    }
}