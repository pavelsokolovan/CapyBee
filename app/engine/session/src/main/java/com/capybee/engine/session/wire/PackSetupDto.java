package com.capybee.engine.session.wire;

import com.capybee.engine.pack.*;
import java.util.*;

/** Public display metadata only; never generation prompts or mechanics. */
public record PackSetupDto(int schemaVersion, String metadataLocale, String title, String description,
        boolean transientTextSupported, List<Step> steps, List<Input> inputs, String preparationFingerprint) {
    public PackSetupDto(int schemaVersion, String metadataLocale, String title, String description, boolean transientTextSupported, List<Step> steps, List<Input> inputs) { this(schemaVersion, metadataLocale, title, description, transientTextSupported, steps, inputs, null); }
    public record Step(String id, String title, List<String> inputIds) {}
    public record Option(String id, String label) {}
    public record Input(String id, String kind, String label, String help, boolean required,
                        int maxItems, Integer maxCodePoints, List<String> defaults, List<Option> options, String notice) {}
    public static PackSetupDto from(Pack pack, Locale hint) {
        var manifest = pack.manifest();
        String locale = manifest.locales().stream().map(LocaleTag::value)
                .filter(tag -> hint != null && tag.equals(hint.getLanguage())).findFirst()
                .orElse(manifest.defaultLocale().value());
        var strings = SessionJson.tree(pack.requireFile("strings/" + locale + ".json"));
        var setup = pack.file("generation/setup.json").map(SessionJson::tree).orElse(null);
        var inputs = manifest.startInputs().stream().map(input -> {
            var question = setup == null ? null : setup.path("questions").valueStream()
                    .filter(q -> input.id().equals(q.path("inputId").asString())).findFirst().orElse(null);
            List<String> values = input instanceof StartInputDef.EnumInput e ? e.values()
                    : input instanceof StartInputDef.EnumSetInput e ? e.values() : List.of();
            var options = values.stream().map(value -> {
                String label = value;
                if (question != null) for (var option : question.path("options"))
                    if (value.equals(option.path("value").asString())) label = strings.path(option.path("labelKey").asString()).asString(value);
                return new Option(value, label);
            }).toList();
            List<String> defaults = question == null ? List.of() : question.path("defaultValues").valueStream().map(n -> n.asString()).toList();
            return new Input(input.id(), input instanceof StartInputDef.FreeTextInput ? "FREE_TEXT" : input instanceof StartInputDef.EnumSetInput ? "ENUM_SET" : "ENUM",
                    strings.path(input.labelKey()).asString(input.id()), question == null ? "" : strings.path(question.path("helpKey").asString()).asString(""),
                    input.required(), input instanceof StartInputDef.EnumSetInput set ? set.maxItems() : 1,
                    input instanceof StartInputDef.FreeTextInput text ? text.maxChars() : null, defaults, options, question == null || question.path("freeTextNoticeKey").isMissingNode() ? null : strings.path(question.path("freeTextNoticeKey").asString()).asString(""));
        }).toList();
        return new PackSetupDto(1, locale, manifest.name(), manifest.description(), false,
                inputs.isEmpty() ? List.of() : List.of(new Step("setup", manifest.name(), inputs.stream().map(Input::id).toList())), inputs);
    }
}
