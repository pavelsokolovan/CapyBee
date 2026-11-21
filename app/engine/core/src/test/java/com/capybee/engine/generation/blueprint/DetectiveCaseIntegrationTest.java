package com.capybee.engine.generation.blueprint;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.DynamicGameBootstrapRequest;
import com.capybee.engine.api.DynamicGameBootstrapResult;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.Run;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import com.capybee.engine.generation.blueprint.json.BlueprintWriter;
import com.capybee.engine.generation.profile.PreparationPresetResolver;
import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.rules.ResultClass;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class DetectiveCaseIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void generatesAndResumesAnEnglishCase() throws Exception { playCase("en"); }

    @Test void generatesAndResumesAPolishCase() throws Exception { playCase("pl"); }

    private static void playCase(String language) throws Exception {
        CaseText text = new CaseText(language.equals("pl"));
        String rejected = blueprint(language, text, null);
        var clues = new CompositeCondition("ALL", List.of(new VisitedSceneCondition("seal"), new VisitedSceneCondition("clock")));
        String valid = blueprint(language, text, clues);
        var blueprintCalls = new ArrayList<StructuredGenerationRequest>();
        var generator = new BlueprintGenerationService(sent -> {
            blueprintCalls.add(sent);
            return BlueprintGenerationServiceTest.complete(blueprintCalls.size() == 1 ? rejected : valid);
        }, new DefaultBlueprintOutputReviewer(), new DefaultBlueprintSemanticValidator(), new SequentialIdSource("blueprint"));
        var sceneCalls = new ArrayList<StructuredGenerationRequest>();
        TextGenerator presenter = sent -> {
            sceneCalls.add(sent);
            return BlueprintGenerationServiceTest.complete(presentation(sent, text));
        };

        Engine online = engine().dynamicBlueprints(generator).textGenerator(presenter).build();
        var result = online.startDynamic(new DynamicGameBootstrapRequest(request(language), 7L));
        assertThat(result).isInstanceOf(DynamicGameBootstrapResult.Started.class);
        var started = (DynamicGameBootstrapResult.Started) result;
        assertThat(blueprintCalls).hasSize(2);
        assertThat(blueprintCalls.getLast().untrustedDataJson())
                .contains("BLUEPRINT_DETECTIVE_ACCUSATION_BEFORE_CLUES", "/scenes/9/choices/0/requires")
                .doesNotContain(text.seal());
        var bindings = started.definition().idBindings();
        Engine offline = engine().build();
        Run incomplete = offline.resume(started.run().snapshot()).run();
        choose(incomplete, bindings.choices().get("opening").get("skip-seal"));
        advanceToResolution(incomplete, bindings.scenes().get("resolution"));
        assertThat(incomplete.view().options()).anySatisfy(option -> {
            assertThat(option.label()).isEqualTo(text.accuse());
            assertThat(option.enabled()).isFalse();
        });
        advance(incomplete);
        assertThat(incomplete.view().result().resultClass()).isEqualTo(ResultClass.PARTIAL_SUCCESS);

        Run solved = started.run();
        choose(solved, bindings.choices().get("opening").get("inspect-seal"));
        assertThat(solved.view().blocks()).anySatisfy(block -> assertThat(block.text()).isEqualTo(text.seal()));
        assertThat(sceneCalls).filteredOn(call -> call.untrustedDataJson().contains("requiredNarration")
                && call.untrustedDataJson().contains(text.seal())).hasSize(2);
        assertThat(sceneCalls.getLast().untrustedDataJson()).contains("DYNAMIC_DETECTIVE_EVIDENCE_CHANGED");
        var clueView = solved.view();
        var saved = SnapshotCodec.fromJson(SnapshotCodec.toJson(solved.snapshot()));
        assertThat(saved.issues()).isEmpty();
        assertThat(offline.resume(saved.snapshot()).run().view()).isEqualTo(clueView);
        advanceToResolution(solved, bindings.scenes().get("resolution"));
        assertThat(solved.view().options()).anySatisfy(option -> {
            assertThat(option.label()).isEqualTo(text.accuse());
            assertThat(option.enabled()).isTrue();
        });
        choose(solved, bindings.choices().get("resolution").get("accuse-suspect-nora"));
        assertThat(solved.view().result().resultClass()).isEqualTo(ResultClass.SUCCESS);
        assertThat(solved.view().blocks()).anySatisfy(block -> assertThat(block.text()).isEqualTo(text.conclusion()));
        assertThat(sceneCalls.getLast().untrustedDataJson())
                .contains("requiredEndingSummary", "DYNAMIC_DETECTIVE_EVIDENCE_CHANGED", text.seal(), text.clock());
        var ending = SnapshotCodec.fromJson(SnapshotCodec.toJson(solved.snapshot()));
        assertThat(ending.issues()).isEmpty();
        assertThat(ending.snapshot().dynamic().artifacts())
                .filteredOn(artifact -> artifact.sceneId().equals(bindings.scenes().get("solved")))
                .singleElement().satisfies(artifact ->
                        assertThat(artifact.payload().ending().summary()).isEqualTo(text.reconstruction()));
        assertThat(offline.resume(ending.snapshot()).run().view()).isEqualTo(solved.view());
    }

    @Test void rejectsASolutionWhichOmitsAClueOrAllowsAnUnmarkedAccusation() throws Exception {
        var request = request("en");
        var text = new CaseText(false);
        var gate = new CompositeCondition("ALL", List.of(new VisitedSceneCondition("seal"), new VisitedSceneCondition("clock")));
        ObjectNode changed = (ObjectNode) JSON.readTree(blueprint("en", text, gate));
        ((ObjectNode) changed.path("outcomes").get(0)).put("description", "Nora removed the letter.");
        ((ObjectNode) changed.path("scenes").get(9).path("choices").get(0)).put("id", "close-case");
        var parsed = new com.capybee.engine.generation.blueprint.json.BlueprintOutputParser()
                .parse(changed.toString(), request.profile(), request.preferences(), request.constraints()).blueprint().orElseThrow();
        var result = new DefaultBlueprintSemanticValidator().validate(parsed, request.profile(), request.preferences(), request.constraints());
        assertThat(result.issues()).extracting(BlueprintIssue::code)
                .contains("BLUEPRINT_DETECTIVE_ACCUSATION_REQUIRED", "BLUEPRINT_DETECTIVE_RECONSTRUCTION_INVALID");
    }

    private static BlueprintGenerationRequest request(String language) throws Exception {
        Path root = Path.of("../generator-profiles/classic-detective");
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        var profile = new GeneratorProfileLoader().load(files).profile().orElseThrow();
        var resolved = new PreparationPresetResolver().resolve(profile, Map.of(), new LocaleTag(language), null);
        return new BlueprintGenerationRequest("detective-fixture", 1, 1, profile, resolved.preferences().orElseThrow(),
                resolved.constraints().orElseThrow(), null, null, BlueprintCancellation.NEVER, null);
    }

    private static String blueprint(String locale, CaseText text, Condition gate) {
        var actors = List.of(new Actor("detective", "Iga", text.phrase("Investigator", "Śledcza"), text.next()),
                new Actor("client", "Ada", text.phrase("Client", "Klientka"), text.next()),
                new Actor("suspect-nora", "Nora", text.phrase("Archivist", "Archiwistka"), text.next()),
                new Actor("suspect-leon", "Leon", text.phrase("Clockmaker", "Zegarmistrz"), text.next()));
        var objectives = List.of(new Objective("clue-seal", text.phrase("Inspect the seal", "Zbadaj pieczęć"),
                        text.next(), new VisitedSceneCondition("seal")),
                new Objective("clue-clock", text.phrase("Check the clock", "Sprawdź zegar"),
                        text.next(), new VisitedSceneCondition("clock")));
        var cast = actors.stream().map(Actor::id).toList();
        var objectiveIds = objectives.stream().map(Objective::id).toList();
        var phases = java.util.Arrays.stream(PhaseKind.values())
                .map(phase -> new Phase(phase, text.next(), text.next(), text.next(), cast, objectiveIds)).toList();
        var scenes = List.<Scene>of(
                new DecisionScene("opening", PhaseKind.OPENING, text.title(), text.next(), cast, List.of(),
                        List.of(new Choice("inspect-seal", text.phrase("Inspect the envelope", "Zbadaj kopertę"), "seal", null, List.of()),
                                new Choice("skip-seal", text.phrase("Question the client", "Zapytaj klientkę"), "interview", null, List.of()))),
                scene("seal", PhaseKind.DEVELOPMENT, text, text.seal(), "hall", List.of("clue-seal")),
                scene("interview", PhaseKind.DEVELOPMENT, text, text.next(), "hall", List.of()),
                scene("hall", PhaseKind.DEVELOPMENT, text, text.next(), "clock", List.of()),
                scene("clock", PhaseKind.CHALLENGE, text, text.clock(), "alibi", List.of("clue-clock")),
                scene("alibi", PhaseKind.CHALLENGE, text, text.next(), "test", List.of()),
                scene("test", PhaseKind.CHALLENGE, text, text.next(), "compare", List.of()),
                scene("compare", PhaseKind.CHALLENGE, text, text.next(), "corroborate", List.of()),
                scene("corroborate", PhaseKind.CHALLENGE, text, text.next(), "resolution", List.of()),
                new DecisionScene("resolution", PhaseKind.RESOLUTION, text.title(), text.next(), cast, List.of(),
                        List.of(new Choice("accuse-suspect-nora", text.accuse(), "solved", gate, List.of()),
                                new Choice("defer", text.phrase("Leave case open", "Zostaw otwartą sprawę"), "unresolved", null, List.of()))),
                new EndingScene("solved", PhaseKind.ENDING, text.title(), text.conclusion(), cast, List.of(), "solved"),
                new EndingScene("unresolved", PhaseKind.ENDING, text.title(), text.next(), cast, List.of(), "unresolved"));
        return new BlueprintWriter().writeString(new ParsedGameBlueprint(1, locale, text.title(), text.next(),
                text.phrase("The archive", "Archiwum"), List.of("cerebral"), List.of(text.next()), actors,
                new State(List.of(), List.of(), List.of()), objectives, phases, "opening", scenes,
                List.of(new Outcome("solved", ResultClass.SUCCESS, text.title(), text.reconstruction()),
                        new Outcome("unresolved", ResultClass.PARTIAL_SUCCESS, text.title(), text.next()))));
    }

    private static DecisionScene scene(String id, PhaseKind phase, CaseText text, String intent, String next, List<String> objectives) {
        return new DecisionScene(id, phase, text.title(), intent, List.of("detective"), objectives,
                List.of(new Choice("continue", text.phrase("Keep investigating", "Badaj dalej"), next, null, List.of())));
    }

    private static String presentation(StructuredGenerationRequest request, CaseText text) {
        var input = JSON.readTree(request.untrustedDataJson());
        boolean alterClue = input.path("requiredNarration").asString("").equals(text.seal());
        var output = JSON.createObjectNode().put("sceneId", input.path("sceneId").asString())
                .put("title", input.path("title").asString())
                .put("narration", alterClue ? text.next() : input.path("intent").asString());
        output.putArray("dialogue");
        var choices = output.putArray("choices");
        input.path("choices").forEach(choice -> {
            String label = choice.path("intent").asString();
            if (label.equals(text.accuse()) && input.path("operation").asString().equals("initial")) {
                label = text.phrase("Accuse Leon", "Oskarż Leon");
            }
            choices.addObject().put("id", choice.path("id").asString()).put("label", label);
        });
        output.putArray("continuityFacts");
        if (input.has("requiredEndingSummary")) {
            String summary = input.path("operation").asString().equals("initial")
                    ? text.conclusion() : input.path("requiredEndingSummary").asString();
            var ending = output.putObject("ending").put("headline", text.title())
                    .put("summary", summary).put("reflection", "");
            ending.putArray("objectiveNotes");
        }
        return output.toString();
    }

    private static com.capybee.engine.api.EngineBuilder engine() {
        return Engine.builder(new PackRegistry(List.of())).clock(Clock.systemUTC())
                .random(Randoms.seeded(7)).ids(new SequentialIdSource("run"));
    }

    private static void advanceToResolution(Run run, String resolution) {
        for (int i = 0; i < 10 && !run.snapshot().state().sceneId().equals(resolution); i++) advance(run);
        assertThat(run.snapshot().state().sceneId()).isEqualTo(resolution);
    }

    private static void advance(Run run) {
        choose(run, run.view().options().stream().filter(option -> option.enabled()).findFirst().orElseThrow().id());
    }

    private static void choose(Run run, String id) { assertThat(run.apply(new Command.Choose(id)).accepted()).isTrue(); }

    private record CaseText(boolean polish) {
        String phrase(String en, String pl) { return polish ? pl : en; }
        String title() { return phrase("The missing letter", "Zaginiony list"); }
        String next() { return phrase("Iga studies the quiet archive.", "Iga ogląda ciche archiwum."); }
        String seal() { return phrase("The wax has two impressions.", "Wosk ma dwa odciski."); }
        String clock() { return phrase("The hall clock stopped before dusk.", "Zegar w holu zatrzymał się przed zmrokiem."); }
        String accuse() { return phrase("Accuse Nora", "Oskarż Nora"); }
        String conclusion() { return phrase("Nora opened the seal before dusk to hide the letter.",
                "Nora otworzyła pieczęć przed zmrokiem, żeby ukryć list."); }
        String reconstruction() { return seal() + " " + clock() + " " + conclusion(); }
    }
}
