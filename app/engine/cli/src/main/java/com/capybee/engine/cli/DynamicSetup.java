package com.capybee.engine.cli;

import com.capybee.engine.generation.profile.GeneratorProfile;
import com.capybee.engine.generation.profile.PreparationAnswer;
import com.capybee.engine.generation.profile.ValidatedGeneratorProfile;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.input.PiiScrubber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profile-driven local wizard. The optional suggestion never leaves this object. */
final class DynamicSetup {
    record Result(boolean cancelled, Map<String, PreparationAnswer> answers, String suggestion, long revision) {
        Result { answers = Map.copyOf(answers); }
        @Override public String toString() { return "DynamicSetup.Result[cancelled=" + cancelled + ", suggestion=redacted]"; }
    }

    private final BufferedReader input;
    private final PrintStream out;

    DynamicSetup(BufferedReader input, PrintStream out) { this.input = input; this.out = out; }

    Result run(ValidatedGeneratorProfile validated, LocaleTag locale, boolean suggestionAllowed) {
        GeneratorProfile profile = validated.profile();
        Map<String, String> strings = profile.strings().getOrDefault(locale, Map.of());
        Map<String, StartInputDef> inputs = new LinkedHashMap<>();
        for (StartInputDef definition : profile.setup().inputs()) {
            if (inputs.putIfAbsent(definition.id(), definition) != null)
                return new Result(true, Map.of(), null, 1);
        }
        if (profile.setup().questions().stream().anyMatch(question -> !inputs.containsKey(question.inputId())))
            return new Result(true, Map.of(), null, 1);
        out.println("Prepare a new dynamic game");
        out.println(safe(strings.getOrDefault(profile.titleKey(), profile.id().value())));
        out.println("1) Quick start  2) Customize  3) Cancel");
        String mode = read();
        if (mode == null || mode.equals("3") || mode.equalsIgnoreCase("/quit")) return new Result(true, Map.of(), null, 1);
        out.println("Choose Enter to keep each default, 'back' to revisit, 'clear' to clear an optional choice, or /quit.");
        var answers = new LinkedHashMap<String, PreparationAnswer>();
        String suggestion = null;
        long revision = 1;
        if (!mode.equals("2")) {
            for (var question : profile.setup().questions()) {
                StartInputDef definition = inputs.get(question.inputId());
                if (!(definition instanceof StartInputDef.FreeTextInput) && !question.defaultValues().isEmpty())
                    answers.put(definition.id(), new PreparationAnswer.Selected(question.defaultValues()));
            }
            review(profile, strings, locale, answers, null);
            out.println("Start? [y/N]");
            String confirmation=read();
            return new Result(!yes(confirmation), answers, null, revision);
        }
        int index = 0;
        while (index < profile.setup().questions().size()) {
            var question = profile.setup().questions().get(index);
            StartInputDef definition = inputs.get(question.inputId());
            if (definition instanceof StartInputDef.FreeTextInput) {
                if (!suggestionAllowed) { index++; continue; }
                out.println("Optional idea privacy: after Start, approved text and setup may be sent to the configured external AI service. Do not enter real names, contact details, exact addresses, school/class details, passwords, or secrets. Detection may miss details. The original is not saved; generated game content is saved. Cancel before Start sends nothing; cancellation after dispatch cannot retract a request. Continue? [y/N]");
                if (!yes(read())) { index++; continue; }
                out.println(safe(strings.getOrDefault(definition.labelKey(), definition.id())) + " (optional; [provided] only in review)");
                String line = read();
                if (line == null || line.equalsIgnoreCase("/quit")) return new Result(true, answers, null, revision);
                if (line.equalsIgnoreCase("back") && index > 0) { index--; continue; }
                if (line.isBlank() || line.equalsIgnoreCase("clear")) suggestion = null;
                else {
                    String normalized;
                    try { normalized = new PiiScrubber().normalize(line); }
                    catch (IllegalArgumentException invalid) { out.println("[needs editing] Replace, clear, or /quit."); continue; }
                    int maximum=Math.min(300,Math.min(((StartInputDef.FreeTextInput)definition).maxChars(),profile.policy().maxSuggestionCodePoints()));
                    if(normalized.codePointCount(0,normalized.length())>maximum){out.println("[needs editing] Text is over the limit; it was not truncated. Replace, clear, or /quit.");continue;}
                    suggestion=normalized;
                }
                revision++;
                index++; continue;
            }
            List<String> values = definition instanceof StartInputDef.EnumInput item ? item.values()
                    : ((StartInputDef.EnumSetInput) definition).values();
            out.println(safe(strings.getOrDefault(definition.labelKey(), definition.id())) + ":");
            for (int i = 0; i < values.size(); i++) {
                String optionValue = values.get(i);
                String label = question.options().stream().filter(o -> o.value().equals(optionValue)).findFirst()
                        .map(o -> strings.getOrDefault(o.labelKey(), o.value())).orElse(values.get(i));
                out.println((i + 1) + ") " + safe(label));
            }
            String defaults = String.join(",", question.defaultValues());
            out.print("Selection [" + safe(defaults) + "]: ");
            String line = read();
            if (line == null || line.equalsIgnoreCase("/quit")) return new Result(true, answers, null, revision);
            if (line.equalsIgnoreCase("back") && index > 0) { index--; continue; }
            if (line.equalsIgnoreCase("clear") && !definition.required()) answers.put(definition.id(), new PreparationAnswer.Cleared());
            else if (!line.isBlank()) {
                try {
                    String[] choices=line.trim().split(","); var selectedValues=new java.util.ArrayList<String>();
                    for(String choice:choices){int selected=Integer.parseInt(choice.trim())-1;if(selected<0||selected>=values.size()||selectedValues.contains(values.get(selected)))throw new NumberFormatException();selectedValues.add(values.get(selected));}
                    if(definition instanceof StartInputDef.EnumInput && selectedValues.size()!=1 || definition instanceof StartInputDef.EnumSetInput set && selectedValues.size()>set.maxItems())throw new NumberFormatException();
                    answers.put(definition.id(), new PreparationAnswer.Selected(selectedValues));
                } catch (NumberFormatException invalid) { out.println("Choose a listed number."); continue; }
            }
            revision++;
            index++;
        }
        review(profile, strings, locale, answers, suggestion);
        out.println("Start? [y/N]");
        String confirmation = read();
        return new Result(!yes(confirmation), answers, suggestion, revision);
    }

    private String read() { try { return input.readLine(); } catch (IOException failure) { return null; } }
    private void review(GeneratorProfile profile,Map<String,String> strings,LocaleTag locale,Map<String,PreparationAnswer> answers,String suggestion){
        out.println("Review:");
        for(var question:profile.setup().questions()){
            PreparationAnswer answer=answers.get(question.inputId());if(answer==null||answer instanceof PreparationAnswer.Cleared)continue;
            if(answer instanceof PreparationAnswer.Selected selected){for(String value:selected.values()){String label=question.options().stream().filter(o->o.value().equals(value)).findFirst().map(o->strings.getOrDefault(o.labelKey(),o.value())).orElse(value);out.println(safe(question.inputId())+"="+safe(label));}}
        }
        out.println("locale="+safe(locale.value())+", suggestion="+(suggestion==null?"[skipped]":"[provided]")+", provider=external, saving=enabled");
    }
    private static boolean yes(String value){return value!=null&&(value.equalsIgnoreCase("y")||value.equalsIgnoreCase("yes"));}
    private static String safe(String value) { return ConsoleRenderer.sanitize(value); }
}
