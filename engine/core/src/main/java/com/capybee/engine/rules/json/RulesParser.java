package com.capybee.engine.rules.json;

import com.capybee.engine.pack.EventDef;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.CounterDef;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.FlagDef;
import com.capybee.engine.rules.ObjectiveDef;
import com.capybee.engine.rules.OutcomeDef;
import com.capybee.engine.rules.Rules;
import com.capybee.engine.rules.SetDef;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RulesParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RULES_FILE = "rules.json";

    private final Pack pack;
    private final PackValidationContext context;

    private RulesParser(Pack pack, PackValidationContext context) {
        this.pack = pack;
        this.context = context;
    }

    public static Rules parse(Pack pack) {
        return new RulesParser(pack, null).parseRules();
    }

    public static Rules validate(Pack pack, PackValidationContext context) {
        return new RulesParser(pack, context).parseRules();
    }

    public static Condition parseCondition(JsonNode node, Pack pack, PackValidationContext context,
                                          String file, String pointer) {
        return new RulesParser(pack, context).condition(node, file, pointer, 1, new int[1]);
    }

    public static List<Effect> parseEffects(JsonNode node, Pack pack, PackValidationContext context,
                                            String file, String pointer, boolean missingIsEmpty) {
        return new RulesParser(pack, context).effects(node, file, pointer, missingIsEmpty);
    }

    private Rules parseRules() {
        String json = pack.file(RULES_FILE).orElse(null);
        if (json == null) return Rules.empty();
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception exception) {
            error("RULES_FIELD_INVALID", RULES_FILE, "", "Invalid rules JSON");
            return Rules.empty();
        }
        if (root == null || !root.isObject()) {
            error("RULES_FIELD_INVALID", RULES_FILE, "", "Expected rules object");
            return Rules.empty();
        }
        unknown(root, RULES_FILE, "", "schemaVersion", "counters", "flags", "sets", "objectives", "outcomes", "limits");
        JsonNode schema = root.get("schemaVersion");
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt() || schema.intValue() != 1) {
            error("RULES_FIELD_INVALID", RULES_FILE, "/schemaVersion", "Expected schema version 1");
        }
        JsonNode limits = root.get("limits");
        if (limits != null && (!limits.isObject() || !limits.isEmpty())) {
            error("RULES_FIELD_INVALID", RULES_FILE, "/limits", "Reserved limits object must be empty");
        }

        List<CounterDef> counters = parseCounters(root.get("counters"));
        List<FlagDef> flags = parseFlags(root.get("flags"));
        List<SetDef> sets = parseSets(root.get("sets"));
        List<ObjectiveDef> objectives = parseObjectives(root.get("objectives"));
        List<OutcomeDef> outcomes = parseOutcomes(root.get("outcomes"));
        return new Rules(counters, flags, sets, objectives, outcomes);
    }

    private List<CounterDef> parseCounters(JsonNode node) {
        List<CounterDef> result = new ArrayList<>();
        if (!array(node, "counters", 32, true)) return result;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/counters/" + index;
            JsonNode item = node.get(index);
            if (!object(item, pointer)) continue;
            unknown(item, RULES_FILE, pointer, "id", "labelKey", "min", "max", "initial", "display", "maxDelta");
            String id = id(item.get("id"), pointer + "/id");
            String label = label(item.get("labelKey"), pointer + "/labelKey");
            Integer min = integer(item.get("min"), pointer + "/min");
            Integer max = integer(item.get("max"), pointer + "/max");
            Integer initial = integer(item.get("initial"), pointer + "/initial");
            Boolean display = bool(item.get("display"), pointer + "/display");
            Integer maxDelta = item.has("maxDelta") ? integer(item.get("maxDelta"), pointer + "/maxDelta") : null;
            if (min != null && max != null && min >= max) invalid(pointer, "Counter min must be less than max");
            if (min != null && max != null && initial != null && (initial < min || initial > max)) {
                invalid(pointer + "/initial", "Counter initial value is outside its bounds");
            }
            if (min != null && max != null && maxDelta != null
                    && (maxDelta <= 0 || (long) maxDelta > (long) max - min)) {
                invalid(pointer + "/maxDelta", "Counter maxDelta must be positive and fit its range");
            }
            if (id != null) define("counter", id, pointer + "/id");
            if (id != null && label != null && min != null && max != null && initial != null && display != null
                    && (maxDelta == null || maxDelta > 0 && (long) maxDelta <= (long) max - min)) {
                result.add(new CounterDef(id, label, min, max, initial, display, maxDelta));
            }
        }
        return result;
    }

    private List<FlagDef> parseFlags(JsonNode node) {
        List<FlagDef> result = new ArrayList<>();
        if (!array(node, "flags", 32, true)) return result;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/flags/" + index;
            JsonNode item = node.get(index);
            if (!object(item, pointer)) continue;
            unknown(item, RULES_FILE, pointer, "id", "initial");
            String id = id(item.get("id"), pointer + "/id");
            Boolean initial = item.has("initial") ? bool(item.get("initial"), pointer + "/initial") : false;
            if (id != null) define("flag", id, pointer + "/id");
            if (id != null && initial != null) result.add(new FlagDef(id, initial));
        }
        return result;
    }

    private List<SetDef> parseSets(JsonNode node) {
        List<SetDef> result = new ArrayList<>();
        if (!array(node, "sets", 16, true)) return result;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/sets/" + index;
            JsonNode item = node.get(index);
            if (!object(item, pointer)) continue;
            unknown(item, RULES_FILE, pointer, "id", "values", "initial");
            String id = id(item.get("id"), pointer + "/id");
            List<String> values = idList(item.get("values"), pointer + "/values", 1, 32);
            List<String> initial = item.has("initial")
                    ? idList(item.get("initial"), pointer + "/initial", 0, 32) : List.of();
            Set<String> allowed = new HashSet<>(values);
            for (int valueIndex = 0; valueIndex < initial.size(); valueIndex++) {
                if (!allowed.contains(initial.get(valueIndex))) invalid(pointer + "/initial/" + valueIndex, "Initial set member is not declared");
            }
            if (id != null) {
                define("set", id, pointer + "/id");
                for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                    define("setValue:" + id, values.get(valueIndex), pointer + "/values/" + valueIndex);
                }
            }
            if (id != null && values.size() >= 1 && initial.stream().allMatch(allowed::contains)) {
                result.add(new SetDef(id, values, initial));
            }
        }
        return result;
    }

    private List<ObjectiveDef> parseObjectives(JsonNode node) {
        List<ObjectiveDef> result = new ArrayList<>();
        if (!array(node, "objectives", 8, true)) return result;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/objectives/" + index;
            JsonNode item = node.get(index);
            if (!object(item, pointer)) continue;
            unknown(item, RULES_FILE, pointer, "id", "labelKey", "completeWhen", "onComplete");
            String id = id(item.get("id"), pointer + "/id");
            String label = label(item.get("labelKey"), pointer + "/labelKey");
            Condition completeWhen = condition(item.get("completeWhen"), RULES_FILE, pointer + "/completeWhen", 1, new int[1]);
            List<Effect> effects = effects(item.get("onComplete"), RULES_FILE, pointer + "/onComplete", true);
            if (id != null) define("objective", id, pointer + "/id");
            if (id != null && label != null && completeWhen != null) {
                result.add(new ObjectiveDef(id, label, completeWhen, effects));
            } else {
                invalid(pointer, "Invalid objective definition");
            }
        }
        return result;
    }

    private List<OutcomeDef> parseOutcomes(JsonNode node) {
        List<OutcomeDef> result = new ArrayList<>();
        if (!array(node, "outcomes", 16, true)) return result;
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/outcomes/" + index;
            JsonNode item = node.get(index);
            if (!object(item, pointer)) continue;
            unknown(item, RULES_FILE, pointer, "id");
            String id = id(item.get("id"), pointer + "/id");
            if (id != null) {
                define("outcome", id, pointer + "/id");
                result.add(new OutcomeDef(id));
            }
        }
        return result;
    }

    private Condition condition(JsonNode node, String file, String pointer, int depth, int[] nodes) {
        nodes[0]++;
        if (depth > 4 || nodes[0] > 64 || node == null || !node.isObject()) {
            error("CONDITION_INVALID", file, pointer, "Condition is malformed or exceeds limits");
            return null;
        }
        String type = string(node.get("type"));
        if (type == null) return conditionError(file, pointer);
        Condition result = null;
        switch (type) {
            case "COUNTER_AT_LEAST", "COUNTER_AT_MOST", "COUNTER_EQUALS" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "counter", "value");
                String counter = id(node.get("counter"), file, pointer + "/counter", "CONDITION_INVALID");
                Integer value = integer(node.get("value"), file, pointer + "/value", "CONDITION_INVALID");
                if (counter != null) reference("counter", counter, file, pointer + "/counter");
                if (counter != null && value != null) {
                    result = switch (type) {
                        case "COUNTER_AT_LEAST" -> new Condition.CounterAtLeast(counter, value);
                        case "COUNTER_AT_MOST" -> new Condition.CounterAtMost(counter, value);
                        default -> new Condition.CounterEquals(counter, value);
                    };
                }
            }
            case "FLAG_IS" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "flag", "value");
                String flag = id(node.get("flag"), file, pointer + "/flag", "CONDITION_INVALID");
                Boolean value = bool(node.get("value"), file, pointer + "/value", "CONDITION_INVALID");
                if (flag != null) reference("flag", flag, file, pointer + "/flag");
                if (flag != null && value != null) result = new Condition.FlagIs(flag, value);
            }
            case "SET_CONTAINS" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "set", "value");
                String set = id(node.get("set"), file, pointer + "/set", "CONDITION_INVALID");
                String value = id(node.get("value"), file, pointer + "/value", "CONDITION_INVALID");
                if (set != null) reference("set", set, file, pointer + "/set");
                if (set != null && value != null) reference("setValue:" + set, value, file, pointer + "/value");
                if (set != null && value != null) result = new Condition.SetContains(set, value);
            }
            case "SET_SIZE_AT_LEAST" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "set", "size");
                String set = id(node.get("set"), file, pointer + "/set", "CONDITION_INVALID");
                Integer size = integer(node.get("size"), file, pointer + "/size", "CONDITION_INVALID");
                if (set != null) reference("set", set, file, pointer + "/set");
                if (set != null && size != null && size >= 0) result = new Condition.SetSizeAtLeast(set, size);
                else if (size != null) error("CONDITION_INVALID", file, pointer + "/size", "Set size must be non-negative");
            }
            case "VISITED_SCENE" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "scene");
                String scene = id(node.get("scene"), file, pointer + "/scene", "CONDITION_INVALID");
                if (scene != null) {
                    for (LocaleTag locale : pack.manifest().locales()) reference("scene:" + locale.value(), scene, file, pointer + "/scene");
                    result = new Condition.VisitedScene(scene);
                }
            }
            case "STEP_AT_LEAST" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "step");
                Long step = longInteger(node.get("step"), file, pointer + "/step", "CONDITION_INVALID");
                if (step != null && step >= 0) result = new Condition.StepAtLeast(step);
                else if (step != null) error("CONDITION_INVALID", file, pointer + "/step", "Step must be non-negative");
            }
            case "ALL", "ANY" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "conditions");
                JsonNode children = node.get("conditions");
                if (children == null || !children.isArray() || children.size() < 1 || children.size() > 16) {
                    error("CONDITION_INVALID", file, pointer + "/conditions", "Composite needs 1-16 children");
                    break;
                }
                List<Condition> parsed = new ArrayList<>();
                for (int index = 0; index < children.size(); index++) {
                    Condition child = condition(children.get(index), file, pointer + "/conditions/" + index, depth + 1, nodes);
                    if (child != null) parsed.add(child);
                }
                if (parsed.size() == children.size()) result = type.equals("ALL")
                        ? new Condition.All(parsed) : new Condition.Any(parsed);
            }
            case "NOT" -> {
                exact(node, file, pointer, "CONDITION_INVALID", "type", "condition");
                Condition child = condition(node.get("condition"), file, pointer + "/condition", depth + 1, nodes);
                if (child != null) result = new Condition.Not(child);
            }
            default -> error("CONDITION_INVALID", file, pointer + "/type", "Unsupported condition type");
        }
        if (result == null && !hasParseIssueAt(file, pointer)) {
            error("CONDITION_INVALID", file, pointer, "Condition fields are invalid");
        }
        return result;
    }

    private List<Effect> effects(JsonNode node, String file, String pointer, boolean missingIsEmpty) {
        if (node == null && missingIsEmpty) return List.of();
        if (node == null || !node.isArray() || node.size() > 32) {
            error("EFFECT_INVALID", file, pointer, "Expected an effect list with at most 32 entries");
            return List.of();
        }
        List<Effect> result = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            Effect effect = effect(node.get(index), file, pointer + "/" + index);
            if (effect != null) result.add(effect);
        }
        return List.copyOf(result);
    }

    private Effect effect(JsonNode node, String file, String pointer) {
        if (node == null || !node.isObject()) {
            error("EFFECT_INVALID", file, pointer, "Expected effect object");
            return null;
        }
        String type = string(node.get("type"));
        if (type == null) return effectError(file, pointer);
        String[] fields = switch (type) {
            case "COUNTER_ADD" -> new String[]{"type", "counter", "delta"};
            case "COUNTER_SET" -> new String[]{"type", "counter", "value"};
            case "FLAG_SET" -> new String[]{"type", "flag", "value"};
            case "SET_ADD", "SET_REMOVE" -> new String[]{"type", "set", "value"};
            case "EMIT_EVENT" -> new String[]{"type", "event", "payload"};
            case "END_RUN" -> node.has("outcome") ? new String[]{"type", "outcome"} : new String[]{"type"};
            default -> {
                error("EFFECT_INVALID", file, pointer + "/type", "Unsupported effect type");
                yield null;
            }
        };
        if (fields == null) return null;
        exact(node, file, pointer, "EFFECT_INVALID", fields);
        return switch (type) {
            case "COUNTER_ADD" -> {
                String counter = id(node.get("counter"), file, pointer + "/counter", "EFFECT_INVALID");
                Integer delta = integer(node.get("delta"), file, pointer + "/delta", "EFFECT_INVALID");
                if (counter != null) reference("counter", counter, file, pointer + "/counter");
                if (delta != null && delta == 0) error("EFFECT_INVALID", file, pointer + "/delta", "Counter delta cannot be zero");
                yield counter != null && delta != null && delta != 0 ? new Effect.CounterAdd(counter, delta) : null;
            }
            case "COUNTER_SET" -> {
                String counter = id(node.get("counter"), file, pointer + "/counter", "EFFECT_INVALID");
                Integer value = integer(node.get("value"), file, pointer + "/value", "EFFECT_INVALID");
                if (counter != null) reference("counter", counter, file, pointer + "/counter");
                yield counter != null && value != null ? new Effect.CounterSet(counter, value) : null;
            }
            case "FLAG_SET" -> {
                String flag = id(node.get("flag"), file, pointer + "/flag", "EFFECT_INVALID");
                Boolean value = bool(node.get("value"), file, pointer + "/value", "EFFECT_INVALID");
                if (flag != null) reference("flag", flag, file, pointer + "/flag");
                yield flag != null && value != null ? new Effect.FlagSet(flag, value) : null;
            }
            case "SET_ADD", "SET_REMOVE" -> {
                String set = id(node.get("set"), file, pointer + "/set", "EFFECT_INVALID");
                String value = id(node.get("value"), file, pointer + "/value", "EFFECT_INVALID");
                if (set != null) reference("set", set, file, pointer + "/set");
                if (set != null && value != null) reference("setValue:" + set, value, file, pointer + "/value");
                yield set == null || value == null ? null : type.equals("SET_ADD")
                        ? new Effect.SetAdd(set, value) : new Effect.SetRemove(set, value);
            }
            case "EMIT_EVENT" -> parseEvent(node, file, pointer);
            case "END_RUN" -> {
                String outcome = node.has("outcome") ? id(node.get("outcome"), file, pointer + "/outcome", "EFFECT_INVALID") : null;
                if (outcome != null) reference("outcome", outcome, file, pointer + "/outcome");
                yield node.has("outcome") && outcome == null ? null : new Effect.EndRun(outcome);
            }
            default -> null;
        };
    }

    private Effect parseEvent(JsonNode node, String file, String pointer) {
        String eventId = string(node.get("event"));
        if (eventId == null || !eventId.matches("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) {
            error("EFFECT_INVALID", file, pointer + "/event", "Invalid event id");
            return null;
        }
        EventDef event = pack.manifest().events().stream().filter(item -> item.id().equals(eventId)).findFirst().orElse(null);
        if (event == null) {
            error("EVENT_UNDECLARED", file, pointer + "/event", "Event is not declared in the manifest");
            return null;
        }
        reference("event", eventId, file, pointer + "/event");
        JsonNode payload = node.get("payload");
        if (payload == null || !payload.isObject()) {
            error("EVENT_PAYLOAD_INVALID", file, pointer + "/payload", "Expected event payload object");
            return null;
        }
        Map<String, Effect.PayloadValue> values = new LinkedHashMap<>();
        Set<String> expected = new HashSet<>();
        boolean valid = true;
        for (EventDef.PayloadField field : event.payload()) {
            expected.add(field.name());
            JsonNode value = payload.get(field.name());
            Effect.PayloadValue parsed = payloadValue(value, field.type(), file, pointer + "/payload/" + field.name());
            if (parsed == null) valid = false;
            else values.put(field.name(), parsed);
        }
        for (var entry : payload.properties()) {
            if (!expected.contains(entry.getKey())) {
                error("EVENT_PAYLOAD_INVALID", file, pointer + "/payload/" + pointerToken(entry.getKey()), "Unexpected event payload field");
                valid = false;
            }
        }
        return valid ? new Effect.EmitEvent(eventId, values) : null;
    }

    private Effect.PayloadValue payloadValue(JsonNode value, EventDef.PayloadType type, String file, String pointer) {
        if (value == null || value.isNull()) {
            error("EVENT_PAYLOAD_INVALID", file, pointer, "Required event payload field is missing");
            return null;
        }
        if (value.isObject() && value.size() == 1 && value.get("counter") != null && type == EventDef.PayloadType.INT) {
            String counter = id(value.get("counter"), file, pointer + "/counter", "EVENT_PAYLOAD_INVALID");
            if (counter != null) {
                reference("counter", counter, file, pointer + "/counter");
                return new Effect.PayloadValue.CounterRef(counter);
            }
            return null;
        }
        return switch (type) {
            case STRING -> value.isString() ? new Effect.PayloadValue.StringLiteral(value.asString()) : payloadError(file, pointer);
            case INT -> value.isIntegralNumber() && value.canConvertToInt()
                    ? new Effect.PayloadValue.IntegerLiteral(value.intValue()) : payloadError(file, pointer);
            case BOOL -> value.isBoolean() ? new Effect.PayloadValue.BooleanLiteral(value.booleanValue()) : payloadError(file, pointer);
            case REF_ID -> value.isString() && validId(value.asString())
                    ? new Effect.PayloadValue.StringLiteral(value.asString()) : payloadError(file, pointer);
        };
    }

    private List<String> idList(JsonNode node, String pointer, int min, int max) {
        if (node == null || !node.isArray() || node.size() < min || node.size() > max) {
            invalid(pointer, "Expected a list within the allowed size");
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < node.size(); index++) {
            String value = id(node.get(index), pointer + "/" + index);
            if (value != null) {
                if (!unique.add(value)) invalid(pointer + "/" + index, "Duplicate list value");
                values.add(value);
            }
        }
        return values;
    }

    private boolean array(JsonNode node, String name, int max, boolean optional) {
        if (node == null && optional) return false;
        if (node == null || !node.isArray()) {
            invalid("/" + name, "Expected array");
            return false;
        }
        if (node.size() > max) invalid("/" + name, "Array exceeds limit");
        return true;
    }

    private boolean object(JsonNode node, String pointer) {
        if (node == null || !node.isObject()) {
            invalid(pointer, "Expected object");
            return false;
        }
        return true;
    }

    private String label(JsonNode node, String pointer) {
        String value = string(node);
        if (value == null || value.length() > 80 || !value.matches("[a-z][a-zA-Z0-9._-]*")) {
            invalid(pointer, "Invalid string key");
            return null;
        }
        if (value != null) {
            for (LocaleTag locale : pack.manifest().locales()) reference("stringKey:" + locale.value(), value, RULES_FILE, pointer);
        }
        return value;
    }

    private String id(JsonNode node, String pointer) {
        String value = string(node);
        if (value == null || !validId(value)) {
            invalid(pointer, "Invalid id");
            return null;
        }
        return value;
    }

    private String id(JsonNode node, String file, String pointer, String code) {
        String value = string(node);
        if (value == null || !validId(value)) {
            error(code, file, pointer, "Invalid id");
            return null;
        }
        return value;
    }

    private Integer integer(JsonNode node, String pointer) {
        return integer(node, RULES_FILE, pointer, "RULES_FIELD_INVALID");
    }

    private Integer integer(JsonNode node, String file, String pointer, String code) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            error(code, file, pointer, "Expected signed 32-bit integer");
            return null;
        }
        return node.intValue();
    }

    private Long longInteger(JsonNode node, String file, String pointer, String code) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            error(code, file, pointer, "Expected non-negative integer");
            return null;
        }
        return node.longValue();
    }

    private Boolean bool(JsonNode node, String pointer) {
        return bool(node, RULES_FILE, pointer, "RULES_FIELD_INVALID");
    }

    private Boolean bool(JsonNode node, String file, String pointer, String code) {
        if (node == null || !node.isBoolean()) {
            error(code, file, pointer, "Expected boolean");
            return null;
        }
        return node.booleanValue();
    }

    private String string(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    private void exact(JsonNode node, String file, String pointer, String code, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (var entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) error(code, file, pointer + "/" + pointerToken(entry.getKey()), "Unexpected field");
        }
        for (String field : fields) {
            if (!node.has(field)) error(code, file, pointer + "/" + field, "Required field is missing");
        }
    }

    private void unknown(JsonNode node, String file, String pointer, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (var entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) warn("PACK_UNKNOWN_FIELD", file,
                    pointer + "/" + pointerToken(entry.getKey()), "Unknown field");
        }
    }

    private void define(String kind, String id, String pointer) {
        if (context != null) context.references().define(kind, id, RULES_FILE, pointer);
    }

    private void reference(String kind, String id, String file, String pointer) {
        if (context != null) context.references().reference(kind, id, file, pointer);
    }

    private void invalid(String pointer, String message) {
        error("RULES_FIELD_INVALID", RULES_FILE, pointer, message);
    }

    private void error(String code, String file, String pointer, String message) {
        if (context != null) context.error(code, file, pointer, message);
    }

    private void warn(String code, String file, String pointer, String message) {
        if (context != null) context.warn(code, file, pointer, message);
    }

    private boolean hasParseIssueAt(String file, String pointer) {
        if (context == null) return false;
        return context.issues().stream().anyMatch(issue -> issue.file().equals(file)
                && (issue.pointer().equals(pointer) || issue.pointer().startsWith(pointer + "/")));
    }

    private Condition conditionError(String file, String pointer) {
        error("CONDITION_INVALID", file, pointer, "Condition type is missing or invalid");
        return null;
    }

    private Effect effectError(String file, String pointer) {
        error("EFFECT_INVALID", file, pointer, "Effect type is missing or invalid");
        return null;
    }

    private <T> T payloadError(String file, String pointer) {
        error("EVENT_PAYLOAD_INVALID", file, pointer, "Payload value does not match declared type");
        return null;
    }

    private static boolean validId(String value) {
        return value.length() <= 48 && value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    }

    private static String pointerToken(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}