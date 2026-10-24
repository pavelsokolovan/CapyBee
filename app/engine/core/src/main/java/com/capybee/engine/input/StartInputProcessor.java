package com.capybee.engine.input;

import com.capybee.engine.api.Issue;
import com.capybee.engine.pack.ContentPolicy;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.generation.profile.GeneratorProfile;
import com.capybee.engine.port.InputGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The single validation, normalization, scrubbing, and guarding path for start inputs. */
public final class StartInputProcessor {
    private final PiiScrubber scrubber;

    public StartInputProcessor() { this(new PiiScrubber()); }
    public StartInputProcessor(PiiScrubber scrubber) { this.scrubber = scrubber; }

    public PreparedStartInputs prepare(Pack pack, LocaleTag locale, Map<String, String> submitted,
                                       InputGuard guard) {
        ContentPolicy content = pack.manifest().contentPolicy();
        return prepare(pack.manifest().startInputs(), content, submitted, guard);
    }

    /** Host-neutral profile entry point. Only the declared transient input should be submitted here. */
    public PreparedStartInputs prepare(List<StartInputDef> definitions, GeneratorProfile.ProfilePolicy profilePolicy,
                                       Map<String, String> submitted, InputGuard guard) {
        ContentPolicy.Audience audience = profilePolicy == null ? null
                : new ContentPolicy.Audience(profilePolicy.minAge(), profilePolicy.maxAge());
        ContentPolicy policy = profilePolicy == null ? null : new ContentPolicy(profilePolicy.freeTextAllowed(),
                audience, List.copyOf(profilePolicy.forbiddenTags()));
        return prepare(definitions, policy, submitted, guard);
    }

    private PreparedStartInputs prepare(List<StartInputDef> definitions, ContentPolicy content,
                                        Map<String, String> submitted, InputGuard guard) {
        Map<String, StartInputDef> declared = new LinkedHashMap<>();
        definitions.forEach(input -> declared.put(input.id(), input));
        Map<String, String> values = normalize(submitted);
        Map<String, String> persistent = new LinkedHashMap<>();
        Map<String, SafeTextInput> text = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();

        values.keySet().stream().filter(key -> !declared.containsKey(key)).forEach(key ->
                issues.add(error("START_INPUT_UNKNOWN", "Unknown start input: " + key)));
        for (StartInputDef input : definitions) {
            String value = values.get(input.id());
            if (value == null || value.isBlank()) {
                if (input.required()) issues.add(error("START_INPUT_MISSING", "Required start input is missing: " + input.id()));
                continue;
            }
            if (input instanceof StartInputDef.EnumInput enumInput) {
                if (!enumInput.values().contains(value)) issues.add(invalid(input));
                else persistent.put(input.id(), value);
            } else if (input instanceof StartInputDef.EnumSetInput enumSet) {
                List<String> selected = split(value);
                if (selected.size() > enumSet.maxItems() || selected.isEmpty()
                        || new HashSet<>(selected).size() != selected.size()
                        || !enumSet.values().containsAll(selected)) issues.add(invalid(input));
                else persistent.put(input.id(), enumSet.values().stream().filter(selected::contains)
                        .collect(java.util.stream.Collectors.joining(",")));
            } else if (input instanceof StartInputDef.FreeTextInput freeText) {
                prepareText(content, freeText, value, guard, text, issues);
            }
        }
        return new PreparedStartInputs(persistent, text, issues);
    }

    private void prepareText(ContentPolicy content, StartInputDef.FreeTextInput input, String value, InputGuard guard,
                             Map<String, SafeTextInput> output, List<Issue> issues) {
        if (content == null || !content.freeTextAllowed()) {
            issues.add(error("FREE_TEXT_DISABLED", "Free-text input is disabled by pack policy")); return;
        }
        if (guard == null) {
            issues.add(error("INPUT_GUARD_UNAVAILABLE", "Text safety checks are unavailable")); return;
        }
        try {
            String normalized = scrubber.normalize(value);
            int limit = Math.min(input.maxChars(), PiiScrubber.MAX_INPUT_CODE_POINTS);
            if (normalized.codePointCount(0, normalized.length()) > limit) {
                issues.add(error("INPUT_TOO_LONG", "Text input exceeds the allowed length")); return;
            }
            ProcessedInput processed = scrubber.process(normalized);
            InputSafetyDecision decision = guard.inspect(processed.input(), policy(content, limit));
            if (decision == null || decision.action() == null || decision.action() == InputSafetyAction.UNAVAILABLE) {
                issues.add(error("INPUT_GUARD_UNAVAILABLE", "Text safety checks are unavailable")); return;
            }
            if (decision.action() == InputSafetyAction.BLOCK) {
                issues.add(error("INPUT_BLOCKED", "Text input was not accepted")); return;
            }
            if (decision.action() == InputSafetyAction.NEEDS_CLARIFICATION) {
                issues.add(error("INPUT_NEEDS_CLARIFICATION", "Text input needs clarification")); return;
            }
            output.put(input.id(), processed.input());
            if (processed.changed() || decision.action() == InputSafetyAction.ALLOW_REDACTED) {
                issues.add(new Issue(Issue.Severity.WARNING, "INPUT_REDACTED", "Personal details were removed from text input"));
            }
        } catch (PiiScrubber.InputTextException exception) {
            issues.add(error(exception.code(), "Text input is invalid"));
        } catch (RuntimeException exception) {
            issues.add(error("INPUT_GUARD_UNAVAILABLE", "Text safety checks are unavailable"));
        }
    }

    private static Map<String, String> normalize(Map<String, String> submitted) {
        Map<String, String> result = new LinkedHashMap<>();
        if (submitted == null) return result;
        submitted.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.trim().isBlank()) result.put(key.trim(), value.trim());
        });
        return result;
    }

    private static List<String> split(String value) {
        return java.util.Arrays.stream(value.split(",", -1)).map(String::trim).toList();
    }

    private static InputPolicy policy(ContentPolicy content, int maxChars) {
        String audience = content.audience() == null ? null
                : content.audience().minAge() + "-" + content.audience().maxAge();
        return new InputPolicy(true, maxChars, 280, 3, audience, Set.copyOf(content.forbiddenTags()), "1");
    }

    private static Issue invalid(StartInputDef input) {
        return error("START_INPUT_INVALID", "Start input value is invalid for " + input.id());
    }
    private static Issue error(String code, String message) { return new Issue(Issue.Severity.ERROR, code, message); }
}
