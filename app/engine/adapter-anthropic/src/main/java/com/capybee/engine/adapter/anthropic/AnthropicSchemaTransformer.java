package com.capybee.engine.adapter.anthropic;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Produces the bounded JSON Schema subset accepted by Anthropic structured outputs. */
final class AnthropicSchemaTransformer {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> UNSUPPORTED = Set.of(
            "minimum", "maximum", "multipleOf", "minLength", "maxLength", "maxItems", "uniqueItems", "not");

    private AnthropicSchemaTransformer() {}

    static ObjectNode transform(JsonNode supplied) {
        if (!(supplied instanceof ObjectNode object)) throw new IllegalArgumentException("Schema must be an object");
        ObjectNode root = object.deepCopy();
        int conditionDepth = 4;
        JsonNode effective = root.get("x-capybee-effectiveConstraints");
        if (effective != null && effective.get("maxConditionDepth") != null
                && effective.get("maxConditionDepth").canConvertToInt()) {
            conditionDepth = Math.max(1, Math.min(4, effective.get("maxConditionDepth").intValue()));
        }
        flattenAllOf(root, root);
        expandRecursiveConditions(root, conditionDepth);
        pruneUnusedDefinitions(root);
        normalize(root, true);
        return root;
    }

    private static void pruneUnusedDefinitions(ObjectNode root) {
        if (!(root.get("$defs") instanceof ObjectNode definitions)) return;
        Set<String> reachable = new LinkedHashSet<>();
        for (var entry : root.properties()) {
            if (!entry.getKey().equals("$defs")) collectDefinitionReferences(entry.getValue(), definitions, reachable);
        }
        for (String name : new ArrayList<>(definitions.propertyNames())) {
            if (!reachable.contains(name)) definitions.remove(name);
        }
    }

    private static void collectDefinitionReferences(JsonNode node, ObjectNode definitions, Set<String> reachable) {
        if (node instanceof ArrayNode array) {
            for (JsonNode child : array) collectDefinitionReferences(child, definitions, reachable);
            return;
        }
        if (!(node instanceof ObjectNode object)) return;
        JsonNode reference = object.get("$ref");
        if (reference != null && reference.isString()) {
            String prefix = "#/$defs/";
            String value = reference.asString();
            if (value.startsWith(prefix)) {
                String name = value.substring(prefix.length());
                if (!name.contains("/") && reachable.add(name) && definitions.get(name) != null)
                    collectDefinitionReferences(definitions.get(name), definitions, reachable);
            }
        }
        for (var entry : object.properties()) {
            if (!entry.getKey().equals("$ref")) collectDefinitionReferences(entry.getValue(), definitions, reachable);
        }
    }

    private static void flattenAllOf(JsonNode node, ObjectNode root) {
        if (node instanceof ArrayNode array) {
            for (JsonNode child : array) flattenAllOf(child, root);
            return;
        }
        if (!(node instanceof ObjectNode object)) return;
        JsonNode allOf = object.get("allOf");
        if (allOf instanceof ArrayNode members) {
            ObjectNode merged = JSON.createObjectNode();
            for (var entry : new ArrayList<>(object.properties())) {
                if (!entry.getKey().equals("allOf")) merged.set(entry.getKey(), entry.getValue().deepCopy());
            }
            for (JsonNode member : members) {
                JsonNode source = member;
                if (member instanceof ObjectNode item && item.get("$ref") != null) {
                    source = resolve(root, item.get("$ref").asString());
                }
                if (!(source instanceof ObjectNode schema)) throw new IllegalArgumentException("Unsupported allOf member");
                ObjectNode copy = schema.deepCopy();
                flattenAllOf(copy, root);
                merge(merged, copy);
            }
            object.removeAll();
            object.setAll(merged);
        }
        for (var entry : new ArrayList<>(object.properties())) flattenAllOf(entry.getValue(), root);
    }

    private static JsonNode resolve(ObjectNode root, String reference) {
        if (reference == null || !reference.startsWith("#/")) throw new IllegalArgumentException("External schema reference");
        JsonNode resolved = root.at(reference.substring(1));
        if (resolved == null || resolved.isMissingNode()) throw new IllegalArgumentException("Missing schema reference");
        return resolved;
    }

