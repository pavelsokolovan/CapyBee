package com.capybee.engine.cli;

import com.capybee.engine.generation.GenerationSetup;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.rules.json.RuleStrings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic, pack-driven presenter for the validated full-game setup resource. */
final class InteractiveSetup {
    private static final ObjectMapper JSON = new ObjectMapper();

    record Result(boolean cancelled, Map<String, String> inputs) {
        Result { inputs = Map.copyOf(inputs); }
    }

    private final BufferedReader input;
    private final PrintStream out;

    InteractiveSetup(BufferedReader input, PrintStream out) {
        this.input = input;
        this.out = out;
    }

    Result run(Pack pack, LocaleTag locale, boolean providerAvailable, Map<String, String> supplied) {
        if (providerAvailable && !disclose()) return new Result(true, Map.of());
        if (!providerAvailable) out.println("AI text service unavailable; using the complete reviewed story.\n");

        Map<String, StartInputDef> definitions = new LinkedHashMap<>();
        pack.manifest().startInputs().forEach(definition -> definitions.put(definition.id(), definition));
        Map<String, String> strings = RuleStrings.load(pack, locale);
        List<Question> questions = questions(pack).stream()
                .filter(question -> providerAvailable || !(definitions.get(question.inputId()) instanceof StartInputDef.FreeTextInput))
                .toList();
        Map<String, String> answers = new LinkedHashMap<>(supplied);
        int index = 0;
        while (index < questions.size()) {
            Question question = questions.get(index);
            StartInputDef definition = definitions.get(question.inputId());
            if (definition == null) { index++; continue; }
            printQuestion(question, definition, strings, answers.get(question.inputId()));
            String line = readLine();
            if (line == null || line.equalsIgnoreCase("q") || line.equalsIgnoreCase("/cancel"))
                return new Result(true, Map.of());
            if (line.equalsIgnoreCase("b")) { if (index > 0) index--; else out.println("Already at the first question."); continue; }
            String value = answer(line, question, definition);
            if (value == null) { out.println("That answer is not valid. Please try again."); continue; }
            if (value.isBlank()) answers.remove(question.inputId()); else answers.put(question.inputId(), value);
            index++;
        }

        out.println("\nReview your setup:");
        for (Question question : questions) {
            StartInputDef definition = definitions.get(question.inputId());
            String label = text(strings, definition.labelKey(), definition.id());
            String value = answers.get(question.inputId());
            if (definition instanceof StartInputDef.FreeTextInput)
                out.println("- " + label + ": " + (value == null ? "[skipped]" : "[provided]"));
            else out.println("- " + label + ": " + labels(value, question, strings));
        }
        out.print("Start this game? [y/N] ");
        String confirmation = readLine();
        return confirmation != null && (confirmation.equalsIgnoreCase("y") || confirmation.equalsIgnoreCase("yes"))
                ? new Result(false, answers) : new Result(true, Map.of());
    }

    private boolean disclose() {
        out.println("A configured AI text service may shape story presentation.");
        out.println("Your selected setup answers may be sent to that service. Optional text is checked locally and detected personal details are removed, but detection may not be perfect.");
        out.println("Do not enter names, contact details, an exact address, school or class details, passwords, or secrets.");
        out.println("Optional text is not saved in the game snapshot. Cancel sends nothing and creates no game.");
        while (true) {
            out.print("Continue? [y/N] ");
            String answer = readLine();
            if (answer == null || answer.isBlank() || answer.equalsIgnoreCase("n")
                    || answer.equalsIgnoreCase("no") || answer.equalsIgnoreCase("/cancel")) return false;
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return true;
            out.println("Please enter y or n.");
        }
    }

    private void printQuestion(Question question, StartInputDef definition, Map<String, String> strings,
                               String existing) {
        out.println("\n" + text(strings, definition.labelKey(), definition.id()));
        out.println(text(strings, question.helpKey(), "Choose an option."));
        for (int i = 0; i < question.options().size(); i++) {
            Option option = question.options().get(i);
            out.println((i + 1) + ") " + text(strings, option.labelKey(), option.value()));
        }
        if (question.noticeKey() != null) out.println(text(strings, question.noticeKey(), ""));
        String defaults = existing == null ? String.join(",", question.defaults()) : existing;
        if (!defaults.isBlank()) out.println("Default: " + labels(defaults, question, strings));
        out.print("Enter a number" + (definition instanceof StartInputDef.EnumSetInput ? " or comma-separated numbers" : "")
                + ", b to go back, q to cancel, or press Enter" + (defaults.isBlank() ? " to skip: " : " for the default: "));
    }

    private String answer(String line, Question question, StartInputDef definition) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return String.join(",", question.defaults());
        if (definition instanceof StartInputDef.FreeTextInput text)
            return trimmed.codePointCount(0, trimmed.length()) <= text.maxChars() ? trimmed : null;
        String[] selections = trimmed.split(",");
        if (!(definition instanceof StartInputDef.EnumSetInput) && selections.length != 1) return null;
        List<String> values = new ArrayList<>();
        for (String selection : selections) {
            try {
                int selected = Integer.parseInt(selection.trim()) - 1;
                if (selected < 0 || selected >= question.options().size()) return null;
                String value = question.options().get(selected).value();
                if (values.contains(value)) return null;
                values.add(value);
            } catch (NumberFormatException invalid) { return null; }
        }
        if (definition instanceof StartInputDef.EnumSetInput set && values.size() > set.maxItems()) return null;
        return String.join(",", values);
    }

    private static String labels(String values, Question question, Map<String, String> strings) {
        if (values == null || values.isBlank()) return "[skipped]";
        Map<String, String> labels = new LinkedHashMap<>();
        question.options().forEach(option -> labels.put(option.value(), text(strings, option.labelKey(), option.value())));
        return java.util.Arrays.stream(values.split(",")).map(String::trim)
                .map(value -> labels.getOrDefault(value, value)).collect(java.util.stream.Collectors.joining(", "));
    }

    private static List<Question> questions(Pack pack) {
        try {
            JsonNode root = JSON.readTree(pack.requireFile("generation/setup.json"));
            List<Question> result = new ArrayList<>();
            for (JsonNode node : root.get("questions")) {
                List<String> defaults = new ArrayList<>();
                if (node.get("defaultValues") != null) node.get("defaultValues").forEach(value -> defaults.add(value.asString()));
                List<Option> options = new ArrayList<>();
                if (node.get("options") != null) node.get("options").forEach(option ->
                        options.add(new Option(option.get("value").asString(), option.get("labelKey").asString())));
                result.add(new Question(node.get("inputId").asString(), node.get("helpKey").asString(), defaults,
                        options, node.get("freeTextNoticeKey") == null ? null : node.get("freeTextNoticeKey").asString()));
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            return List.of(); // Pack validation owns malformed/missing setup resources.
        }
    }

    private String readLine() {
        try { return input.readLine(); }
        catch (Exception failure) { return null; }
    }

    private static String text(Map<String, String> strings, String key, String fallback) {
        return key == null ? fallback : strings.getOrDefault(key, fallback);
    }

    private record Question(String inputId, String helpKey, List<String> defaults,
                            List<Option> options, String noticeKey) { }
    private record Option(String value, String labelKey) { }
}
