package com.capybee.engine.pack.json;

import com.capybee.engine.pack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PackLoaderTest {
    private final PackId id = new PackId("fixture-space-courier");
    private final PackLoader loader = new PackLoader(List.of());

    private InMemoryPackSource source() {
        var source = new ClasspathPackSource(getClass().getClassLoader(), "game-packs/" + id.value());
        return new InMemoryPackSource(Map.of("pack.json", source.read("pack.json").orElseThrow(),
                "strings/en.json", source.read("strings/en.json").orElseThrow(),
                "prompts/en/system.md", source.read("prompts/en/system.md").orElseThrow()));
    }

    @Test void mapsCompleteManifest() {
        LoadResult result = loader.tryLoad(id, source());
        assertTrue(result.ok(), result.issues().toString());
        assertTrue(result.issues().isEmpty());
        PackManifest manifest = result.pack().orElseThrow().manifest();
        assertEquals(id, manifest.id());
        assertEquals(new PackVersion(1, 0, 0), manifest.version());
        assertEquals(3, manifest.startInputs().size());
        assertInstanceOf(StartInputDef.EnumInput.class, manifest.startInputs().get(0));
        assertEquals(2, ((StartInputDef.EnumSetInput) manifest.startInputs().get(1)).maxItems());
        assertEquals(140, ((StartInputDef.FreeTextInput) manifest.startInputs().get(2)).maxChars());
        assertEquals(2, manifest.features().size());
        assertEquals("run.completed", manifest.events().getFirst().id());
        assertEquals(EventDef.PayloadType.INT, manifest.events().getFirst().payload().get(1).type());
        assertEquals(new ContentPolicy.Audience(10, 14), manifest.contentPolicy().audience());
        assertEquals(List.of("weapon"), manifest.contentPolicy().forbiddenTags());
        assertEquals("Fixture - Space Courier", manifest.name());
        assertNotNull(manifest.description());
        assertEquals(1, manifest.schemaVersion());
        assertEquals(List.of(new LocaleTag("en")), manifest.locales());
        assertEquals(new LocaleTag("en"), manifest.defaultLocale());
        assertEquals(Set.of(PackFeature.GENERATION, PackFeature.ASSETS), manifest.features());
        var cargo = (StartInputDef.EnumInput) manifest.startInputs().getFirst();
        assertEquals("cargo", cargo.id());
        assertTrue(cargo.required());
        assertEquals("input.cargo", cargo.labelKey());
        assertEquals(List.of("medicine", "mail", "seeds"), cargo.values());
        var hazards = (StartInputDef.EnumSetInput) manifest.startInputs().get(1);
        assertEquals("hazards", hazards.id());
        assertFalse(hazards.required());
        assertEquals("input.hazards", hazards.labelKey());
        assertEquals(3, hazards.values().size());
        var wish = (StartInputDef.FreeTextInput) manifest.startInputs().get(2);
        assertEquals("wish", wish.id());
        assertFalse(wish.required());
        assertEquals("input.wish", wish.labelKey());
        assertTrue(manifest.contentPolicy().freeTextAllowed());
        assertEquals("summary", manifest.events().getFirst().payload().getFirst().name());
        assertEquals(2, manifest.files().size());
    }

    @Test void contentFailuresAreIssuesNotExceptions() {
        var missing = loader.tryLoad(id, source().without("pack.json"));
        assertEquals("PACK_MANIFEST_MISSING", missing.issues().getFirst().code());
        var invalid = loader.tryLoad(id, source().with("pack.json", "{bad"));
        assertEquals("PACK_JSON_INVALID", invalid.issues().getFirst().code());
        assertTrue(invalid.issues().getFirst().message().contains("line"));
        assertTrue(invalid.issues().getFirst().message().contains("column"));
    }

    private InMemoryPackSource manifest(Consumer<ObjectNode> change) {
        InMemoryPackSource source = source();
        var mapper = JsonMapper.builder().build();
        ObjectNode root = (ObjectNode) mapper.readTree(source.read("pack.json").orElseThrow());
        change.accept(root);
        return source.with("pack.json", mapper.writeValueAsString(root));
    }

    private void issue(InMemoryPackSource source, String code) {
        LoadResult result = assertDoesNotThrow(() -> loader.tryLoad(id, source));
        assertFalse(result.ok());
        assertTrue(result.pack().isEmpty());
        assertTrue(result.issues().stream().anyMatch(problem -> problem.code().equals(code)), result.issues().toString());
    }

    private static ObjectNode input(ObjectNode root, int index) {
        return (ObjectNode) root.get("startInputs").get(index);
    }

    private static ObjectNode policy(ObjectNode root) { return (ObjectNode) root.get("contentPolicy"); }

    @Test void rejectsUnsupportedSchema() {
        issue(manifest(root -> root.put("schemaVersion", 2)), "PACK_SCHEMA_UNSUPPORTED");
    }

    @Test void reportsInvalidFieldsTogetherWithPointers() {
        var result = loader.tryLoad(id, manifest(root -> {
            root.put("version", "01.2.3");
            input(root, 2).put("maxChars", 301);
        }));
        assertTrue(result.issues().stream().anyMatch(problem -> problem.pointer().equals("/version")));
        assertTrue(result.issues().stream().anyMatch(problem -> problem.pointer().equals("/startInputs/2/maxChars")));
    }

    @Test void unknownFieldsWarnWithoutBlockingIncludingNestedFields() {
        LoadResult result = loader.tryLoad(id, manifest(root -> {
            root.put("extra", true);
            input(root, 0).put("typo", true);
            policy(root).put("other", true);
        }));
        assertTrue(result.ok());
        assertEquals(3, result.issues().size());
        assertTrue(result.issues().stream().allMatch(problem -> problem.code().equals("PACK_UNKNOWN_FIELD")
                && problem.severity() == PackIssue.Severity.WARNING));
    }

    @Test void rejectsMismatchedPackId() {
        issue(manifest(root -> root.put("id", "other")), "PACK_ID_MISMATCH");
    }

    @ParameterizedTest @MethodSource("invalidPaths")
    void rejectsInvalidIndexedPathsWithoutReadingThem(String path) {
        issue(manifest(root -> ((ArrayNode) root.get("files")).add(path)), "PACK_FILE_PATH_INVALID");
    }

    static Stream<String> invalidPaths() {
        return Stream.of("/absolute.json", "../outside.json", "a\\b.json", "C:/file.json", "a".repeat(121) + ".json",
                "image.png", "", "./file.json", "a//b.json", "pack.json", "a\u0000.json");
    }

    @Test void rejectsDuplicateFileIndex() {
        issue(manifest(root -> ((ArrayNode) root.get("files")).add("strings/en.json")), "PACK_FILE_DUPLICATE");
    }

    @Test void reportsMissingIndexedFiles() {
        issue(source().without("strings/en.json"), "PACK_FILE_MISSING");
    }

    @Test void requiresGenerationPromptOnlyWhenFeatureDeclared() {
        issue(manifest(root -> ((ArrayNode) root.get("files")).remove(1)), "PACK_REQUIRED_FILE_MISSING");
        LoadResult authored = loader.tryLoad(id, manifest(root -> {
            root.putArray("features");
            ((ArrayNode) root.get("files")).remove(1);
        }).without("prompts/en/system.md"));
        assertTrue(authored.ok(), authored.issues().toString());
    }

    @Test void requiresStringsForEveryLocale() {
        issue(manifest(root -> ((ArrayNode) root.get("files")).remove(0)), "PACK_REQUIRED_FILE_MISSING");
    }

    @Test void rejectsEmptyFiles() {
        issue(source().with("strings/en.json", ""), "PACK_FILE_EMPTY");
        issue(source().with("prompts/en/system.md", " \n\t"), "PACK_FILE_EMPTY");
    }

    @Test void enforcesFileSizeIncludingManifestAndUtf8Bytes() {
        issue(source().with("prompts/en/system.md", "x".repeat(PackLayout.MAX_FILE_BYTES + 1)), "PACK_TOO_LARGE");
        issue(source().with("pack.json", "x".repeat(PackLayout.MAX_FILE_BYTES + 1)), "PACK_TOO_LARGE");
        issue(source().with("prompts/en/system.md", "\u20ac".repeat(PackLayout.MAX_FILE_BYTES / 2)), "PACK_TOO_LARGE");
        assertTrue(loader.tryLoad(id, source().with("prompts/en/system.md", "x".repeat(PackLayout.MAX_FILE_BYTES))).ok());
    }

    @Test void enforcesTotalPackSize() {
        InMemoryPackSource source = manifest(root -> {
            ArrayNode files = (ArrayNode) root.get("files");
            for (int index = 0; index < 8; index++) files.add("extra-" + index + ".md");
        });
        for (int index = 0; index < 8; index++) {
            source = source.with("extra-" + index + ".md", "x".repeat(PackLayout.MAX_FILE_BYTES));
        }
        issue(source, "PACK_TOO_LARGE");
    }

    @Test void stripsBomWithWarningsAndPreservesRawFilesOtherwise() {
        InMemoryPackSource source = source();
        source = source.with("pack.json", "\uFEFF" + source.read("pack.json").orElseThrow())
                .with("strings/en.json", "\uFEFF" + source.read("strings/en.json").orElseThrow())
                .with("prompts/en/system.md", "\uFEFFtext");
        LoadResult result = loader.tryLoad(id, source);
        assertTrue(result.ok(), result.issues().toString());
        assertEquals(3, result.issues().size());
        assertTrue(result.issues().stream().allMatch(problem -> problem.code().equals("PACK_BOM_STRIPPED")));
        assertEquals("text", result.pack().orElseThrow().requireFile("prompts/en/system.md"));
    }

    @Test void rejectsUndeclaredDefaultLocale() {
        issue(manifest(root -> root.put("defaultLocale", "pl")), "PACK_DEFAULT_LOCALE_INVALID");
    }

    @ParameterizedTest @MethodSource("invalidStrings")
    void rejectsInvalidStrings(String text) {
        issue(source().with("strings/en.json", text), "PACK_STRINGS_INVALID");
    }

    static Stream<String> invalidStrings() {
        return Stream.of("[]", "null", "{\"bad/key\":\"text\"}", "{\"key\":\" \"}", "{\"key\":12}",
                "{\"key\":{}}", "{\"" + "a".repeat(81) + "\":\"text\"}", "{\"key\":\"" + "a".repeat(401) + "\"}");
    }

    @Test void requiresFreeTextOptIn() {
        issue(manifest(root -> policy(root).put("freeTextAllowed", false)), "PACK_FREE_TEXT_NOT_ALLOWED");
    }

    @Test void allowsAtMostOneFreeTextInput() {
        issue(manifest(root -> {
            ObjectNode extra = input(root, 2).deepCopy();
            extra.put("id", "other-wish");
            ((ArrayNode) root.get("startInputs")).add(extra);
        }), "PACK_TOO_MANY_FREE_TEXT_INPUTS");
    }

    @Test void duplicateInputAndEventIdsAreReferenceIssues() {
        issue(manifest(root -> input(root, 1).put("id", "cargo")), "PACK_DUPLICATE_ID");
        issue(manifest(root -> ((ArrayNode) root.get("events")).add(root.get("events").get(0).deepCopy())), "PACK_DUPLICATE_ID");
    }

    @Test void missingLabelInOnlyOneLocaleNamesThatLocale() {
        InMemoryPackSource source = manifest(root -> {
            ((ArrayNode) root.get("locales")).add("pl");
            ((ArrayNode) root.get("files")).add("strings/pl.json").add("prompts/pl/system.md");
        }).with("strings/pl.json", "{\"input.cargo\":\"Cargo\",\"input.hazards\":\"Hazards\"}")
                .with("prompts/pl/system.md", "Text.");
        LoadResult result = loader.tryLoad(id, source);
        assertFalse(result.ok());
        assertEquals(1, result.issues().size());
        assertEquals("PACK_DANGLING_REF", result.issues().getFirst().code());
        assertEquals("stringKey:pl:input.wish", result.issues().getFirst().message());
    }

    @Test void aggregatesIndependentProblemsAndExceptionCarriesEveryIssue() {
        InMemoryPackSource source = manifest(root -> {
            root.put("id", "other");
            root.put("defaultLocale", "pl");
        }).without("strings/en.json");
        LoadResult result = loader.tryLoad(id, source);
        assertTrue(result.issues().stream().anyMatch(problem -> problem.code().equals("PACK_ID_MISMATCH")));
        assertTrue(result.issues().stream().anyMatch(problem -> problem.code().equals("PACK_FILE_MISSING")));
        PackValidationException exception = assertThrows(PackValidationException.class, () -> loader.load(id, source));
        assertEquals(result.issues(), exception.issues());
        assertThrows(UnsupportedOperationException.class, () -> exception.issues().clear());
    }

    @Test void invalidManifestFieldDoesNotHideIndependentContentProblems() {
        InMemoryPackSource source = manifest(root -> {
            root.put("name", " ");
            policy(root).put("freeTextAllowed", false);
        }).without("strings/en.json");
        LoadResult result = assertDoesNotThrow(() -> loader.tryLoad(id, source));
        assertFalse(result.ok());
        assertTrue(result.pack().isEmpty());
        Set<String> codes = result.issues().stream().map(PackIssue::code)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.containsAll(Set.of("PACK_FIELD_INVALID", "PACK_FILE_MISSING",
                "PACK_FREE_TEXT_NOT_ALLOWED", "PACK_DANGLING_REF")), result.issues().toString());
    }

    @Test void validatorsRunInRegistrationOrderAfterBuiltInsAndShareReferenceIndex() {
        List<Integer> order = new ArrayList<>();
        PackLoader extensions = new PackLoader(List.of((pack, context) -> {
            order.add(1);
            assertTrue(pack.file("strings/en.json").isPresent());
            context.references().define("kind", "one", "extra.json", "/id");
            context.warn("EXTENSION_WARNING", "extra.json", "", "Warning");
        }, (pack, context) -> {
            order.add(2);
            context.references().reference("kind", "one", "other.json", "/ref");
        }));
        assertTrue(extensions.tryLoad(id, source()).ok());
        assertEquals(List.of(1, 2), order);
        order.clear();
        assertFalse(extensions.tryLoad(id, source().without("strings/en.json")).ok());
        assertTrue(order.isEmpty());
    }

    @Test void validatorsCanReportErrorsAndDuplicateAndDanglingReferences() {
        PackLoader extensions = new PackLoader(List.of((pack, context) -> {
            context.references().define("event", "run.completed", "extra.json", "/id");
            context.references().reference("kind", "absent", "extra.json", "/ref");
            context.error("EXTENSION_INVALID", "extra.json", "", "Invalid section");
        }));
        LoadResult result = extensions.tryLoad(id, source());
        assertFalse(result.ok());
        assertEquals(Set.of("PACK_DUPLICATE_ID", "PACK_DANGLING_REF", "EXTENSION_INVALID"),
                result.issues().stream().map(PackIssue::code).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void parsesEveryListedJsonAndRejectsTrailingTokensAndDuplicateKeys() {
        issue(source().with("strings/en.json", "{bad"), "PACK_JSON_INVALID");
        issue(source().with("pack.json", source().read("pack.json").orElseThrow() + " {}"), "PACK_JSON_INVALID");
        issue(source().with("strings/en.json", "{\"key\":\"one\",\"key\":\"two\"}"), "PACK_JSON_INVALID");
        issue(manifest(root -> ((ArrayNode) root.get("files")).add("extra.json")).with("extra.json", "{bad"), "PACK_JSON_INVALID");
    }

    @Test void ignoresUnindexedFilesAndDoesNotMutateSources() {
        assertTrue(loader.tryLoad(id, source().with("unindexed.json", "invalid")).ok());
        assertEquals(loader.load(id, source()), loader.load(id, source()));
    }

    @ParameterizedTest @MethodSource("invalidFields")
    void malformedManifestFieldsNeverEscapeAsConstructorFailures(Consumer<ObjectNode> change, String pointer) {
        LoadResult result = assertDoesNotThrow(() -> loader.tryLoad(id, manifest(change)));
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(problem -> problem.code().equals("PACK_FIELD_INVALID")
                && problem.pointer().equals(pointer)), result.issues().toString());
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                invalid(root -> root.remove("schemaVersion"), "/schemaVersion"),
                invalid(root -> root.put("schemaVersion", 1.5), "/schemaVersion"),
                invalid(root -> root.put("id", "Bad"), "/id"),
                invalid(root -> root.put("version", "1.2"), "/version"),
                invalid(root -> root.put("name", " "), "/name"),
                invalid(root -> root.put("name", "a".repeat(81)), "/name"),
                invalid(root -> root.put("description", "a".repeat(301)), "/description"),
                invalid(root -> root.putArray("locales"), "/locales"),
                invalid(root -> ((ArrayNode) root.get("locales")).add("en"), "/locales/1"),
                invalid(root -> ((ArrayNode) root.get("locales")).add("EN"), "/locales/1"),
                invalid(root -> root.put("defaultLocale", "bad-tag"), "/defaultLocale"),
                invalid(root -> root.put("features", true), "/features"),
                invalid(root -> ((ArrayNode) root.get("features")).add("UNKNOWN"), "/features/2"),
                invalid(root -> ((ArrayNode) root.get("features")).add("ASSETS"), "/features/2"),
                invalid(root -> ((ArrayNode) root.get("features")).add(1), "/features/2"),
                invalid(root -> root.putNull("startInputs"), "/startInputs"),
                invalid(root -> ((ArrayNode) root.get("startInputs")).add(1), "/startInputs/3"),
                invalid(root -> input(root, 0).put("kind", "OTHER"), "/startInputs/0/kind"),
                invalid(root -> input(root, 0).put("id", "Bad"), "/startInputs/0/id"),
                invalid(root -> input(root, 0).put("labelKey", "bad/key"), "/startInputs/0/labelKey"),
                invalid(root -> input(root, 0).put("required", "true"), "/startInputs/0/required"),
                invalid(root -> input(root, 0).putArray("values").add("one"), "/startInputs/0/values"),
                invalid(root -> input(root, 0).putArray("values").add("one").add("one"), "/startInputs/0/values/1"),
                invalid(root -> input(root, 0).putArray("values").add("one").add("Bad"), "/startInputs/0/values/1"),
                invalid(root -> input(root, 1).put("maxItems", 0), "/startInputs/1/maxItems"),
                invalid(root -> input(root, 1).put("maxItems", 4), "/startInputs/1/maxItems"),
                invalid(root -> input(root, 2).put("maxChars", 0), "/startInputs/2/maxChars"),
                invalid(root -> input(root, 2).put("maxChars", 301), "/startInputs/2/maxChars"),
                invalid(root -> input(root, 2).put("maxChars", 999999999999L), "/startInputs/2/maxChars"),
                invalid(root -> root.putNull("events"), "/events"),
                invalid(root -> ((ArrayNode) root.get("events")).add(true), "/events/1"),
                invalid(root -> ((ObjectNode) root.get("events").get(0)).put("id", "single"), "/events/0/id"),
                invalid(root -> ((ObjectNode) root.get("events").get(0)).putNull("payload"), "/events/0/payload"),
                invalid(root -> ((ArrayNode) root.get("events").get(0).get("payload")).add(false), "/events/0/payload/2"),
                invalid(root -> ((ObjectNode) root.get("events").get(0).get("payload").get(1)).put("name", "summary"), "/events/0/payload/1/name"),
                invalid(root -> ((ObjectNode) root.get("events").get(0).get("payload").get(0)).put("type", "FLOAT"), "/events/0/payload/0/type"),
                invalid(root -> {
                    ArrayNode payload = (ArrayNode) root.get("events").get(0).get("payload");
                    for (int index = 0; index < 7; index++) payload.addObject().put("name", "field-" + index).put("type", "BOOL");
                }, "/events/0/payload"),
                invalid(root -> root.putNull("contentPolicy"), "/contentPolicy"),
                invalid(root -> policy(root).remove("freeTextAllowed"), "/contentPolicy/freeTextAllowed"),
                invalid(root -> policy(root).putNull("audience"), "/contentPolicy/audience"),
                invalid(root -> ((ObjectNode) policy(root).get("audience")).put("minAge", 0), "/contentPolicy/audience/minAge"),
                invalid(root -> ((ObjectNode) policy(root).get("audience")).put("maxAge", 100), "/contentPolicy/audience/maxAge"),
                invalid(root -> ((ObjectNode) policy(root).get("audience")).put("maxAge", 9), "/contentPolicy/audience/maxAge"),
                invalid(root -> policy(root).putArray("forbiddenTags").add("Bad"), "/contentPolicy/forbiddenTags/0"),
                invalid(root -> root.putNull("files"), "/files"),
                invalid(root -> ((ArrayNode) root.get("files")).add(false), "/files/2"));
    }

    private static Arguments invalid(Consumer<ObjectNode> change, String pointer) {
        return Arguments.of(change, pointer);
    }

    @Test void rejectsNonObjectManifest() {
        issue(source().with("pack.json", "[]"), "PACK_FIELD_INVALID");
        issue(source().with("pack.json", "null"), "PACK_FIELD_INVALID");
    }
}