    private static void merge(ObjectNode target, ObjectNode source) {
        for (var entry : source.properties()) {
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (name.equals("properties") && value instanceof ObjectNode properties) {
                ObjectNode destination = target.get("properties") instanceof ObjectNode existing
                        ? existing : target.putObject("properties");
                properties.properties().forEach(property -> destination.set(property.getKey(), property.getValue().deepCopy()));
            } else if (name.equals("required") && value instanceof ArrayNode required) {
                LinkedHashSet<String> values = new LinkedHashSet<>();
                if (target.get("required") instanceof ArrayNode existing)
                    existing.forEach(item -> values.add(item.asString()));
                required.forEach(item -> values.add(item.asString()));
                ArrayNode destination = target.putArray("required");
                values.forEach(destination::add);
            } else if (name.equals("additionalProperties")) {
                target.put("additionalProperties", false);
            } else {
                target.set(name, value.deepCopy());
            }
        }
    }

    private static void expandRecursiveConditions(ObjectNode root, int maximumDepth) {
        if (!(root.get("$defs") instanceof ObjectNode definitions)
                || !(definitions.get("condition") instanceof ObjectNode condition)) return;
        JsonNode union = condition.get("oneOf") != null ? condition.get("oneOf") : condition.get("anyOf");
        if (!(union instanceof ArrayNode branches)) return;
        List<JsonNode> leafBranches = new ArrayList<>();
        boolean composite = false;
        boolean negation = false;
        for (JsonNode branch : branches) {
            String reference = branch.get("$ref") == null ? null : branch.get("$ref").asString();
            if ("#/$defs/compositeCondition".equals(reference)) composite = true;
            else if ("#/$defs/notCondition".equals(reference)) negation = true;
            else leafBranches.add(branch.deepCopy());
        }
        if (leafBranches.isEmpty()) throw new IllegalArgumentException("Condition schema has no finite branch");
        for (int depth = 1; depth <= maximumDepth; depth++) {
            ObjectNode expanded = JSON.createObjectNode();
            ArrayNode options = expanded.putArray("anyOf");
            leafBranches.forEach(branch -> options.add(branch.deepCopy()));
            if (depth > 1 && composite && definitions.get("compositeCondition") instanceof ObjectNode schema) {
                options.add(replaceReference(schema.deepCopy(), "#/$defs/condition", "#/$defs/conditionDepth" + (depth - 1)));
            }
            if (depth > 1 && negation && definitions.get("notCondition") instanceof ObjectNode schema) {
                options.add(replaceReference(schema.deepCopy(), "#/$defs/condition", "#/$defs/conditionDepth" + (depth - 1)));
            }
            definitions.set("conditionDepth" + depth, expanded);
        }
        definitions.set("condition", definitions.get("conditionDepth" + maximumDepth).deepCopy());
        definitions.remove("compositeCondition");
        definitions.remove("notCondition");
    }

    private static JsonNode replaceReference(JsonNode node, String from, String to) {
        if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++) array.set(index, replaceReference(array.get(index), from, to));
        } else if (node instanceof ObjectNode object) {
            for (var entry : new ArrayList<>(object.properties())) {
                if (entry.getKey().equals("$ref") && entry.getValue().isString()
                        && entry.getValue().asString().equals(from)) object.put("$ref", to);
                else object.set(entry.getKey(), replaceReference(entry.getValue(), from, to));
            }
        }
        return node;
    }

    private static void normalize(JsonNode node, boolean root) {
        if (node instanceof ArrayNode array) {
            for (JsonNode child : array) normalize(child, false);
            return;
        }
        if (!(node instanceof ObjectNode object)) return;
        if (root) {
            object.remove("$schema");
            object.remove("$id");
        }
        for (String name : UNSUPPORTED) object.remove(name);
        for (String name : new ArrayList<>(object.propertyNames())) if (name.startsWith("x-")) object.remove(name);
        JsonNode oneOf = object.remove("oneOf");
        if (oneOf != null) object.set("anyOf", oneOf);
        JsonNode prefixItems = object.remove("prefixItems");
        if (prefixItems instanceof ArrayNode tuple && object.get("items") == null) {
            ObjectNode items = object.putObject("items");
            ArrayNode alternatives = items.putArray("anyOf");
            for (JsonNode item : tuple) alternatives.add(item.deepCopy());
        }
        JsonNode minimumItems = object.get("minItems");
        if (minimumItems != null && minimumItems.canConvertToInt() && minimumItems.intValue() > 1)
            object.put("minItems", 1);
        JsonNode type = object.get("type");
        if ((type != null && type.isString() && "object".equals(type.asString())) || object.get("properties") != null)
            object.put("additionalProperties", false);
        for (var entry : new ArrayList<>(object.properties())) normalize(entry.getValue(), false);
    }
}
